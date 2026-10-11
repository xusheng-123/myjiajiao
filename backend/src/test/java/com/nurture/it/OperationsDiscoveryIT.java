package com.nurture.it;
import com.fasterxml.jackson.databind.*;
import com.nurture.verification.app.VerificationApplicationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class OperationsDiscoveryIT {
 @Autowired MockMvc mvc;@Autowired ObjectMapper om;@Autowired JdbcTemplate db;@Autowired VerificationApplicationService verification;
 Support.Account teacher;String admin,subject;
 @BeforeEach void setup()throws Exception{teacher=Support.register(mvc,om,"teacher","TEACHER","16"+String.format("%09d",ThreadLocalRandom.current().nextInt(1000000000)),"Operations#2026");subject=UUID.randomUUID().toString().replace("-","").substring(0,24);admin=Support.postJson(mvc,om,"/api/v1/identity/auth/login:password",null,Map.of("role","ADMIN","account","13800000000","password","Admin#2026")).path("accessToken").asText();}
 JsonNode offering(String subject,long price)throws Exception{return Support.postJson(mvc,om,"/api/v1/teacher/me/subjects",teacher.accessToken(),Map.of("subject",subject,"gradeBand","高中","pricePerHourCents",price));}
 JsonNode getJson(String path,String token)throws Exception{var q=get(path);if(token!=null)q.header("Authorization","Bearer "+token);return om.readTree(mvc.perform(q).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 void approve()throws Exception{byte[] pdf="%PDF-1.4\nProof\n%%EOF".getBytes();mvc.perform(multipart("/api/v1/verification/cases").file(new MockMultipartFile("idDocument","id.pdf","application/pdf",pdf)).file(new MockMultipartFile("degreeCert","degree.pdf","application/pdf",pdf)).header("Authorization","Bearer "+teacher.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isCreated());Support.postJson(mvc,om,"/api/v1/admin/verification-cases/"+verification.latestOfTeacher(teacher.accountId()).getId()+":decide",admin,Map.of("approve",true,"note","真实审核通过"));}
 String path(JsonNode o){return "/api/v1/teacher/me/subjects/"+o.path("offeringId").asText();}
 void delist(JsonNode o)throws Exception{mvc.perform(delete(path(o)).header("Authorization","Bearer "+teacher.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isNoContent());}
 @Test void 全量排序分页突破二百条截断且匿名只展示已审核()throws Exception{
  approve();for(int i=0;i<211;i++)offering(subject+String.format("%03d",i),30000-i*10);
  var low=getJson("/api/v1/matching/public/teachers-page?subject="+subject+"&sort=price-asc&size=20",null);
  assertThat(low.path("totalElements").asInt()).isEqualTo(211);assertThat(low.path("content").get(0).path("pricePerHourCents").asLong()).isEqualTo(27900);
  Set<String> seen=new HashSet<>();for(int page=0;page<11;page++){var rows=getJson("/api/v1/matching/public/teachers-page?subject="+subject+"&sort=price-asc&page="+page,null).path("content");for(var r:rows)assertThat(seen.add(r.path("subject").asText())).isTrue();}assertThat(seen).hasSize(211);
  var detail=getJson("/api/v1/matching/public/teachers/"+teacher.accountId(),null);assertThat(detail.path("offerings").size()).isEqualTo(211);
 }
 @Test void 未审核与锁定教师在分页契约中不可见()throws Exception{
  offering(subject,15000);assertThat(getJson("/api/v1/matching/public/teachers-page?subject="+subject+"&includeUnverified=true",null).path("totalElements").asInt()).isZero();approve();
  assertThat(getJson("/api/v1/matching/public/teachers-page?subject="+subject,null).path("totalElements").asInt()).isEqualTo(1);
  Support.postJson(mvc,om,"/api/v1/admin/accounts/"+teacher.accountId()+":lock",admin,Map.of("note","暂时冻结"));assertThat(getJson("/api/v1/matching/public/teachers-page?subject="+subject,null).path("totalElements").asInt()).isZero();
 }
 @Test void 重新上架归属幂等审计销售与检索一致()throws Exception{
  approve();var o=offering(subject,16800);delist(o);assertThat(getJson("/api/v1/teacher/me/subjects",teacher.accessToken())).isEmpty();assertThat(getJson("/api/v1/teacher/me/subjects?includeInactive=true",teacher.accessToken()).size()).isEqualTo(1);
  var other=Support.register(mvc,om,"teacher","TEACHER","16"+String.format("%09d",ThreadLocalRandom.current().nextInt(1000000000)),"Operations#2026");
  mvc.perform(post(path(o)+":activate").header("Authorization","Bearer "+other.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isForbidden());
  String key=UUID.randomUUID().toString();for(int i=0;i<2;i++)mvc.perform(post(path(o)+":activate").header("Authorization","Bearer "+teacher.accessToken()).header("Idempotency-Key",key)).andExpect(status().isOk());
  mvc.perform(post(path(o)+":activate").header("Authorization","Bearer "+teacher.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isOk());
  assertThat(getJson("/api/v1/matching/public/teachers-page?subject="+subject,null).path("totalElements").asInt()).isEqualTo(1);
  assertThat(db.queryForObject("select count(*) from audit_logs where caller_id=? and action_type='OFFERING_RELIST'",Long.class,teacher.accountId())).isEqualTo(1L);
 }
 @Test void 同科目学段另有在售条目恢复被拒绝()throws Exception{
  var old=offering(subject,15000);delist(old);offering(subject,18000);mvc.perform(post(path(old)+":activate").header("Authorization","Bearer "+teacher.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isConflict());
  assertThat(getJson("/api/v1/teacher/me/subjects",teacher.accessToken()).size()).isEqualTo(1);
 }
 @Test void 运营队列稳定分页状态过滤与权限边界()throws Exception{
  for(int i=0;i<21;i++)db.update("insert into verification_cases(id,created_at,updated_at,teacher_id,submitted_at,status) values (?,now(),now(),?,now(),'SUBMITTED')",UUID.randomUUID(),teacher.accountId());
  var first=getJson("/api/v1/admin/verification-cases?status=SUBMITTED",admin);var second=getJson("/api/v1/admin/verification-cases?status=SUBMITTED&page=1",admin);assertThat(first.path("content").size()).isEqualTo(20);assertThat(second.path("content").size()).isGreaterThan(0);assertThat(first.findValuesAsText("caseId")).doesNotContainAnyElementsOf(second.findValuesAsText("caseId"));
  Support.postJson(mvc,om,"/api/v1/admin/accounts/"+teacher.accountId()+":lock",admin,Map.of("note","过滤验证"));assertThat(getJson("/api/v1/admin/accounts?status=LOCKED&size=100",admin).findValuesAsText("accountId")).contains(teacher.accountId().toString());
  for(String queue:List.of("accounts","verification-cases","refund-cases","audit-logs","orders/pending-payment")){
   mvc.perform(get("/api/v1/admin/"+queue+"?page=-1").header("Authorization","Bearer "+admin)).andExpect(status().isBadRequest());
   mvc.perform(get("/api/v1/admin/"+queue+"?size=101").header("Authorization","Bearer "+admin)).andExpect(status().isBadRequest());
   mvc.perform(get("/api/v1/admin/"+queue)).andExpect(status().isForbidden());
  }
 }
 @Test void 公开分页参数与筛选校验()throws Exception{
  for(String query:List.of("size=51","page=-1","sort=invalid","maxPricePerHourCents=-1"))mvc.perform(get("/api/v1/matching/public/teachers-page?"+query)).andExpect(status().isBadRequest());
 }
}
