package com.nurture.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class TrialEntitlementIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate db;
    record Pair(Support.Account teacher, Support.Account student) {}
    Pair pair() throws Exception {
        return new Pair(account("TEACHER"), account("STUDENT"));
    }
    Support.Account account(String role) throws Exception {
        return Support.register(mvc, om, role.toLowerCase(), role,
                "16"+String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000)), "TrialSafe#12");
    }
    JsonNode request(Support.Account a, String url, Object body, int expected) throws Exception {
        return request(a,url,body,expected,UUID.randomUUID().toString());
    }
    JsonNode request(Support.Account a,String url,Object body,int expected,String key) throws Exception {
        var response = mvc.perform(post(url).header("Authorization","Bearer "+a.accessToken())
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(body))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return om.readTree(response.getContentAsString());
    }
    String slot(Pair p,int hours) throws Exception {
        var at = OffsetDateTime.now().plusHours(hours);
        return request(p.teacher(),"/api/v1/scheduling/availability-slots",
                Map.of("startAt",at.toString(),"endAt",at.plusHours(1).toString()),201).path("slotId").asText();
    }
    Map<String,Object> body(Pair p,String slot,String type) {
        return Map.of("teacherId",p.teacher().accountId(),"slotId",slot,"subject","数学","bookingType",type);
    }
    String book(Pair p,String slot) throws Exception {
        return request(p.student(),"/api/v1/booking/bookings",body(p,slot,"TRIAL"),201).path("bookingId").asText();
    }
    void cancel(Pair p,String id) throws Exception {
        var preview = request(p.student(),"/api/v1/booking/bookings/"+id+":cancel/preview",Map.of("reason","调整学习安排"),200);
        request(p.student(),"/api/v1/booking/bookings/"+id+":cancel/execute",
                Map.of("reason","调整学习安排","confirmationToken",preview.path("confirmationToken").asText()),200);
    }
    @Test void 同师重复拒绝且正式课不同师不同生不受影响() throws Exception {
        var p=pair(); book(p,slot(p,30)); var other=slot(p,40);
        request(p.student(),"/api/v1/booking/bookings",body(p,other,"TRIAL"),409);
        request(p.student(),"/api/v1/booking/bookings",body(p,other,"FORMAL"),201);
        var differentStudent=new Pair(p.teacher(),account("STUDENT")); book(differentStudent,slot(p,50));
        var differentTeacher=new Pair(account("TEACHER"),p.student()); book(differentTeacher,slot(differentTeacher,60));
    }
    @Test void 开课前取消和教师驳回释放限额() throws Exception {
        var p=pair(); var id=book(p,slot(p,30)); cancel(p,id);
        var second=book(p,slot(p,40));
        request(p.teacher(),"/api/v1/booking/booking-requests/"+second+":decide?decision=reject",Map.of(),200);
        book(p,slot(p,50));
    }
    @Test void 开课后取消仍消耗限额且不产生订单或钱包流水() throws Exception {
        var p=pair(); var id=book(p,slot(p,30));
        request(p.teacher(),"/api/v1/booking/bookings/"+id+":start",Map.of(),200); cancel(p,id);
        request(p.student(),"/api/v1/booking/bookings",body(p,slot(p,40),"TRIAL"),409);
        assertThat(db.queryForObject("select count(*) from orders where booking_id=?",Long.class,UUID.fromString(id))).isZero();
        assertThat(db.queryForObject("select consumed from trial_entitlements where student_id=? and teacher_id=?",Boolean.class,p.student().accountId(),p.teacher().accountId())).isTrue();
    }
    @Test void 计划开课时间已过取消消耗限额() throws Exception {
        var p=pair(); var s=slot(p,30); var id=book(p,s);
        db.update("update availability_slots set start_at=now()-interval '2 hours',end_at=now()-interval '1 hour' where id=?",UUID.fromString(s));
        cancel(p,id);
        request(p.student(),"/api/v1/booking/bookings",body(p,slot(p,40),"TRIAL"),409);
    }
    @Test void 改期沿用预约且幂等重试不占两次() throws Exception {
        var p=pair(); var key=UUID.randomUUID().toString(); var b=body(p,slot(p,30),"TRIAL");
        var first=request(p.student(),"/api/v1/booking/bookings",b,201,key);
        var replay=request(p.student(),"/api/v1/booking/bookings",b,201,key);
        assertThat(replay.path("bookingId")).isEqualTo(first.path("bookingId"));
        var next=slot(p,40); var id=first.path("bookingId").asText();
        request(p.student(),"/api/v1/booking/bookings/"+id+":reschedule",Map.of("newSlotId",next),200);
        assertThat(db.queryForObject("select current_booking_id from trial_entitlements where student_id=? and teacher_id=?",UUID.class,p.student().accountId(),p.teacher().accountId())).isEqualTo(UUID.fromString(id));
        request(p.student(),"/api/v1/booking/bookings",body(p,slot(p,50),"TRIAL"),409);
    }
    @Test void 失败预约回滚限額且过去档期不遗留HELD() throws Exception {
        var p=pair(); var old=slot(p,-30);
        request(p.student(),"/api/v1/booking/bookings",body(p,old,"TRIAL"),400);
        assertThat(db.queryForObject("select status from availability_slots where id=?",String.class,UUID.fromString(old))).isEqualTo("OPEN");
        assertThat(db.queryForObject("select count(*) from trial_entitlements where student_id=?",Long.class,p.student().accountId())).isZero();
        book(p,slot(p,40));
    }
    @RepeatedTest(3) void 并发不同档期仅一胜且败方档期不占用() throws Exception {
        var p=pair(); var slots=new String[]{slot(p,30),slot(p,40)};
        var barrier=new CyclicBarrier(2); var pool=Executors.newFixedThreadPool(2);
        try {
            var futures=new java.util.ArrayList<Future<Integer>>();
            for(var s:slots) futures.add(pool.submit(() -> {
                barrier.await(10,TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/booking/bookings").header("Authorization","Bearer "+p.student().accessToken())
                        .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(body(p,s,"TRIAL")))).andReturn().getResponse().getStatus();
            }));
            assertThat(java.util.List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            assertThat(db.queryForObject("select count(*) from bookings where student_id=? and teacher_id=?",Long.class,p.student().accountId(),p.teacher().accountId())).isEqualTo(1);
            assertThat(db.queryForList("select status from availability_slots where id in (?,?)",String.class,UUID.fromString(slots[0]),UUID.fromString(slots[1]))).containsExactlyInAnyOrder("OPEN","HELD");
        } finally { pool.shutdownNow(); }
    }

    @Test void 名额查询不建记录且结课永久消耗() throws Exception {
        var p=pair(); var url="/api/v1/booking/trial-eligibility?teacherId="+p.teacher().accountId();
        assertThat(Support.getJson(mvc,om,url,p.student().accessToken()).path("available").asBoolean()).isTrue();
        assertThat(db.queryForObject("select count(*) from trial_entitlements where student_id=?",Long.class,p.student().accountId())).isZero();
        var id=book(p,slot(p,30));
        assertThat(Support.getJson(mvc,om,url,p.student().accessToken()).path("available").asBoolean()).isFalse();
        request(p.teacher(),"/api/v1/booking/bookings/"+id+":start",Map.of(),200);
        request(p.teacher(),"/api/v1/booking/bookings/"+id+":complete",Map.of(),200);
        request(p.student(),"/api/v1/booking/bookings",body(p,slot(p,40),"TRIAL"),409);
    }

    @RepeatedTest(3) void 开课与取消并发按最终先后正确消耗名额() throws Exception {
        var p=pair(); var id=book(p,slot(p,30));
        var preview=request(p.student(),"/api/v1/booking/bookings/"+id+":cancel/preview",Map.of("reason","调整学习安排"),200);
        var barrier=new CyclicBarrier(2); var pool=Executors.newFixedThreadPool(2);
        try {
            var start=pool.submit(() -> {
                barrier.await(10,TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/booking/bookings/"+id+":start")
                        .header("Authorization","Bearer "+p.teacher().accessToken()).header("Idempotency-Key",UUID.randomUUID().toString()))
                        .andReturn().getResponse().getStatus();
            });
            var cancel=pool.submit(() -> {
                barrier.await(10,TimeUnit.SECONDS);
                return request(p.student(),"/api/v1/booking/bookings/"+id+":cancel/execute",
                        Map.of("reason","调整学习安排","confirmationToken",preview.path("confirmationToken").asText()),200);
            });
            var started=start.get(20,TimeUnit.SECONDS); cancel.get(20,TimeUnit.SECONDS);
            assertThat(started).isIn(200,409);
            assertThat(db.queryForObject("select status from bookings where id=?",String.class,UUID.fromString(id))).isEqualTo("CANCELLED");
            assertThat(db.queryForObject("select consumed from trial_entitlements where student_id=? and teacher_id=?",Boolean.class,p.student().accountId(),p.teacher().accountId())).isEqualTo(started==200);
        } finally { pool.shutdownNow(); }
    }
}
