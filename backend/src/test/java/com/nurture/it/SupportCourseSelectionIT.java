package com.nurture.it;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class SupportCourseSelectionIT {
 @Autowired MockMvc mvc;@Autowired ObjectMapper om;@Autowired JdbcTemplate db;
 static final String OPTIONS="/api/v1/support/cases/course-options";
 Support.Account account(String role)throws Exception{return Support.register(mvc,om,"Course"+role,role,"17"+String.format("%09d",ThreadLocalRandom.current().nextInt(1000000000)),"Support#2026");}
 JsonNode call(String method,String path,Support.Account a,Object body,int expected)throws Exception{
  var r=mvc.perform(request(HttpMethod.valueOf(method),path).header("Authorization","Bearer "+a.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body==null?new byte[0]:om.writeValueAsBytes(body))).andReturn().getResponse();
  assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(expected);return r.getContentAsString().isBlank()?om.nullNode():om.readTree(r.getContentAsString());
 }
 JsonNode booking(Support.Account s,Support.Account t,int day)throws Exception{
  var at=OffsetDateTime.now().plusDays(day);var slot=call("POST","/api/v1/scheduling/availability-slots",t,Map.of("startAt",at.toString(),"endAt",at.plusHours(1).toString()),201);
  return call("POST","/api/v1/booking/bookings",s,Map.of("teacherId",t.accountId(),"slotId",slot.path("slotId").asText(),"subject","课程选择数学","bookingType","FORMAL"),201);
 }
 JsonNode bind(Support.Account s,Support.Account p)throws Exception{
  var profile=call("GET","/api/v1/student/me/profile",s,null,200);
  return call("POST","/api/v1/parent/me/children",p,Map.of("studentNo",profile.path("studentNo").asText(),"relation","母亲"),201);
 }
 Map<String,Object> caseBody(JsonNode b){return Map.of("category","SCHEDULE","title","已选课程咨询","description","请核查这节课的时间安排","bookingId",b.path("bookingId").asText());}
 @Test void 三端真实归属选择与运营详情不泄漏联系方式()throws Exception{
  var s=account("STUDENT");var t=account("TEACHER");var p=account("PARENT");var b=booking(s,t,45);bind(s,p);
  call("POST","/api/v1/parent/me/children:confirm",s,Map.of(),200);
  for(var a:List.of(s,t,p)){
   var opts=call("GET",OPTIONS,a,null,200);assertThat(opts.path("totalElements").asInt()).isEqualTo(1);var row=opts.path("content").get(0);
   assertThat(row.path("bookingId").asText()).isEqualTo(b.path("bookingId").asText());assertThat(row.path("studentName").asText()).isEqualTo("IT-STUDENT");assertThat(row.path("teacherName").asText()).isEqualTo("IT-TEACHER");
   assertThat(row.toString()).doesNotContain(s.phone(),t.phone(),"storageKey","studentId","teacherId");
   var c=call("POST","/api/v1/support/cases",a,caseBody(b),201);assertThat(c.path("course").path("bookingId").asText()).isEqualTo(b.path("bookingId").asText());
   var login=call("POST","/api/v1/identity/auth/login:password",a,Map.of("role","ADMIN","account","13800000000","password","Admin#2026"),200);
   var admin=new Support.Account("13800000000",login.path("accessToken").asText(),UUID.fromString(db.queryForObject("select id::text from accounts where phone='13800000000'",String.class)));
   assertThat(call("GET","/api/v1/support/cases/"+c.path("caseId").asText(),admin,null,200).path("course")).isEqualTo(c.path("course"));
  }
 }
 @Test void 无关师生家长选择为空且伪造课程关联拒绝()throws Exception{
  var b=booking(account("STUDENT"),account("TEACHER"),45);
  for(var role:List.of("STUDENT","TEACHER","PARENT")){var a=account(role);assertThat(call("GET",OPTIONS,a,null,200).path("totalElements").asInt()).isZero();call("POST","/api/v1/support/cases",a,caseBody(b),403);}
 }
 @Test void 待确认不授权撤销后拒绝新关联且历史工单隐藏课程详情()throws Exception{
  var s=account("STUDENT");var p=account("PARENT");var b=booking(s,account("TEACHER"),45);bind(s,p);
  assertThat(call("GET",OPTIONS,p,null,200).path("totalElements").asInt()).isZero();call("POST","/api/v1/support/cases",p,caseBody(b),403);
  call("POST","/api/v1/parent/me/children:confirm",s,Map.of(),200);var c=call("POST","/api/v1/support/cases",p,caseBody(b),201);
  assertThat(db.update("update guardian_bindings set status='REVOKED' where parent_id=(select id from parent_profiles where account_id=?)",p.accountId())).isEqualTo(1);
  assertThat(call("GET",OPTIONS,p,null,200).path("totalElements").asInt()).isZero();call("POST","/api/v1/support/cases",p,caseBody(b),403);
  var old=call("GET","/api/v1/support/cases/"+c.path("caseId").asText(),p,null,200);assertThat(old.path("bookingId").asText()).isEqualTo(b.path("bookingId").asText());assertThat(old.path("course").isNull()).isTrue();
 }
 @Test void 二十一课程稳定分页及历史状态筛选()throws Exception{
  var s=account("STUDENT");var t=account("TEACHER");JsonNode b=null;
  for(int i=0;i<21;i++)b=booking(s,t,45+i);
  var first=call("GET",OPTIONS,s,null,200);var second=call("GET",OPTIONS+"?page=1",s,null,200);
  assertThat(first.path("totalElements").asInt()).isEqualTo(21);assertThat(first.path("content").size()).isEqualTo(20);assertThat(second.path("content").size()).isEqualTo(1);
  assertThat(first.findValuesAsText("bookingId")).doesNotContain(second.path("content").get(0).path("bookingId").asText());assertThat(call("GET",OPTIONS,s,null,200)).isEqualTo(first);
  db.update("update bookings set status='CANCELLED' where id=?",UUID.fromString(b.path("bookingId").asText()));
  var filtered=call("GET",OPTIONS+"?status=CANCELLED",s,null,200);assertThat(filtered.path("totalElements").asInt()).isEqualTo(1);assertThat(filtered.path("content").get(0).path("bookingId").asText()).isEqualTo(b.path("bookingId").asText());
 }
 @Test void 分页输入拒绝匿名与运营不能调用用户选择入口()throws Exception{
  var s=account("STUDENT");for(var suffix:List.of("?page=-1","?page=10001","?size=0","?size=51","?status=NOPE"))call("GET",OPTIONS+suffix,s,null,400);
  assertThat(mvc.perform(get(OPTIONS)).andReturn().getResponse().getStatus()).isEqualTo(403);
  var login=call("POST","/api/v1/identity/auth/login:password",s,Map.of("role","ADMIN","account","13800000000","password","Admin#2026"),200);
  var admin=new Support.Account("13800000000",login.path("accessToken").asText(),s.accountId());call("GET",OPTIONS,admin,null,403);
 }
}
