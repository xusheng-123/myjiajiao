package com.nurture.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.settlement.domain.SettlementEntry;
import com.nurture.settlement.infra.SettlementEntryRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 教师结算汇总专项（真 PostgreSQL）：汇总与明细分页是两个独立查询。
 * 场景 A：条目 >50 时汇总=全部条目之和（而非前 50 条之和——历史缺陷：84 条少报 ¥6,885）。
 * 场景 B：明细仍固定返回最近 50 条（不为汇总放开分页）。
 * 场景 C：汇总与 DB 全量聚合一致。
 * 场景 D：状态语义——PENDING 计 pending、PAID_OUT 计 paidOut、SETTLED 当前不计入任一字段
 *         （经实体合法状态转换构造：markSettled/markPaidOut；SETTLED 不可达为现状，如实断言）。
 * 场景 E：教师间数据隔离（汇总/明细均为 JWT 侧教师本人）；非教师角色被拒。
 * 写入路径回归（订单支付生成条目/抽成/快照）由 OfflinePaymentStateMachineIT/E2E 覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class SettlementSummaryIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired SettlementEntryRepository settlementRepository;

    private static final long NET_MAIN = 8500;   // 前 50 条：gross 10000 − fee 1500
    private static final long NET_TAIL = 7000;   // 其后 5 条：gross 7777 − fee 777
    private static final long NET_PAID_OUT = 42500; // 已提现 1 条
    private static final long NET_SETTLED = 9000;   // 已结算待提现 1 条（不计入两字段）

    private SettlementEntry pendingEntry(UUID teacherId, long gross, long fee) {
        return new SettlementEntry(teacherId, UUID.randomUUID(), UUID.randomUUID(), gross, fee);
    }

    @Test
    void 汇总基于全部条目_明细保持50条_状态语义与隔离正确() throws Exception {
        var teacherA = Support.register(mvc, om, "teacher", "TEACHER", "17" + rand9(), "ItSummary#1");
        var teacherB = Support.register(mvc, om, "teacher", "TEACHER", "17" + rand9(), "ItSummary#2");
        UUID a = teacherA.accountId();

        // 场景 A 数据：55 条 PENDING（50×8500 + 5×7000，两组净额不同 ⇒ 任何 50 条子集之和 ≠ 全量和）
        for (int i = 0; i < 50; i++) {
            settlementRepository.save(pendingEntry(a, 10000, 1500));
        }
        for (int i = 0; i < 5; i++) {
            settlementRepository.save(pendingEntry(a, 7777, 777));
        }
        // 场景 D 数据：经实体合法状态转换构造 PAID_OUT 与 SETTLED 各 1 条
        var paidOut = pendingEntry(a, 50000, 7500);
        paidOut.markSettled();
        paidOut.markPaidOut();
        settlementRepository.save(paidOut);
        var settled = pendingEntry(a, 12000, 3000);
        settled.markSettled();
        settlementRepository.save(settled);
        long expectedPending = 50 * NET_MAIN + 5 * NET_TAIL; // 460000

        // 场景 A + D：汇总=全部 PENDING 之和；PAID_OUT 单独汇总
        var body = om.readTree(mvc.perform(get("/api/v1/teacher/me/settlement")
                        .header("Authorization", "Bearer " + teacherA.accessToken())).andReturn()
                .getResponse().getContentAsString());
        assertThat(body.get("pendingNetCents").asLong())
                .as("场景 A：汇总必须等于全部 55 条 PENDING 之和（截断实现只会得到 425000 或 417500）")
                .isEqualTo(expectedPending);
        assertThat(body.get("paidOutNetCents").asLong())
                .as("场景 D：PAID_OUT 净额单独汇总")
                .isEqualTo(NET_PAID_OUT);

        // 场景 B：明细固定 50 条（共 57 条不全部返回）
        var entries = body.get("entries");
        assertThat(entries.size()).as("场景 B：明细保持既有 50 条上限").isEqualTo(50);

        // 场景 C：API 汇总 == DB 全量聚合；且 ≠ 明细页求和（证明汇总不是来自前 50 条）
        long dbAll = settlementRepository.findByTeacherIdAndStatus(a, SettlementEntry.SettlementStatus.PENDING)
                .stream().mapToLong(SettlementEntry::getNetCents).sum();
        assertThat(expectedPending).isEqualTo(dbAll);
        long detailSum = 0;
        for (var e : entries) {
            if ("PENDING".equals(e.get("status").asText())) {
                detailSum += e.get("netCents").asLong();
            }
        }
        // 任意 50 条 PENDING 子集之和 ≤ 50×8500 < 全量和 ⇒ 汇总不可能是明细页求和；排除条目净额恒为正
        assertThat(detailSum).as("明细页 PENDING 求和必须小于全量汇总（否则即是截断缺陷复发）").isLessThan(expectedPending);
        assertThat(expectedPending - detailSum).as("未进入明细页的条目净额为正").isPositive();

        // 场景 D（续）：SETTLED 条目不计入 pending/paidOut 任一字段（当前展示语义，如实断言）
        long pendingOnly = settlementRepository.findByTeacherIdAndStatus(a, SettlementEntry.SettlementStatus.PENDING)
                .stream().mapToLong(SettlementEntry::getNetCents).sum();
        assertThat(pendingOnly).isEqualTo(expectedPending); // SETTLED 的 9000 不在其中

        // 场景 E：教师间隔离——B 无任何条目，看到的是自己的零汇总与空明细
        var bodyB = om.readTree(mvc.perform(get("/api/v1/teacher/me/settlement")
                        .header("Authorization", "Bearer " + teacherB.accessToken())).andReturn()
                .getResponse().getContentAsString());
        assertThat(bodyB.get("pendingNetCents").asLong()).isZero();
        assertThat(bodyB.get("paidOutNetCents").asLong()).isZero();
        assertThat(bodyB.get("entries").isEmpty()).isTrue();
        // 场景 E（续）：非教师角色被角色护栏拒绝（学生 token）
        int studentSt = mvc.perform(get("/api/v1/teacher/me/settlement")
                        .header("Authorization", "Bearer " + studentToken())).andReturn()
                .getResponse().getStatus();
        assertThat(studentSt).as("学生访问教师结算端点应被角色护栏拒绝").isIn(401, 403);
    }

    private String studentToken() throws Exception {
        var acc = Support.register(mvc, om, "student", "STUDENT", "17" + rand9(), "ItSummary#3");
        return acc.accessToken();
    }

    private String rand9() {
        return String.format("%09d", new java.util.Random().nextInt(1_000_000_000));
    }
}
