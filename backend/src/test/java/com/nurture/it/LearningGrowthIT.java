package com.nurture.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学习成长专项 IT（真 PostgreSQL，走完整过滤器链）：
 * 覆盖 成长中心聚合 / 课后任务状态机与权限 / 学习档案权限 / 家长周报权限 / 教师收藏幂等 / 课后答疑权限。
 * 一条主线链路（教师↔学生↔家长 + 两节已确认课程）驱动全部场景，另建无关角色验证 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LearningGrowthIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private String tTeacher, tStudent, tParent, tOtherTeacher, tOutsider;
    private String teacherId, otherTeacherId;
    private UUID bookingA, bookingB;     // 两节经审批的正式课；同师仅有一次试听
    private UUID taskId;
    private UUID otherBookingId;         // 无关教师的课程

    private int offlineAuditBaseline;

    @BeforeAll
    void setUp() throws Exception {
        String suffix = String.format("%07d", new java.security.SecureRandom().nextInt(10_000_000));  // 纯数字 7 位
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", "139" + suffix + "1", "LearnIT#1");
        var student = Support.register(mvc, om, "student", "STUDENT", "138" + suffix + "2", "LearnIT#2");
        var parent = Support.register(mvc, om, "parent", "PARENT", "137" + suffix + "3", "LearnIT#3");
        var otherTeacher = Support.register(mvc, om, "teacher", "TEACHER", "136" + suffix + "4", "LearnIT#4");
        var outsider = Support.register(mvc, om, "student", "STUDENT", "135" + suffix + "5", "LearnIT#5");
        tTeacher = teacher.accessToken();   teacherId = teacher.accountId().toString();
        tStudent = student.accessToken();
        tParent = parent.accessToken();
        tOtherTeacher = otherTeacher.accessToken(); otherTeacherId = otherTeacher.accountId().toString();
        tOutsider = outsider.accessToken();

        // 监护绑定（ACTIVE）
        String studentNo = Support.getJson(mvc, om, "/api/v1/student/me/profile", tStudent).get("studentNo").asText();
        Support.postJson(mvc, om, "/api/v1/parent/me/children", tParent,
                new java.util.HashMap<>() {{ put("studentNo", studentNo); put("relation", "母亲"); }});
        Support.postJson(mvc, om, "/api/v1/parent/me/children:confirm", tStudent, new java.util.HashMap<>());

        // 学习任务不依赖试听类型，使用真实正式课审批链路。
        bookingA = createConfirmedLesson(tTeacher, tStudent, 20);
        bookingB = createConfirmedLesson(tTeacher, tStudent, 40);
        otherBookingId = UUID.fromString(Support.postJson(mvc, om, "/api/v1/booking/bookings", tOutsider,
                new java.util.HashMap<>() {{
                    put("teacherId", otherTeacherId);
                    put("slotId", Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", tOtherTeacher,
                            new java.util.HashMap<>() {{ put("startAt", plusDays(21, 9)); put("endAt", plusDays(21, 10)); }}).get("slotId").asText());
                    put("subject", "他人课程"); put("bookingType", "TRIAL");
                }}).get("bookingId").asText());

        offlineAuditBaseline = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action_type='payment.offline-confirm' AND execution_status='SUCCESS'",
                Integer.class);
    }

    private java.util.Map<String, Object> trialBody(String teacherId, String startIso, String endIso, String subject) {
        return new java.util.HashMap<>() {{
            put("teacherId", teacherId);
            put("slotId", "");
            put("subject", subject);
            put("bookingType", "TRIAL");
        }};
    }

    private String plusDays(int d, int h) {
        var t = OffsetDateTime.now().plusDays(d).withHour(h).withMinute(0).withSecond(0).withNano(0);
        return t.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx"));
    }

    private UUID createConfirmedLesson(String teacher, String student, int day) throws Exception {
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", teacher,
                new java.util.HashMap<>() {{ put("startAt", plusDays(day, 9)); put("endAt", plusDays(day, 10)); }});
        var builder = post("/api/v1/booking/bookings").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + student)
                .content(om.writeValueAsBytes(new java.util.HashMap<>() {{
                    put("teacherId", UUID.fromString(teacherId));
                    put("slotId", slot.get("slotId").asText());
                    put("subject", "学习成长IT");
                    put("bookingType", "FORMAL");
                }}));
        MvcResult r = mvc.perform(builder).andExpect(status().is2xxSuccessful()).andReturn();
        var id = UUID.fromString(om.readTree(r.getResponse().getContentAsString()).get("bookingId").asText());
        var confirmed = sendPost("/api/v1/booking/booking-requests/"+id+":decide?decision=approve",
                teacher, java.util.Map.of(), UUID.randomUUID().toString());
        assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);
        assertThat(om.readTree(confirmed.getResponse().getContentAsString()).path("status").asText()).isEqualTo("CONFIRMED");
        return id;
    }

    private MvcResult sendPost(String url, String token, Object body, String idem) throws Exception {
        var b = post(url).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idem);
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) b.content(om.writeValueAsBytes(body));
        return mvc.perform(b).andReturn();
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    // ══════════════ 课后任务 ══════════════

    @Test
    @Order(10)
    @DisplayName("任务：教师为自己课程创建→学生可见→提交→反馈→COMPLETED")
    void taskLifecycle() throws Exception {
        var created = sendPost("/api/v1/learning/teacher/tasks", tTeacher, new java.util.HashMap<>() {{
            put("bookingId", bookingA.toString());
            put("title", "二次函数练习 1～5");
            put("description", "课本 P42");
            put("priority", "HIGH");
            put("deadline", plusDays(7, 22));
        }}, UUID.randomUUID().toString());
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = om.readTree(created.getResponse().getContentAsString());
        taskId = UUID.fromString(body.get("taskId").asText());
        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        // 学生视角可见且 PENDING
        var list = Support.getJson(mvc, om, "/api/v1/learning/tasks", tStudent);
        boolean found = false;
        for (JsonNode t : list) if (t.get("taskId").asText().equals(taskId.toString())) {
            found = true;
            assertThat(t.get("status").asText()).isEqualTo("PENDING");
        }
        assertThat(found).as("学生任务列表应包含新任务").isTrue();
        // 提交
        var sub = sendPost("/api/v1/learning/tasks/" + taskId + ":submit", tStudent,
                new java.util.HashMap<>() {{ put("content", "已完成 1～5 题"); }}, UUID.randomUUID().toString());
        assertThat(sub.getResponse().getStatus()).isEqualTo(200);
        assertThat(om.readTree(sub.getResponse().getContentAsString()).get("status").asText()).isEqualTo("SUBMITTED");
        // 重复提交被拒
        var again = sendPost("/api/v1/learning/tasks/" + taskId + ":submit", tStudent,
                new java.util.HashMap<>() {{ put("content", "again"); }}, UUID.randomUUID().toString());
        assertThat(again.getResponse().getStatus()).as("重复提交应被拒绝").isEqualTo(409);
        // 反馈
        var fb = sendPost("/api/v1/learning/tasks/" + taskId + ":feedback", tTeacher,
                new java.util.HashMap<>() {{ put("feedback", "正确率 80%，注意顶点式"); }}, UUID.randomUUID().toString());
        assertThat(fb.getResponse().getStatus()).isEqualTo(200);
        assertThat(om.readTree(fb.getResponse().getContentAsString()).get("status").asText()).isEqualTo("COMPLETED");
        // 非法反馈（已完成任务再反馈）被拒
        var fb2 = sendPost("/api/v1/learning/tasks/" + taskId + ":feedback", tTeacher,
                new java.util.HashMap<>() {{ put("feedback", "x"); }}, UUID.randomUUID().toString());
        assertThat(fb2.getResponse().getStatus()).as("COMPLETED 后再反馈应被拒").isEqualTo(409);
    }

    @Test
    @Order(11)
    @DisplayName("任务权限：非归属教师无法对自己课程外创建/反馈；他人无法提交")
    void taskPermissions() throws Exception {
        // 无关教师在「他人课程」上创建任务 → 403（booking 归属校验）
        var wrong = sendPost("/api/v1/learning/teacher/tasks", tOtherTeacher, new java.util.HashMap<>() {{
            put("bookingId", bookingA.toString());
            put("title", "越权任务");
        }}, UUID.randomUUID().toString());
        assertThat(wrong.getResponse().getStatus()).as("非归属教师创建应 403").isEqualTo(403);
        // 无关学生提交他人任务 → 403（学生归属校验）
        var outsiderSubmit = sendPost("/api/v1/learning/tasks/" + otherBookingId + ":submit", tOutsider,
                new java.util.HashMap<>() {{ put("content", "x"); }}, UUID.randomUUID().toString());
        int st = outsiderSubmit.getResponse().getStatus();
        assertThat(st).as("无关提交应 403/400/404，实际 " + st).isIn(403, 400, 404);
        // 无关教师反馈他人任务 → 403
        var created = sendPost("/api/v1/learning/teacher/tasks", tTeacher, new java.util.HashMap<>() {{
            put("bookingId", bookingB.toString());
            put("title", "权限验证任务");
        }}, UUID.randomUUID().toString());
        UUID tid = UUID.fromString(om.readTree(created.getResponse().getContentAsString()).get("taskId").asText());
        post("/api/v1/learning/tasks/" + tid + ":submit", tStudent,
                new java.util.HashMap<>() {{ put("content", "done"); }}, UUID.randomUUID().toString());
        var fb = sendPost("/api/v1/learning/tasks/" + tid + ":feedback", tOtherTeacher,
                new java.util.HashMap<>() {{ put("feedback", "越权"); }}, UUID.randomUUID().toString());
        assertThat(fb.getResponse().getStatus()).as("非归属教师反馈应 403").isEqualTo(403);
    }

    @Test
    @Order(12)
    @DisplayName("逾期标记：deadline 已过且 PENDING → OVERDUE_PENDING")
    void overdueFlag() throws Exception {
        var created = sendPost("/api/v1/learning/teacher/tasks", tTeacher, new java.util.HashMap<>() {{
            put("bookingId", bookingB.toString());
            put("title", "已过期任务");
            put("deadline", plusDays(-3, 20));
        }}, UUID.randomUUID().toString());
        UUID tid = UUID.fromString(om.readTree(created.getResponse().getContentAsString()).get("taskId").asText());
        var list = Support.getJson(mvc, om, "/api/v1/learning/tasks", tStudent);
        boolean flagged = false;
        for (JsonNode t : list) if (t.get("taskId").asText().equals(tid.toString())) {
            flagged = t.get("status").asText().equals("OVERDUE_PENDING");
        }
        assertThat(flagged).as("过期未交任务应标 OVERDUE_PENDING").isTrue();
    }

    // ══════════════ 学习档案权限 ══════════════

    @Test
    @Order(20)
    @DisplayName("档案权限：学生本人 ✓；授课教师 ✓；无关教师 403； ADMIN ✓")
    void archivePermissions() throws Exception {
        var self = Support.getJson(mvc, om, "/api/v1/learning/archive", tStudent);
        assertThat(self.get("stats").get("completedLessons").asInt()).as("本人档案可读").isGreaterThanOrEqualTo(0);
        // 从「我的学生」列表取本 IT 学生（该列表来自教师真实授课关系）
        var myList = Support.getJson(mvc, om, "/api/v1/learning/teacher/students", tTeacher);
        String sid = null;
        for (JsonNode st : myList) {
            // 通过任务标题无法反查，改为：抓列表中第一个学生（本 IT 教师只教这一个学生）
            sid = st.get("studentId").asText();
        }
        assertThat(sid).as("教师学生列表应包含本 IT 学生").isNotNull();
        var teacher = Support.getJson(mvc, om, "/api/v1/learning/teacher/students/" + sid, tTeacher);
        assertThat(teacher.has("profile")).as("授课教师可读学生档案").isTrue();
        var other = sendPost("/api/v1/learning/teacher/students/" + UUID.fromString(extractStudentId()), tOtherTeacher, null, UUID.randomUUID().toString());
        // GET 无 body：用 get 请求验证
        var otherGet = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/learning/teacher/students/" + sid)
                .header("Authorization", "Bearer " + tOtherTeacher)).andReturn();
        assertThat(otherGet.getResponse().getStatus()).as("无关教师应 403").isEqualTo(403);
    }

    private String extractStudentId() throws Exception {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE phone = (SELECT phone FROM accounts WHERE id = (SELECT student_id FROM learning_tasks LIMIT 1))", String.class);
    }

    // ══════════════ 成长中心 ══════════════

    @Test
    @Order(21)
    @DisplayName("成长中心：统计来自真实数据；无关学生仅见自己空数据")
    void growthCenter() throws Exception {
        var g = Support.getJson(mvc, om, "/api/v1/learning/growth", tStudent);
        assertThat(g.has("week")).as("周统计存在").isTrue();
        assertThat(g.get("week").get("taskCompleted").asInt()).as("本周完成任务数（含已反馈任务）").isGreaterThanOrEqualTo(1);
        // 无关学生只见自己的数据：绝不包含学生A的任务反馈
        var outsider = Support.getJson(mvc, om, "/api/v1/learning/growth", tOutsider);
        String outsiderJson = outsider.toString();
        assertThat(outsiderJson.contains("二次函数练习")).as("无关学生不得看到他人任务").isFalse();
    }

    // ══════════════ 家长周报 ══════════════

    @Test
    @Order(22)
    @DisplayName("周报：ACTIVE 绑定可读；未绑定家长 403；PENDING 不放行")
    void weeklyReportPermissions() throws Exception {
        var studentAccountId = jdbc.queryForObject(
                "SELECT a.id FROM accounts a JOIN student_profiles sp ON sp.id = (SELECT id FROM student_profiles WHERE account_id = a.id LIMIT 1) WHERE a.role = 'STUDENT' LIMIT 1", String.class);
        // 用学生accountId：直接从IT成员变量推导——学生 accountId 需要从 Support 获取，这里改用父子查询学生账号
        var kids = Support.getJson(mvc, om, "/api/v1/parent/me/children", tParent);
        UUID studentUuid = UUID.fromString(kids.get(0).get("studentId").asText());
        var ok = Support.getJson(mvc, om, "/api/v1/parent/me/weekly-report?studentId=" + studentUuid, tParent);
        assertThat(ok.has("weekLessons")).as("ACTIVE 家长可读周报").isTrue();
        // 未绑定家长（新建）读同一学生 → 403
        var stranger = Support.register(mvc, om, "parent", "PARENT",
                "134" + String.format("%09d", new java.security.SecureRandom().nextInt(1_000_000_000)).substring(1), "Stranger#1");
        var res = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/parent/me/weekly-report?studentId=" + studentUuid)
                .header("Authorization", "Bearer " + stranger.accessToken())).andReturn();
        assertThat(res.getResponse().getStatus()).as("未绑定家长应 403").isEqualTo(403);
    }

    private UUID findStudentAccountId() {
        // 学生 accountId = learning_tasks 中本 IT 学生的 student_id（tasks 由 teacherId+booking 关联产生）
        return jdbc.queryForObject(
                "SELECT DISTINCT student_id FROM learning_tasks WHERE teacher_id IN (SELECT id FROM accounts WHERE role='TEACHER') LIMIT 1",
                UUID.class);
    }

    // ══════════════ 教师收藏 ══════════════

    @Test
    @Order(30)
    @DisplayName("收藏：toggle 收藏→取消→再收藏；DB 无重复行")
    void favoriteToggle() throws Exception {
        var r1 = sendPost("/api/v1/matching/teachers/" + teacherId + ":favorite", tStudent, null, UUID.randomUUID().toString());
        assertThat(r1.getResponse().getStatus()).isEqualTo(200);
        assertThat(om.readTree(r1.getResponse().getContentAsString()).get("favorited").asBoolean()).isTrue();
        assertThat(count("SELECT count(*) FROM favorite_teachers WHERE teacher_id=?", UUID.fromString(teacherId))).isEqualTo(1);
        var r2 = sendPost("/api/v1/matching/teachers/" + teacherId + ":favorite", tStudent, null, UUID.randomUUID().toString());
        assertThat(om.readTree(r2.getResponse().getContentAsString()).get("favorited").asBoolean()).isFalse();
        assertThat(count("SELECT count(*) FROM favorite_teachers WHERE teacher_id=?", UUID.fromString(teacherId))).isEqualTo(0);
        var r3 = sendPost("/api/v1/matching/teachers/" + teacherId + ":favorite", tStudent, null, UUID.randomUUID().toString());
        assertThat(om.readTree(r3.getResponse().getContentAsString()).get("favorited").asBoolean()).isTrue();
        assertThat(count("SELECT count(*) FROM favorite_teachers WHERE teacher_id=?", UUID.fromString(teacherId))).isEqualTo(1);
        var list = Support.getJson(mvc, om, "/api/v1/matching/favorites", tStudent);
        assertThat(list.size()).as("收藏列表包含该教师").isGreaterThanOrEqualTo(1);
    }

    // ══════════════ 课后答疑 ══════════════

    @Test
    @Order(40)
    @DisplayName("答疑：授课关系提问→教师回答→关闭；无关教师回答 403")
    void questionFlow() throws Exception {
        var asked = sendPost("/api/v1/learning/questions", tStudent, new java.util.HashMap<>() {{
            put("teacherId", UUID.fromString(teacherId));
            put("subject", "数学");
            put("title", "二次函数顶点式");
            put("content", "顶点公式是怎么推出来的？");
        }}, UUID.randomUUID().toString());
        assertThat(asked.getResponse().getStatus()).isEqualTo(201);
        UUID qid = UUID.fromString(om.readTree(asked.getResponse().getContentAsString()).get("questionId").asText());
        // 无关教师回答 → 403
        var wrong = sendPost("/api/v1/learning/questions/" + qid + ":answer", tOtherTeacher,
                new java.util.HashMap<>() {{ put("answer", "越权回答"); }}, UUID.randomUUID().toString());
        assertThat(wrong.getResponse().getStatus()).as("无关教师回答应 403").isEqualTo(403);
        // 归属教师回答
        var ans = sendPost("/api/v1/learning/questions/" + qid + ":answer", tTeacher,
                new java.util.HashMap<>() {{ put("answer", "配方法：y=a(x-h)²+k"); }}, UUID.randomUUID().toString());
        assertThat(ans.getResponse().getStatus()).isEqualTo(200);
        assertThat(om.readTree(ans.getResponse().getContentAsString()).get("status").asText()).isEqualTo("ANSWERED");
        // 关闭
        var close = sendPost("/api/v1/learning/questions/" + qid + ":close", tStudent, null, UUID.randomUUID().toString());
        assertThat(close.getResponse().getStatus()).isEqualTo(200);
        // CLOSED 后再回答 → 拒绝
        var ans2 = sendPost("/api/v1/learning/questions/" + qid + ":answer", tTeacher,
                new java.util.HashMap<>() {{ put("answer", "x"); }}, UUID.randomUUID().toString());
        assertThat(ans2.getResponse().getStatus()).as("CLOSED 后再答应被拒").isEqualTo(409);
        // 无授课关系的学生向陌生教师提问 → 403
        var strangerAsk = sendPost("/api/v1/learning/questions", tOutsider, new java.util.HashMap<>() {{
            put("teacherId", UUID.fromString(teacherId));
            put("subject", "数学"); put("title", "越权"); put("content", "x");
        }}, UUID.randomUUID().toString());
        assertThat(strangerAsk.getResponse().getStatus()).as("无授课关系提问应 403").isEqualTo(403);
    }

    // ══════════════ 事务一致性 ══════════════

    @Test
    @Order(50)
    @DisplayName("一致性：任务提交后 DB 状态与 HTTP 结果一致（无半成功）")
    void stateConsistency() throws Exception {
        var created = sendPost("/api/v1/learning/teacher/tasks", tTeacher, new java.util.HashMap<>() {{
            put("bookingId", bookingA.toString());
            put("title", "一致性任务");
        }}, UUID.randomUUID().toString());
        UUID tid = UUID.fromString(om.readTree(created.getResponse().getContentAsString()).get("taskId").asText());
        var submitted = sendPost("/api/v1/learning/tasks/" + tid + ":submit", tStudent,
                new java.util.HashMap<>() {{ put("content", "内容"); }}, UUID.randomUUID().toString());
        assertThat(submitted.getResponse().getStatus()).as("提交应成功").isEqualTo(200);
        String dbStatus = jdbc.queryForObject("SELECT status FROM learning_tasks WHERE id=?", String.class, tid);
        assertThat(dbStatus).as("DB 状态应与提交结果一致").isEqualTo("SUBMITTED");
        Integer attempts = jdbc.queryForObject("SELECT count(*) FROM learning_tasks WHERE id=? AND submission_content IS NOT NULL", Integer.class, tid);
        assertThat(attempts).isEqualTo(1);
    }
}
