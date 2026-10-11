package com.nurture.it;
import com.fasterxml.jackson.databind.*;
import com.nurture.admin.app.SupportCaseService;
import com.nurture.shared.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.*;
import org.springframework.mock.web.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class SupportCasesIT {
 @Autowired MockMvc mvc;@Autowired ObjectMapper om;@Autowired JdbcTemplate db;@Autowired PlatformTransactionManager tm;@Autowired SupportCaseService service;
 static final byte[] PDF="%PDF-1.4\nSupport evidence\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
 Support.Account account(String role)throws Exception{return Support.register(mvc,om,role.toLowerCase(),role,"17"+String.format("%09d",ThreadLocalRandom.current().nextInt(1000000000)),"Support#2026");}
 Support.Account admin()throws Exception{var n=Support.postJson(mvc,om,"/api/v1/identity/auth/login:password",null,Map.of("role","ADMIN","account","13800000000","password","Admin#2026"));return new Support.Account("13800000000",n.path("accessToken").asText(),UUID.fromString(db.queryForObject("select id::text from accounts where phone='13800000000'",String.class)));}
 MockHttpServletResponse response(String method,String path,Support.Account a,Object body,String key)throws Exception{return mvc.perform(request(HttpMethod.valueOf(method),path).header("Authorization","Bearer "+a.accessToken()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body==null?new byte[0]:om.writeValueAsBytes(body))).andReturn().getResponse();}
 JsonNode call(String method,String path,Support.Account a,Object body,int expected)throws Exception{var r=response(method,path,a,body,UUID.randomUUID().toString());assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(expected);return r.getContentAsString().isBlank()?om.nullNode():om.readTree(r.getContentAsString());}
 Map<String,Object> body(){return Map.of("category","SCHEDULE","title","课程时间调整咨询","description","希望了解改期安排");}
 JsonNode create(Support.Account a)throws Exception{return call("POST","/api/v1/support/cases",a,body(),201);}
 String url(JsonNode c){return "/api/v1/support/cases/"+c.path("caseId").asText();}
 MockHttpServletResponse upload(Support.Account a,String url,String name,byte[] bytes,String key)throws Exception{var part=new MockPart("file",name,bytes);part.getHeaders().setContentType(MediaType.APPLICATION_PDF);return mvc.perform(multipart(url+"/evidence").file(new MockMultipartFile("file",name,"application/pdf",bytes)).part(part).header("Authorization","Bearer "+a.accessToken()).header("Idempotency-Key",key)).andReturn().getResponse();}
 JsonNode file(Support.Account a,JsonNode c)throws Exception{var r=upload(a,url(c),"证据.pdf",PDF,UUID.randomUUID().toString());assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);return om.readTree(r.getContentAsString());}
 @Test void 工单仅本人运营可读与写()throws Exception{
  var s=account("STUDENT");var other=account("TEACHER");var parent=account("PARENT");var c=create(s);var a=admin();
  call("GET",url(c),a,null,200);call("GET",url(c),other,null,403);call("GET",url(c),parent,null,403);
  call("POST",url(c)+"/replies",other,Map.of("body","越权"),403);
  assertThat(call("GET","/api/v1/support/cases",other,null,200).path("totalElements").asInt()).isZero();
  call("POST","/api/v1/support/cases",a,body(),403);
  assertThat(mvc.perform(get(url(c))).andReturn().getResponse().getStatus()).isEqualTo(403);assertThat(db.queryForObject("select count(*) from support_replies where case_id=?",Long.class,UUID.fromString(c.path("caseId").asText()))).isZero();
 }
 @Test void 工单创建及回复幂等载荷变更拒绝()throws Exception{
  var s=account("PARENT");String key=UUID.randomUUID().toString();var r=response("POST","/api/v1/support/cases",s,body(),key);assertThat(r.getStatus()).isEqualTo(201);var c=om.readTree(r.getContentAsString());
  assertThat(om.readTree(response("POST","/api/v1/support/cases",s,body(),key).getContentAsString())).isEqualTo(c);
  assertThat(response("POST","/api/v1/support/cases",s,Map.of("category","ACCOUNT","title","不同内容","description","问题"),key).getStatus()).isEqualTo(409);
  key=UUID.randomUUID().toString();assertThat(response("POST",url(c)+"/replies",s,Map.of("body","补充"),key).getStatus()).isEqualTo(201);
  assertThat(response("POST",url(c)+"/replies",s,Map.of("body","补充"),key).getStatus()).isEqualTo(201);
  assertThat(call("GET",url(c)+"/replies",s,null,200).path("totalElements").asInt()).isEqualTo(1);
 }
 @Test void 状态版本处理用户补充与终态固定()throws Exception{
  var s=account("STUDENT");var a=admin();var c=create(s);String u=url(c);
  c=call("POST",u+"/status",a,Map.of("status","WAITING_USER","revision",0,"note","请提供课程日期"),200);
  call("POST",u+"/status",a,Map.of("status","RESOLVED","revision",0,"note","过时版本"),409);
  call("POST",u+"/status",s,Map.of("status","RESOLVED","revision",1,"note","不能代运营解决"),409);
  c=call("POST",u+"/replies",s,Map.of("body","课程日期为下周一"),201);assertThat(c.path("status").asText()).isEqualTo("IN_PROGRESS");
  c=call("POST",u+"/status",a,Map.of("status","RESOLVED","revision",c.path("revision").asLong(),"note","改期方案已给出"),200);
  c=call("POST",u+"/status",s,Map.of("status","OPEN","revision",c.path("revision").asLong(),"note","还需要进一步处理"),200);
  c=call("POST",u+"/status",s,Map.of("status","CLOSED","revision",c.path("revision").asLong(),"note","已确认解决"),200);
  call("POST",u+"/replies",a,Map.of("body","不能新增"),409);
  assertThat(upload(s,u,"证据.pdf",PDF,UUID.randomUUID().toString()).getStatus()).isEqualTo(409);
  assertThat(db.queryForObject("select count(*) from system_notices where recipient_id=? and notice_type='SUPPORT'",Long.class,s.accountId())).isEqualTo(2L);
 }
 @Test void 私有证据下载字节头部幂等删除审计()throws Exception{
  var s=account("STUDENT");var a=admin();var other=account("STUDENT");var c=create(s);String key=UUID.randomUUID().toString();var r=upload(s,url(c),"证据.pdf",PDF,key);assertThat(r.getStatus()).isEqualTo(201);var f=om.readTree(r.getContentAsString());
  assertThat(f.toString()).doesNotContain("storageKey","storage_key");assertThat(om.readTree(upload(s,url(c),"证据.pdf",PDF,key).getContentAsString())).isEqualTo(f);
  String d="/api/v1/support/cases/evidence/"+f.path("fileId").asText();
  call("GET",d+"/download",other,null,403);call("DELETE",d,a,null,403);
  r=response("GET",d+"/download",a,null,UUID.randomUUID().toString());assertThat(r.getStatus()).isEqualTo(200);assertThat(r.getContentAsByteArray()).isEqualTo(PDF);assertThat(r.getHeader("Content-Disposition")).startsWith("attachment;");assertThat(r.getHeader("Cache-Control")).contains("no-store");assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
  call("DELETE",d,s,null,204);call("DELETE",d,s,null,204);call("GET",d+"/download",s,null,409);
  assertThat(call("GET","/api/v1/support/cases/quota",s,null,200).path("storedBytes").asLong()).isEqualTo(PDF.length);
  assertThat(db.queryForObject("select count(*) from audit_logs where caller_id=? and action_type='SUPPORT_EVIDENCE_REMOVE'",Long.class,s.accountId())).isEqualTo(1L);
 }
 @Test void 输入与伪装证据及保留数量限制()throws Exception{
  var s=account("TEACHER");var c=create(s);call("POST","/api/v1/support/cases",s,Map.of("category","OTHER","title"," ","description","test"),400);
  call("GET","/api/v1/support/cases?size=51",s,null,400);call("GET","/api/v1/support/cases?status=NOPE",s,null,400);
  assertThat(upload(s,url(c),"../证据.pdf",PDF,UUID.randomUUID().toString()).getStatus()).isEqualTo(400);
  assertThat(upload(s,url(c),"伪装.pdf","<script>test</script>".getBytes(),UUID.randomUUID().toString()).getStatus()).isEqualTo(400);
  byte[] huge=new byte[10485761];System.arraycopy(PDF,0,huge,0,PDF.length);assertThat(upload(s,url(c),"大文件.pdf",huge,UUID.randomUUID().toString()).getStatus()).isEqualTo(400);
  for(int i=0;i<10;i++)file(s,c);assertThat(upload(s,url(c),"超额.pdf",PDF,UUID.randomUUID().toString()).getStatus()).isEqualTo(409);
 }
 @Test void 跨工单上传容量并发仅一次写入()throws Exception{
  var s=account("STUDENT");var c1=create(s);var c2=create(s);db.update("insert into support_storage_quotas(owner_id,stored_bytes) values (?,?)",s.accountId(),SupportCaseService.QUOTA-PDF.length);
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var x=pool.submit(()->{start.await();return upload(s,url(c1),"并发.pdf",PDF,UUID.randomUUID().toString()).getStatus();});var y=pool.submit(()->{start.await();return upload(s,url(c2),"并发.pdf",PDF,UUID.randomUUID().toString()).getStatus();});start.countDown();assertThat(List.of(x.get(),y.get())).containsExactlyInAnyOrder(201,409);}finally{pool.shutdownNow();}
  assertThat(db.queryForObject("select stored_bytes from support_storage_quotas where owner_id=?",Long.class,s.accountId())).isEqualTo(SupportCaseService.QUOTA);
 }
 @Test void 工单与沟通记录超过二十可完整分页()throws Exception{
  var s=account("PARENT");JsonNode c=null;for(int i=0;i<21;i++)c=create(s);var first=call("GET","/api/v1/support/cases",s,null,200);var second=call("GET","/api/v1/support/cases?page=1",s,null,200);
  assertThat(first.path("content").size()).isEqualTo(20);assertThat(second.path("content").size()).isEqualTo(1);assertThat(first.findValuesAsText("caseId")).doesNotContain(second.path("content").get(0).path("caseId").asText());
  for(int i=0;i<21;i++)call("POST",url(c)+"/replies",s,Map.of("body","补充"+i),201);
  assertThat(call("GET",url(c)+"/replies",s,null,200).path("content").size()).isEqualTo(20);assertThat(call("GET",url(c)+"/replies?page=1",s,null,200).path("content").size()).isEqualTo(1);
 }
 @Test void 非归属课程不能关联到新工单()throws Exception{
  var s=account("STUDENT");var other=account("STUDENT");var t=account("TEACHER");var at=java.time.OffsetDateTime.now().plusDays(45);
  var slot=call("POST","/api/v1/scheduling/availability-slots",t,Map.of("startAt",at.toString(),"endAt",at.plusHours(1).toString()),201);
  var b=call("POST","/api/v1/booking/bookings",s,Map.of("teacherId",t.accountId(),"slotId",slot.path("slotId").asText(),"subject","数学","bookingType","FORMAL"),201);
  var body=new HashMap<String,Object>(body());body.put("bookingId",b.path("bookingId").asText());call("POST","/api/v1/support/cases",other,body,403);call("POST","/api/v1/support/cases",s,body,201);
 }
 @Test void 证据事务回滚同时恢复元数据容量和物理文件()throws Exception{
  var s=account("STUDENT");var c=create(s);long before=db.queryForObject("select count(*) from support_evidence",Long.class);String[] key={null};
  new TransactionTemplate(tm).executeWithoutResult(tx->{try{var f=file(s,c);key[0]=db.queryForObject("select storage_key from support_evidence where id=?",String.class,UUID.fromString(f.path("fileId").asText()));assertThat(java.nio.file.Files.exists(java.nio.file.Path.of("./target/it-files",key[0]))).isTrue();tx.setRollbackOnly();}catch(Exception e){throw new RuntimeException(e);}});
  assertThat(db.queryForObject("select count(*) from support_evidence",Long.class)).isEqualTo(before);assertThat(java.nio.file.Files.exists(java.nio.file.Path.of("./target/it-files",key[0]))).isFalse();assertThat(call("GET","/api/v1/support/cases/quota",s,null,200).path("storedBytes").asLong()).isZero();
 }
 @Test void 同一版本并发处理只允许一次状态更新()throws Exception{
  var s=account("STUDENT");var a=admin();var c=create(s);var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var x=pool.submit(()->{gate.await();return response("POST",url(c)+"/status",a,Map.of("status","RESOLVED","revision",0,"note","已处理"),UUID.randomUUID().toString()).getStatus();});var y=pool.submit(()->{gate.await();return response("POST",url(c)+"/status",a,Map.of("status","WAITING_USER","revision",0,"note","待补充"),UUID.randomUUID().toString()).getStatus();});gate.countDown();assertThat(List.of(x.get(),y.get())).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}
  assertThat(call("GET",url(c)+"/replies",s,null,200).path("totalElements").asLong()).isEqualTo(1L);
 }
}
