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
import org.springframework.mock.web.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class ConsultationFilesIT{
 @Autowired MockMvc mvc;@Autowired ObjectMapper om;@Autowired JdbcTemplate db;@Autowired PlatformTransactionManager tm;
 static final byte[] PDF="%PDF-1.4\nconsultation fixture\n%%EOF".getBytes();
 record Fixture(Support.Account s,Support.Account t,String conversation){}
 Support.Account account(String role)throws Exception{return Support.register(mvc,om,role.toLowerCase(),role,"19"+String.format("%09d",ThreadLocalRandom.current().nextInt(1000000000)),"Consultation#2026");}
 Fixture fixture()throws Exception{var s=account("STUDENT");var t=account("TEACHER");var c=Support.postJson(mvc,om,"/api/v1/messaging/conversations",s.accessToken(),Map.of("teacherId",t.accountId(),"subject","咨询附件")).path("conversationId").asText();return new Fixture(s,t,c);}
 MockHttpServletResponse upload(Support.Account a,String c,String name,byte[] bytes,String key)throws Exception{var part=new MockPart("file",name,bytes);part.getHeaders().setContentType(MediaType.APPLICATION_PDF);return mvc.perform(multipart("/api/v1/messaging/conversations/"+c+"/files").file(new MockMultipartFile("file",name,"application/pdf",bytes)).part(part).header("Authorization","Bearer "+a.accessToken()).header("Idempotency-Key",key)).andReturn().getResponse();}
 JsonNode file(Fixture f)throws Exception{var r=upload(f.s(),f.conversation(),"课程需求.pdf",PDF,UUID.randomUUID().toString());assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);return om.readTree(r.getContentAsString());}
 @Test void 附件与消息原子发布幂等未读与受控字节下载()throws Exception{
  var f=fixture();String key=UUID.randomUUID().toString();var r=upload(f.s(),f.conversation(),"需求.pdf",PDF,key);assertThat(r.getStatus()).isEqualTo(201);var file=om.readTree(r.getContentAsString());assertThat(file.toString()).doesNotContain("storageKey");
  assertThat(om.readTree(upload(f.s(),f.conversation(),"需求.pdf",PDF,key).getContentAsString())).isEqualTo(file);
  var msgs=Support.getJson(mvc,om,"/api/v1/messaging/conversations/"+f.conversation()+"/messages",f.t().accessToken());assertThat(msgs.size()).isEqualTo(1);assertThat(msgs.get(0).path("attachmentId").asText()).isEqualTo(file.path("fileId").asText());
  var c=Support.getJson(mvc,om,"/api/v1/messaging/conversations",f.t().accessToken());assertThat(c.get(0).path("unreadCount").asLong()).isEqualTo(1L);
  r=mvc.perform(get("/api/v1/messaging/files/"+file.path("fileId").asText()+"/download").header("Authorization","Bearer "+f.t().accessToken())).andReturn().getResponse();assertThat(r.getStatus()).isEqualTo(200);assertThat(r.getContentAsByteArray()).isEqualTo(PDF);assertThat(r.getHeader("Cache-Control")).contains("no-store");assertThat(r.getHeader("Content-Disposition")).startsWith("attachment;");assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
 }
 @Test void 会话以外第三方及家长不能上传下载()throws Exception{
  var f=fixture();var file=file(f);var other=account("TEACHER");var p=account("PARENT");String d="/api/v1/messaging/files/"+file.path("fileId").asText()+"/download";
  for(var a:List.of(other,p)){assertThat(mvc.perform(get(d).header("Authorization","Bearer "+a.accessToken())).andReturn().getResponse().getStatus()).isEqualTo(403);assertThat(upload(a,f.conversation(),"越权.pdf",PDF,UUID.randomUUID().toString()).getStatus()).isEqualTo(403);}
 }
 @Test void 伪装非法路径超量拒绝无消息残留()throws Exception{
  var f=fixture();for(String name:List.of("../evil.pdf","evil.html","宏.docm"))assertThat(upload(f.s(),f.conversation(),name,PDF,UUID.randomUUID().toString()).getStatus()).isEqualTo(400);
  assertThat(upload(f.s(),f.conversation(),"伪装.pdf","<html>test</html>".getBytes(),UUID.randomUUID().toString()).getStatus()).isEqualTo(400);
  byte[] huge=new byte[10485761];assertThat(upload(f.s(),f.conversation(),"超大.pdf",huge,UUID.randomUUID().toString()).getStatus()).isEqualTo(400);
  assertThat(Support.getJson(mvc,om,"/api/v1/messaging/conversations/"+f.conversation()+"/messages",f.t().accessToken())).isEmpty();
 }
 @Test void 容量并发防超额且回滚清理文件与消息()throws Exception{
  var f=fixture();db.update("insert into consultation_storage_quotas(owner_id,stored_bytes) values (?,?)",f.s().accountId(),104857600L-PDF.length);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{var a=pool.submit(()->{start.await();return upload(f.s(),f.conversation(),"并发.pdf",PDF,UUID.randomUUID().toString()).getStatus();});var b=pool.submit(()->{start.await();return upload(f.s(),f.conversation(),"并发.pdf",PDF,UUID.randomUUID().toString()).getStatus();});start.countDown();assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(201,409);}finally{pool.shutdownNow();}
  var fresh=fixture();String[] key={null};new TransactionTemplate(tm).executeWithoutResult(tx->{try{var file=file(fresh);key[0]=db.queryForObject("select storage_key from consultation_files where id=?",String.class,UUID.fromString(file.path("fileId").asText()));assertThat(java.nio.file.Files.exists(java.nio.file.Path.of("./target/it-files",key[0]))).isTrue();tx.setRollbackOnly();}catch(Exception e){throw new RuntimeException(e);}});
  assertThat(java.nio.file.Files.exists(java.nio.file.Path.of("./target/it-files",key[0]))).isFalse();assertThat(db.queryForObject("select count(*) from messages where conversation_id=?",Long.class,UUID.fromString(fresh.conversation()))).isZero();
 }
 @Test void 通知分页真实全量未读与标记归属()throws Exception{
  var s=account("STUDENT");var other=account("STUDENT");for(int i=0;i<21;i++)db.update("insert into system_notices(id,created_at,updated_at,recipient_id,notice_type,title) values (?,now(),now(),?,'ASSESSMENT',?)",UUID.randomUUID(),s.accountId(),"通知"+i);
  var first=Support.getJson(mvc,om,"/api/v1/messaging/notifications?page=0&size=10",s.accessToken());var second=Support.getJson(mvc,om,"/api/v1/messaging/notifications?page=1&size=10",s.accessToken());assertThat(first.path("unreadTotal").asInt()).isEqualTo(21);assertThat(first.path("pages").asInt()).isEqualTo(3);assertThat(first.findValuesAsText("noticeId")).doesNotContainAnyElementsOf(second.findValuesAsText("noticeId"));String id=first.path("items").get(0).path("noticeId").asText();
  assertThat(mvc.perform(post("/api/v1/messaging/notifications/"+id+":read").header("Authorization","Bearer "+other.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString())).andReturn().getResponse().getStatus()).isEqualTo(403);
  Support.postJson(mvc,om,"/api/v1/messaging/notifications/"+id+":read",s.accessToken(),Map.of());assertThat(Support.getJson(mvc,om,"/api/v1/messaging/notifications",s.accessToken()).path("unreadTotal").asInt()).isEqualTo(20);
 }
}
