package com.nurture.it;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class AssessmentIT{
 @Autowired MockMvc mvc;@Autowired ObjectMapper om;@Autowired JdbcTemplate db;
 record Fixture(Support.Account s,Support.Account t,String booking){}
 Support.Account account(String role)throws Exception{return Support.register(mvc,om,role.toLowerCase(),role,"18"+String.format("%09d",ThreadLocalRandom.current().nextInt(1000000000)),"Assessment#2026");}
 int response(String method,String url,Support.Account a,Object body,String key)throws Exception{return mvc.perform(request(HttpMethod.valueOf(method),url).header("Authorization","Bearer "+a.accessToken()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body==null?new byte[0]:om.writeValueAsBytes(body))).andReturn().getResponse().getStatus();}
 JsonNode call(String method,String url,Support.Account a,Object body,int expected)throws Exception{var r=mvc.perform(request(HttpMethod.valueOf(method),url).header("Authorization","Bearer "+a.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body==null?new byte[0]:om.writeValueAsBytes(body))).andReturn().getResponse();assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(expected);return r.getContentAsString().isBlank()?om.nullNode():om.readTree(r.getContentAsString());}
 String book(Support.Account s,Support.Account t)throws Exception{var at=java.time.OffsetDateTime.now().plusDays(60).plusSeconds(ThreadLocalRandom.current().nextInt(100000));var slot=call("POST","/api/v1/scheduling/availability-slots",t,Map.of("startAt",at.toString(),"endAt",at.plusMinutes(30).toString()),201);var b=call("POST","/api/v1/booking/bookings",s,Map.of("teacherId",t.accountId(),"slotId",slot.path("slotId").asText(),"subject","数学","bookingType","FORMAL"),201).path("bookingId").asText();call("POST","/api/v1/booking/booking-requests/"+b+":decide?decision=approve",t,Map.of(),200);return b;}
 Fixture fixture()throws Exception{var s=account("STUDENT");var t=account("TEACHER");return new Fixture(s,t,book(s,t));}
 List<Map<String,Object>> questions(){return List.of(Map.of("prompt","1+1=?","choices",List.of("1","2","3"),"correctIndex",1),Map.of("prompt","2×3=?","choices",List.of("5","6"),"correctIndex",1));}
 Map<String,Object> body(Fixture f){return new HashMap<>(Map.of("bookingId",f.booking(),"subject","数学","title","基础运算小测","scope","只覆盖两道基础题","questions",questions()));}
 JsonNode create(Fixture f)throws Exception{return call("POST","/api/v1/learning/assessments",f.t(),body(f),201);}
 String u(JsonNode q){return "/api/v1/learning/assessments/"+q.path("assessmentId").asText();}
 JsonNode publish(Fixture f,JsonNode q)throws Exception{return call("POST",u(q)+"/publish",f.t(),Map.of("revision",q.path("revision").asLong()),200);}
 @Test void 草稿不可见发布固定题目且答案提交前不泄露()throws Exception{
  var f=fixture();var q=create(f);assertThat(call("GET","/api/v1/learning/assessments",f.s(),null,200).path("totalElements").asInt()).isZero();call("GET",u(q),f.s(),null,403);call("POST",u(q)+"/archive",f.t(),Map.of("revision",0),409);
  q=publish(f,q);var student=call("GET",u(q),f.s(),null,200);assertThat(student.path("questions").get(0).path("correctIndex").isNull()).isTrue();
  var edit=body(f);edit.put("revision",q.path("revision").asLong());call("PUT",u(q),f.t(),edit,409);
  call("POST",u(q)+"/submit",f.s(),Map.of("answers",List.of(0,1),"correctCount",99),200);var submitted=call("GET",u(q),f.s(),null,200);
  assertThat(submitted.path("attempt").path("correctCount").asInt()).isEqualTo(1);assertThat(submitted.path("questions").get(0).path("correctIndex").asInt()).isEqualTo(1);
 }
 @Test void 草稿编辑版本与非法题目拒绝()throws Exception{
  var f=fixture();var q=create(f);var b=body(f);b.put("revision",0);b.put("title","修订小测");q=call("PUT",u(q),f.t(),b,200);assertThat(q.path("revision").asLong()).isEqualTo(1);call("PUT",u(q),f.t(),b,409);call("POST",u(q)+"/publish",f.t(),Map.of("revision",0),409);
  b=body(f);b.put("questions",List.of(Map.of("prompt","题目","choices",List.of("重复","重复"),"correctIndex",0)));call("POST","/api/v1/learning/assessments",f.t(),b,400);
  b.put("questions",Collections.nCopies(21,questions().get(0)));call("POST","/api/v1/learning/assessments",f.t(),b,400);
 }
 @Test void 跨学生与其他授课教师不可读取维护()throws Exception{
  var f=fixture();var other=account("TEACHER");book(f.s(),other);var stranger=account("STUDENT");var q=publish(f,create(f));
  call("GET",u(q),other,null,403);call("GET",u(q),stranger,null,403);call("POST",u(q)+"/submit",stranger,Map.of("answers",List.of(1,1)),403);
  call("POST",u(q)+"/comment",other,Map.of("revision",0,"comment","越权"),403);call("POST","/api/v1/learning/assessments",f.s(),body(f),403);
  assertThat(call("GET","/api/v1/learning/assessments?studentId="+f.s().accountId(),other,null,200).path("totalElements").asInt()).isZero();
 }
 @Test void 有效家长只读与撤销绑定立即失效()throws Exception{
  var f=fixture();var p=account("PARENT");var profile=call("GET","/api/v1/student/me/profile",f.s(),null,200);call("POST","/api/v1/parent/me/children",p,Map.of("studentNo",profile.path("studentNo").asText(),"relation","母亲"),201);call("POST","/api/v1/parent/me/children:confirm",f.s(),Map.of(),200);var children=call("GET","/api/v1/parent/me/children",p,null,200);var q=publish(f,create(f));
  var rows=call("GET","/api/v1/learning/assessments?childProfileId="+children.get(0).path("studentId").asText(),p,null,200);assertThat(rows.path("content").get(0).path("questions").get(0).path("correctIndex").isNull()).isTrue();call("POST",u(q)+"/submit",p,Map.of("answers",List.of(1,1)),403);
  assertThat(db.update("update guardian_bindings set status='REVOKED' where id=?",UUID.fromString(children.get(0).path("bindingId").asText()))).isEqualTo(1);call("GET",u(q),p,null,403);
 }
 @Test void 作答幂等同键重放与不同键重复拒绝()throws Exception{
  var f=fixture();var q=publish(f,create(f));String key=UUID.randomUUID().toString();var b=Map.of("answers",List.of(1,1));assertThat(response("POST",u(q)+"/submit",f.s(),b,key)).isEqualTo(200);assertThat(response("POST",u(q)+"/submit",f.s(),b,key)).isEqualTo(200);assertThat(response("POST",u(q)+"/submit",f.s(),Map.of("answers",List.of(0,1)),key)).isEqualTo(409);call("POST",u(q)+"/submit",f.s(),b,409);
  assertThat(db.queryForObject("select count(*) from assessment_attempts where assessment_id=?",Long.class,UUID.fromString(q.path("assessmentId").asText()))).isEqualTo(1L);
 }
 @Test void 并发提交仅一份真实成绩()throws Exception{
  var f=fixture();var q=publish(f,create(f));var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var x=pool.submit(()->{gate.await();return response("POST",u(q)+"/submit",f.s(),Map.of("answers",List.of(1,1)),UUID.randomUUID().toString());});var y=pool.submit(()->{gate.await();return response("POST",u(q)+"/submit",f.s(),Map.of("answers",List.of(0,1)),UUID.randomUUID().toString());});gate.countDown();assertThat(List.of(x.get(),y.get())).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}
 }
 @Test void 教师批注不改变评分旧版本拒绝归档保留结果()throws Exception{
  var f=fixture();var q=publish(f,create(f));q=call("POST",u(q)+"/submit",f.s(),Map.of("answers",List.of(0,1)),200);q=call("POST",u(q)+"/comment",f.t(),Map.of("revision",0,"comment","第一题需重做"),200);assertThat(q.path("attempt").path("correctCount").asInt()).isEqualTo(1);
  call("POST",u(q)+"/comment",f.t(),Map.of("revision",0,"comment","过时覆盖"),409);call("POST",u(q)+"/archive",f.t(),Map.of("revision",q.path("revision").asLong()),200);
  var r=call("GET","/api/v1/learning/assessments/report",f.s(),null,200);assertThat(r.path("scores").path("content").get(0).path("teacherComment").asText()).isEqualTo("第一题需重做");
 }
 @Test void 实际报告课程与任务统计不使用旧上限或空科目()throws Exception{
  var f=fixture();db.update("update bookings set status='COMPLETED' where id=?",UUID.fromString(f.booking()));var q=publish(f,create(f));call("POST",u(q)+"/submit",f.s(),Map.of("answers",List.of(1,1)),200);
  var r=call("GET","/api/v1/learning/assessments/report",f.s(),null,200);assertThat(r.path("lessonsCompleted").asLong()).isEqualTo(1);assertThat(r.path("tasksTotal").asLong()).isZero();assertThat(r.path("notice").asText()).contains("非全面能力诊断");
  call("GET","/api/v1/learning/assessments/report?page=-1",f.s(),null,400);call("POST",u(q)+"/submit",f.s(),Map.of("answers",List.of(-1,99)),409);
 }
 @Test void 成绩独立分页二十一份无遗漏与无效作答无成绩()throws Exception{
  var f=fixture();var q=publish(f,create(f));call("POST",u(q)+"/submit",f.s(),Map.of("answers",List.of(1)),400);call("POST",u(q)+"/submit",f.s(),Map.of("answers",List.of(99,1)),400);
  for(int i=0;i<21;i++){if(i>0)q=publish(f,create(f));call("POST",u(q)+"/submit",f.s(),Map.of("answers",List.of(1,1)),200);}
  var a=call("GET","/api/v1/learning/assessments/report",f.s(),null,200).path("scores");var b=call("GET","/api/v1/learning/assessments/report?page=1",f.s(),null,200).path("scores");assertThat(a.path("totalElements").asInt()).isEqualTo(21);assertThat(a.path("content").size()).isEqualTo(20);assertThat(b.path("content").size()).isEqualTo(1);assertThat(a.findValuesAsText("assessmentId")).doesNotContainAnyElementsOf(b.findValuesAsText("assessmentId"));
 }
}
