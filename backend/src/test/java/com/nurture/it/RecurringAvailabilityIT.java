package com.nurture.it;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class RecurringAvailabilityIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper om;@Autowired JdbcTemplate db;
    Support.Account account(String role) throws Exception {String phone;do{phone="17"+String.format("%09d",ThreadLocalRandom.current().nextInt(1_000_000_000));}while(db.queryForObject("select count(*) from accounts where phone=?",Long.class,phone)>0);return Support.register(mvc,om,role.toLowerCase(),role,phone,"Recurring#12");}
    JsonNode call(String method,String url,Support.Account a,Object body,int expected) throws Exception{return call(method,url,a,body,expected,UUID.randomUUID().toString());}
    JsonNode call(String method,String url,Support.Account a,Object body,int expected,String key) throws Exception {var r=mvc.perform(request(HttpMethod.valueOf(method),url).header("Authorization","Bearer "+a.accessToken()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body==null?new byte[0]:om.writeValueAsBytes(body))).andReturn().getResponse();assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(expected);return r.getContentAsString().isBlank()?om.nullNode():om.readTree(r.getContentAsString());}
    Map<String,Object> rule(){return new HashMap<>(Map.of("startDate",LocalDate.now().plusDays(10).toString(),"startTime","15:00","timeZone","Asia/Shanghai","durationMinutes",60,"weekCount",3,"skipDates",List.of(),"exceptions",List.of()));}
    String slot(Support.Account t,LocalDate date,String time) throws Exception {var at=LocalDateTime.of(date,LocalTime.parse(time)).atOffset(ZoneOffset.ofHours(8));return call("POST","/api/v1/scheduling/availability-slots",t,Map.of("startAt",at.toString(),"endAt",at.plusHours(1).toString()),201).path("slotId").asText();}
    @Test void 逐次预览冲突无业务写入且跳过后发布() throws Exception {
        var t=account("TEACHER");var r=rule();var date=LocalDate.parse(r.get("startDate").toString());slot(t,date,"15:00");
        long before=db.queryForObject("select count(*) from availability_slots where teacher_id=?",Long.class,t.accountId());
        var p=call("POST","/api/v1/scheduling/series/preview",t,r,200);assertThat(p.path("ready").asBoolean()).isFalse();assertThat(p.path("occurrences").get(0).path("conflicts").size()).isEqualTo(1);
        call("POST","/api/v1/scheduling/series",t,r,409);assertThat(db.queryForObject("select count(*) from availability_slots where teacher_id=?",Long.class,t.accountId())).isEqualTo(before);
        assertThat(db.queryForObject("select count(*) from availability_series where teacher_id=?",Long.class,t.accountId())).isZero();
        r.put("skipDates",List.of(date.toString()));var made=call("POST","/api/v1/scheduling/series",t,r,201);assertThat(made.path("slots").size()).isEqualTo(2);assertThat(db.queryForObject("select count(*) from orders where teacher_id=?",Long.class,t.accountId())).isZero();
    }
    @Test void 例外时间与幂等重放只生成一次() throws Exception {
        var t=account("TEACHER");var r=rule();var date=LocalDate.parse(r.get("startDate").toString());r.put("exceptions",List.of(Map.of("date",date.plusWeeks(1).toString(),"startTime","17:00")));
        String key=UUID.randomUUID().toString();var p=call("POST","/api/v1/scheduling/series",t,r,201,key);var replay=call("POST","/api/v1/scheduling/series",t,r,201,key);assertThat(replay.path("seriesId")).isEqualTo(p.path("seriesId"));
        assertThat(p.path("slots").get(1).path("startAt").asText()).contains("17:00");r.put("weekCount",4);call("POST","/api/v1/scheduling/series",t,r,409,key);
        assertThat(db.queryForObject("select count(*) from availability_slots where teacher_id=?",Long.class,t.accountId())).isEqualTo(3L);
        call("GET","/api/v1/scheduling/series/"+p.path("seriesId").asText(),account("TEACHER"),null,403);
    }
    @Test void 无效规则与非教师发布被拒绝() throws Exception {
        var t=account("TEACHER");var s=account("STUDENT");call("POST","/api/v1/scheduling/series/preview",s,rule(),403);
        for(var change:List.of(Map.<String,Object>of("weekCount",13),Map.<String,Object>of("durationMinutes",0),Map.<String,Object>of("timeZone","Not/AZone"),Map.<String,Object>of("startDate",LocalDate.now().minusDays(1).toString()),Map.<String,Object>of("skipDates",List.of("2020-01-01")))){var r=rule();r.putAll(change);call("POST","/api/v1/scheduling/series/preview",t,r,400);}
        var r=rule();r.put("startDate",LocalDate.now().plusDays(181).toString());call("POST","/api/v1/scheduling/series/preview",t,r,400);
        r=rule();r.put("exceptions",List.of(Map.of("date",r.get("startDate"),"startTime","17:00"),Map.of("date",r.get("startDate"),"startTime","18:00")));call("POST","/api/v1/scheduling/series/preview",t,r,400);
    }
    @Test void 同师重复发布并发不重叠无部分提交() throws Exception {
        var t=account("TEACHER");var r=rule();var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{List<Future<Integer>> jobs=new ArrayList<>();for(int i=0;i<2;i++)jobs.add(pool.submit(()->{gate.await();return mvc.perform(post("/api/v1/scheduling/series").header("Authorization","Bearer "+t.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(r))).andReturn().getResponse().getStatus();}));gate.countDown();assertThat(List.of(jobs.get(0).get(),jobs.get(1).get())).containsExactlyInAnyOrder(201,409);}finally{pool.shutdownNow();}
        assertThat(db.queryForObject("select count(*) from availability_series where teacher_id=?",Long.class,t.accountId())).isEqualTo(1L);assertThat(db.queryForObject("select count(*) from availability_slots where teacher_id=?",Long.class,t.accountId())).isEqualTo(3L);
    }
    @Test void 单次与重复发布共用并发锁() throws Exception {
        var t=account("TEACHER");var r=rule();r.put("weekCount",1);var at=LocalDateTime.of(LocalDate.parse(r.get("startDate").toString()),LocalTime.of(15,30)).atOffset(ZoneOffset.ofHours(8));var body=Map.of("startAt",at.toString(),"endAt",at.plusHours(1).toString());var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var one=pool.submit(()->{gate.await();return mvc.perform(post("/api/v1/scheduling/availability-slots").header("Authorization","Bearer "+t.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(body))).andReturn().getResponse().getStatus();});var many=pool.submit(()->{gate.await();return mvc.perform(post("/api/v1/scheduling/series").header("Authorization","Bearer "+t.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(r))).andReturn().getResponse().getStatus();});gate.countDown();assertThat(List.of(one.get(),many.get())).containsExactlyInAnyOrder(201,409);}finally{pool.shutdownNow();}
        assertThat(db.queryForObject("select count(*) from availability_slots where teacher_id=?",Long.class,t.accountId())).isEqualTo(1L);
    }
    @Test void 夏令时跳转区间拒绝并可跳过() throws Exception {
        var selected=List.of("America/New_York","Pacific/Auckland","Europe/London").stream()
            .map(z->Map.entry(z,ZoneId.of(z).getRules().nextTransition(Instant.now())))
            .min(Comparator.comparing(e->e.getValue().getInstant())).orElseThrow();
        var transition=selected.getValue();var invalid=(transition.isGap()?transition.getDateTimeBefore():transition.getDateTimeAfter()).plusMinutes(15);
        var t=account("TEACHER");var r=rule();r.put("startDate",invalid.toLocalDate().toString());r.put("startTime",invalid.toLocalTime().toString());r.put("timeZone",selected.getKey());r.put("weekCount",2);
        call("POST","/api/v1/scheduling/series/preview",t,r,400);r.put("skipDates",List.of(invalid.toLocalDate().toString()));var p=call("POST","/api/v1/scheduling/series/preview",t,r,200);assertThat(p.path("publishCount").asInt()).isEqualTo(1);assertThat(p.path("occurrences").get(1).path("startAt").asText()).endsWith(invalid.plusWeeks(1).atZone(ZoneId.of(selected.getKey())).getOffset().toString());
    }
    @Test void 教师ICS导出仅本人档期且阻断指定别人() throws Exception {
        var t=account("TEACHER");var other=account("TEACHER");slot(t,LocalDate.now().plusDays(10),"15:00");slot(other,LocalDate.now().plusDays(10),"16:00");
        var response=mvc.perform(get("/api/v1/booking/calendar.ics").header("Authorization","Bearer "+t.accessToken())).andReturn().getResponse();assertThat(response.getStatus()).isEqualTo(200);var text=response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(text.split("BEGIN:VEVENT",-1)).hasSize(2);assertThat(response.getHeader("Cache-Control")).contains("no-store");assertThat(response.getHeader("Content-Disposition")).contains(".ics");
        call("GET","/api/v1/booking/calendar.ics?studentId="+other.accountId(),t,null,403);
    }
    @Test void 课程ICS学生与监护隔离撤销立即失效() throws Exception {
        var t=account("TEACHER");var s=account("STUDENT");var parent=account("PARENT");var slot=slot(t,LocalDate.now().plusDays(10),"15:00");
        var b=call("POST","/api/v1/booking/bookings",s,Map.of("teacherId",t.accountId(),"slotId",slot,"subject","数学","bookingType","FORMAL"),201);
        var initial=mvc.perform(get("/api/v1/booking/calendar.ics").header("Authorization","Bearer "+s.accessToken())).andReturn().getResponse();assertThat(initial.getContentAsString()).contains("STATUS:TENTATIVE");
        call("GET","/api/v1/booking/calendar.ics?studentId="+s.accountId(),account("STUDENT"),null,403);
        var profile=call("GET","/api/v1/student/me/profile",s,null,200);call("POST","/api/v1/parent/me/children",parent,Map.of("studentNo",profile.path("studentNo").asText(),"relation","母亲"),201);call("POST","/api/v1/parent/me/children:confirm",s,Map.of(),200);var child=call("GET","/api/v1/parent/me/children",parent,null,200).get(0);
        var response=mvc.perform(get("/api/v1/booking/calendar.ics?childProfileId="+child.path("studentId").asText()).header("Authorization","Bearer "+parent.accessToken())).andReturn().getResponse();assertThat(response.getStatus()).isEqualTo(200);assertThat(response.getContentAsString()).contains(b.path("bookingId").asText());
        assertThat(db.update("update guardian_bindings set status='REVOKED' where id=?",UUID.fromString(child.path("bindingId").asText()))).isEqualTo(1);call("GET","/api/v1/booking/calendar.ics?childProfileId="+child.path("studentId").asText(),parent,null,403);
    }
    @Test void 重复规则分页无遗漏与他人规则隔离() throws Exception {
        var t=account("TEACHER");var other=account("TEACHER");for(int i=0;i<21;i++){var r=rule();r.put("startDate",LocalDate.now().plusDays(20+i).toString());r.put("weekCount",1);call("POST","/api/v1/scheduling/series",t,r,201);}
        var first=call("GET","/api/v1/scheduling/series",t,null,200);var second=call("GET","/api/v1/scheduling/series?page=1",t,null,200);
        assertThat(first.path("content").size()).isEqualTo(20);assertThat(second.path("content").size()).isEqualTo(1);assertThat(call("GET","/api/v1/scheduling/series",other,null,200).path("totalElements").asLong()).isZero();call("GET","/api/v1/scheduling/series?size=51",t,null,400);
    }
}
