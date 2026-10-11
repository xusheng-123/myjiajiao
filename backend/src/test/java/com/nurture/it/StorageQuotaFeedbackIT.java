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
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class StorageQuotaFeedbackIT {
 @Autowired MockMvc mvc;@Autowired ObjectMapper om;@Autowired JdbcTemplate db;
 Support.Account account(String role)throws Exception{return Support.register(mvc,om,role.toLowerCase(),role,"17"+String.format("%09d",ThreadLocalRandom.current().nextInt(1000000000)),"Quota#2026");}
 JsonNode getQuota(Support.Account a,String path,int status)throws Exception{var r=mvc.perform(get(path).header("Authorization","Bearer "+a.accessToken())).andReturn().getResponse();assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);return om.readTree(r.getContentAsString());}
 @Test void 三种容量只读剩余与八成预警按本人分区()throws Exception{
  var s=account("STUDENT");var other=account("STUDENT");
  var tables=List.of("learning_storage_quotas","support_storage_quotas","consultation_storage_quotas");var paths=List.of("/api/v1/learning/files/quota","/api/v1/support/cases/quota","/api/v1/messaging/files/quota");
  for(int i=0;i<3;i++){
   long limit=(i==0?200:100)*1024L*1024;
   assertThat(getQuota(s,paths.get(i),200).path("remainingBytes").asLong()).isEqualTo(limit);
   assertThat(db.queryForObject("select count(*) from "+tables.get(i)+" where owner_id=?",Long.class,s.accountId())).isZero();
   db.update("insert into "+tables.get(i)+"(owner_id,stored_bytes) values (?,?)",s.accountId(),limit*4/5-1);
   assertThat(getQuota(s,paths.get(i),200).path("nearLimit").asBoolean()).isFalse();
   db.update("update "+tables.get(i)+" set stored_bytes=? where owner_id=?",limit*4/5,s.accountId());var q=getQuota(s,paths.get(i),200);
   assertThat(q.path("remainingBytes").asLong()).isEqualTo(limit/5);assertThat(q.path("nearLimit").asBoolean()).isTrue();assertThat(q.path("retentionNotice").asText()).contains("当前不自动到期删除");
   assertThat(getQuota(other,paths.get(i)+"?ownerId="+s.accountId(),200).path("storedBytes").asLong()).isZero();
   db.update("update "+tables.get(i)+" set stored_bytes=? where owner_id=?",limit,s.accountId());assertThat(getQuota(s,paths.get(i),200).path("remainingBytes").asLong()).isZero();
  }
 }
 @Test void 真实上传逻辑移除保留容量分解不释放且他人看不到()throws Exception{
  var s=account("STUDENT");var other=account("STUDENT");var c=Support.postJson(mvc,om,"/api/v1/support/cases",s.accessToken(),Map.of("category","OTHER","title","容量验收","description","保留审计证据"));byte[] pdf="%PDF-1.4\nQuota\n%%EOF".getBytes();
  var upload=mvc.perform(multipart("/api/v1/support/cases/"+c.path("caseId").asText()+"/evidence").file(new MockMultipartFile("file","quota.pdf","application/pdf",pdf)).header("Authorization","Bearer "+s.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString())).andReturn().getResponse();assertThat(upload.getStatus()).as(upload.getContentAsString()).isEqualTo(201);
  var f=om.readTree(upload.getContentAsString());String key=db.queryForObject("select storage_key from support_evidence where id=?",String.class,UUID.fromString(f.path("fileId").asText()));
  assertThat(getQuota(s,"/api/v1/support/cases/quota",200).path("removedRetainedBytes").asLong()).isZero();
  var deleted=mvc.perform(delete("/api/v1/support/cases/evidence/"+f.path("fileId").asText()).header("Authorization","Bearer "+s.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString())).andReturn().getResponse();assertThat(deleted.getStatus()).isEqualTo(204);
  var q=getQuota(s,"/api/v1/support/cases/quota",200);assertThat(q.path("storedBytes").asLong()).isEqualTo(pdf.length);assertThat(q.path("removedRetainedBytes").asLong()).isEqualTo(pdf.length);assertThat(q.path("remainingBytes").asLong()).isEqualTo(104857600-pdf.length);
  assertThat(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("target/it-files",key))).isEqualTo(pdf);assertThat(getQuota(other,"/api/v1/support/cases/quota",200).path("removedRetainedBytes").asLong()).isZero();
 }
 @Test void 学习移除分解依据本人元数据和受限角色()throws Exception{
  var s=account("STUDENT");var t=account("TEACHER");var p=account("PARENT");var at=java.time.OffsetDateTime.now().plusDays(44);
  var slot=Support.postJson(mvc,om,"/api/v1/scheduling/availability-slots",t.accessToken(),Map.of("startAt",at.toString(),"endAt",at.plusHours(1).toString()));var b=Support.postJson(mvc,om,"/api/v1/booking/bookings",s.accessToken(),Map.of("teacherId",t.accountId(),"slotId",slot.path("slotId").asText(),"subject","容量数学","bookingType","FORMAL"));
  Support.postJson(mvc,om,"/api/v1/booking/booking-requests/"+b.path("bookingId").asText()+":decide?decision=approve",t.accessToken(),Map.of());byte[] pdf="%PDF-1.4\nLearning quota\n%%EOF".getBytes();
  var r=mvc.perform(multipart("/api/v1/learning/files").file(new MockMultipartFile("file","learning.pdf","application/pdf",pdf)).param("category","MATERIAL").param("contextId",b.path("bookingId").asText()).header("Authorization","Bearer "+t.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString())).andReturn().getResponse();assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);var f=om.readTree(r.getContentAsString());
  assertThat(mvc.perform(delete("/api/v1/learning/files/"+f.path("fileId").asText()).header("Authorization","Bearer "+t.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString())).andReturn().getResponse().getStatus()).isEqualTo(204);
  assertThat(getQuota(t,"/api/v1/learning/files/quota",200).path("removedRetainedBytes").asLong()).isEqualTo(pdf.length);assertThat(getQuota(s,"/api/v1/learning/files/quota",200).path("storedBytes").asLong()).isZero();getQuota(p,"/api/v1/learning/files/quota",403);getQuota(p,"/api/v1/messaging/files/quota",403);
  assertThat(mvc.perform(get("/api/v1/support/cases/quota")).andReturn().getResponse().getStatus()).isEqualTo(403);
 }
}
