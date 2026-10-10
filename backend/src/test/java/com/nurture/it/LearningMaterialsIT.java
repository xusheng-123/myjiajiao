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
import org.springframework.mock.web.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class LearningMaterialsIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper om; @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager tm;
    record Fixture(Support.Account student,Support.Account teacher,String booking,String task) {}
    static final byte[] PDF="%PDF-1.4\nlearning attachment fixture\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Support.Account account(String role) throws Exception {
        String phone;do{phone="18"+String.format("%09d",ThreadLocalRandom.current().nextInt(1_000_000_000));}while(db.queryForObject("select count(*) from accounts where phone=?",Long.class,phone)>0);
        return Support.register(mvc,om,role.toLowerCase(),role,phone,"Materials#12");
    }
    JsonNode call(String method,String url,Support.Account a,Object body,int expected) throws Exception {
        var response=mvc.perform(request(HttpMethod.valueOf(method),url).header("Authorization","Bearer "+a.accessToken())
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(body==null?new byte[0]:om.writeValueAsBytes(body))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsString().isBlank()?om.nullNode():om.readTree(response.getContentAsString());
    }
    Fixture fixture() throws Exception {
        var s=account("STUDENT");var t=account("TEACHER");var at=OffsetDateTime.now().plusDays(30);
        var slot=call("POST","/api/v1/scheduling/availability-slots",t,Map.of("startAt",at.toString(),"endAt",at.plusHours(1).toString()),201);
        var b=call("POST","/api/v1/booking/bookings",s,Map.of("teacherId",t.accountId(),"slotId",slot.path("slotId").asText(),"subject","数学","bookingType","FORMAL"),201).path("bookingId").asText();
        call("POST","/api/v1/booking/booking-requests/"+b+":decide?decision=approve",t,Map.of(),200);
        var task=call("POST","/api/v1/learning/teacher/tasks",t,Map.of("bookingId",b,"title","数学练习","description","提交解答","priority","NORMAL"),201).path("taskId").asText();
        return new Fixture(s,t,b,task);
    }
    Map<String,Object> noteBody(){return new HashMap<>(Map.of("subject","数学","title","函数符号错误","questionText","求函数截距","errorReason","移项符号遗漏","knowledgeTags","一次函数,符号","reviewDate","2026-12-01"));}
    JsonNode note(Fixture f) throws Exception{return call("POST","/api/v1/learning/mistakes",f.student(),noteBody(),201);}
    Map<String,Object> updated(JsonNode n,String status){var b=noteBody();b.put("revision",n.path("revision").asLong());b.put("status",status);return b;}
    MockHttpServletResponse uploadResponse(Support.Account a,String category,String context,String name,String type,byte[] bytes,String key) throws Exception {
        var file=new MockMultipartFile("file",name,type,bytes);
        var part=new MockPart("file",name,bytes);part.getHeaders().setContentType(MediaType.parseMediaType(type));
        return mvc.perform(multipart("/api/v1/learning/files").file(file).part(part)
            .part(new MockPart("category",category.getBytes()),new MockPart("contextId",context.getBytes()))
            .param("category",category).param("contextId",context).header("Authorization","Bearer "+a.accessToken())
            .header("Idempotency-Key",key)).andReturn().getResponse();
    }
    JsonNode upload(Support.Account a,String category,String context,String name,String type,byte[] bytes,int expected) throws Exception {
        var r=uploadResponse(a,category,context,name,type,bytes,UUID.randomUUID().toString());
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(expected);return om.readTree(r.getContentAsString());
    }
    String download(JsonNode file){return "/api/v1/learning/files/"+file.path("fileId").asText()+"/download";}
    @Test void 错题分类复习状态与真实任务闭环() throws Exception {
        var f=fixture();var n=note(f);String u="/api/v1/learning/mistakes/"+n.path("noteId").asText();
        assertThat(call("GET","/api/v1/learning/mistakes?subject=数学&tag=函数&status=UNMASTERED&dueBefore=2026-12-02",f.student(),null,200).path("totalElements").asInt()).isEqualTo(1);
        n=call("PUT",u,f.student(),updated(n,"REVIEWING"),200);
        call("PUT",u,f.student(),updated(om.createObjectNode().put("revision",0),"MASTERED"),409);
        n=call("POST",u+"/task",f.teacher(),Map.of("revision",n.path("revision").asLong(),"bookingId",f.booking()),201);
        String task=n.path("taskId").asText();
        call("POST",u+"/task",f.teacher(),Map.of("revision",n.path("revision").asLong(),"bookingId",f.booking()),409);
        call("POST","/api/v1/learning/tasks/"+task+":submit",f.student(),Map.of("content","重新列式"),200);
        call("POST","/api/v1/learning/tasks/"+task+":feedback",f.teacher(),Map.of("feedback","符号已修正"),200);
        n=call("GET","/api/v1/learning/mistakes",f.student(),null,200).path("content").get(0);
        assertThat(n.path("status").asText()).isEqualTo("REVIEWING");assertThat(n.path("taskStatus").asText()).isEqualTo("COMPLETED");
        n=call("PUT",u,f.student(),updated(n,"MASTERED"),200);
        call("DELETE",u+"?revision="+n.path("revision").asLong(),f.student(),null,204);
        assertThat(call("GET","/api/v1/learning/mistakes",f.student(),null,200).path("totalElements").asInt()).isZero();
        assertThat(db.queryForObject("select count(*) from learning_tasks where id=?",Long.class,UUID.fromString(task))).isEqualTo(1L);
    }
    @Test void 错题归属代录及输入校验() throws Exception {
        var f=fixture();var n=note(f);var outsider=account("STUDENT");String u="/api/v1/learning/mistakes/"+n.path("noteId").asText();
        call("GET","/api/v1/learning/mistakes?studentId="+f.student().accountId(),outsider,null,403);
        call("PUT",u,f.teacher(),updated(n,"MASTERED"),403);
        call("DELETE",u+"?revision=0",outsider,null,403);
        var b=noteBody();b.put("studentId",f.student().accountId());var byTeacher=call("POST","/api/v1/learning/mistakes",f.teacher(),b,201);
        call("PUT","/api/v1/learning/mistakes/"+byTeacher.path("noteId").asText(),f.teacher(),updated(byTeacher,"REVIEWING"),200);
        b.put("title"," ");call("POST","/api/v1/learning/mistakes",f.teacher(),b,400);
        call("GET","/api/v1/learning/mistakes?size=51",f.student(),null,400);call("GET","/api/v1/learning/mistakes?tag=%25",f.student(),null,400);
    }
    @Test void 错题分页无遗漏且转任务跨学生被拒绝() throws Exception {
        var f=fixture();var other=fixture();JsonNode n=null;for(int i=0;i<21;i++)n=note(f);
        var first=call("GET","/api/v1/learning/mistakes",f.student(),null,200);var second=call("GET","/api/v1/learning/mistakes?page=1",f.student(),null,200);
        assertThat(first.path("content").size()).isEqualTo(20);assertThat(second.path("content").size()).isEqualTo(1);
        call("POST","/api/v1/learning/mistakes/"+n.path("noteId").asText()+"/task",f.teacher(),Map.of("revision",0,"bookingId",other.booking()),403);
        assertThat(db.queryForObject("select task_id from mistake_notes where id=?",UUID.class,UUID.fromString(n.path("noteId").asText()))).isNull();
    }
    @Test void 附件字节下载类型头路径不暴露及提交后冻结() throws Exception {
        var f=fixture();var file=upload(f.student(),"TASK_SUBMIT",f.task(),"解答.pdf","application/pdf",PDF,201);
        assertThat(file.toString()).doesNotContain("storageKey","storage_key");
        var r=mvc.perform(get(download(file)).header("Authorization","Bearer "+f.teacher().accessToken())).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(200);assertThat(r.getContentAsByteArray()).isEqualTo(PDF);
        assertThat(r.getHeader("Content-Disposition")).startsWith("attachment;");assertThat(r.getHeader("Cache-Control")).contains("no-store");assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        call("POST","/api/v1/learning/tasks/"+f.task()+":submit",f.student(),Map.of("content","作业见附件"),200);
        upload(f.student(),"TASK_SUBMIT",f.task(),"补充.pdf","application/pdf",PDF,409);
        call("DELETE","/api/v1/learning/files/"+file.path("fileId").asText(),f.student(),null,409);
        assertThat(call("GET","/api/v1/learning/files?taskId="+f.task(),f.student(),null,200).path("content").get(0).path("canDelete").asBoolean()).isFalse();
    }
    @Test void 附件越权类别与其他教师隔离() throws Exception {
        var f=fixture();var other=account("TEACHER");var stranger=account("STUDENT");
        var file=upload(f.teacher(),"MATERIAL",f.booking(),"课件.pdf","application/pdf",PDF,201);
        call("GET",download(file),stranger,null,403);call("GET",download(file),other,null,403);
        upload(stranger,"TASK_SUBMIT",f.task(),"作业.pdf","application/pdf",PDF,403);
        upload(f.student(),"TASK_MATERIAL",f.task(),"课件.pdf","application/pdf",PDF,403);
        call("DELETE","/api/v1/learning/files/"+file.path("fileId").asText(),f.student(),null,403);
    }
    @Test void 拒绝伪装类型危险文件名空文件与超量() throws Exception {
        var f=fixture();long before=db.queryForObject("select count(*) from learning_files",Long.class);
        for(String name:List.of("../作业.pdf","C:evil.pdf","script.html","macro.docm"))upload(f.student(),"TASK_SUBMIT",f.task(),name,"application/pdf",PDF,400);
        upload(f.student(),"TASK_SUBMIT",f.task(),"fake.pdf","application/pdf","<script>alert(1)</script>".getBytes(),400);
        upload(f.student(),"TASK_SUBMIT",f.task(),"empty.pdf","application/pdf",new byte[0],400);
        upload(f.student(),"TASK_SUBMIT",f.task(),"type.pdf","text/html",PDF,400);
        byte[] oversized=new byte[20971521];System.arraycopy(PDF,0,oversized,0,PDF.length);
        upload(f.student(),"TASK_SUBMIT",f.task(),"big.pdf","application/pdf",oversized,400);
        assertThat(db.queryForObject("select count(*) from learning_files",Long.class)).isEqualTo(before);
    }
    @Test void 移除逻辑保留容量审计与幂等重放() throws Exception {
        var f=fixture();String key=UUID.randomUUID().toString();var r=uploadResponse(f.teacher(),"MATERIAL",f.booking(),"课件.pdf","application/pdf",PDF,key);
        assertThat(r.getStatus()).isEqualTo(201);var file=om.readTree(r.getContentAsString());
        var replay=uploadResponse(f.teacher(),"MATERIAL",f.booking(),"课件.pdf","application/pdf",PDF,key);assertThat(replay.getStatus()).isEqualTo(201);assertThat(om.readTree(replay.getContentAsString()).path("fileId")).isEqualTo(file.path("fileId"));
        var changed=uploadResponse(f.teacher(),"MATERIAL",f.booking(),"另一份.pdf","application/pdf",PDF,key);assertThat(changed.getStatus()).isEqualTo(409);
        call("DELETE","/api/v1/learning/files/"+file.path("fileId").asText(),f.teacher(),null,204);
        call("GET",download(file),f.student(),null,409);
        assertThat(call("GET","/api/v1/learning/files/quota",f.teacher(),null,200).path("storedBytes").asLong()).isEqualTo(PDF.length);
        assertThat(db.queryForObject("select deleted from learning_files where id=?",Boolean.class,UUID.fromString(file.path("fileId").asText()))).isTrue();
        assertThat(db.queryForObject("select count(*) from audit_logs where action_type='LEARNING_FILE_REMOVE' and caller_id=?",Long.class,f.teacher().accountId())).isEqualTo(1L);
    }
    @Test void 容量并发只有一次上传成功() throws Exception {
        var f=fixture();db.update("insert into learning_storage_quotas(owner_id,stored_bytes) values (?,?)",f.student().accountId(),209715200L-PDF.length);
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{List<Future<Integer>> jobs=new ArrayList<>();for(int i=0;i<2;i++)jobs.add(pool.submit(()->{gate.await();return uploadResponse(f.student(),"TASK_SUBMIT",f.task(),"并发.pdf","application/pdf",PDF,UUID.randomUUID().toString()).getStatus();}));gate.countDown();assertThat(List.of(jobs.get(0).get(),jobs.get(1).get())).containsExactlyInAnyOrder(201,409);}
        finally{pool.shutdownNow();}
        assertThat(db.queryForObject("select stored_bytes from learning_storage_quotas where owner_id=?",Long.class,f.student().accountId())).isEqualTo(209715200L);
        assertThat(db.queryForObject("select count(*) from learning_files where task_id=?",Long.class,UUID.fromString(f.task()))).isEqualTo(1L);
    }
    @Test void 关联附件上限及答疑关闭限制() throws Exception {
        var f=fixture();for(int i=0;i<20;i++)upload(f.student(),"TASK_SUBMIT",f.task(),"作业"+i+".pdf","application/pdf",PDF,201);
        upload(f.teacher(),"TASK_MATERIAL",f.task(),"第21份.pdf","application/pdf",PDF,409);
        var q=call("POST","/api/v1/learning/questions",f.student(),Map.of("teacherId",f.teacher().accountId(),"subject","数学","title","提问","content","符号怎么处理"),201).path("questionId").asText();
        upload(f.student(),"QUESTION",q,"题目.pdf","application/pdf",PDF,201);upload(f.teacher(),"QUESTION",q,"讲解.pdf","application/pdf",PDF,201);
        call("POST","/api/v1/learning/questions/"+q+":close",f.student(),Map.of(),200);
        upload(f.teacher(),"QUESTION",q,"补充.pdf","application/pdf",PDF,409);
    }
    @Test void 有效监护只读与解绑立即失效() throws Exception {
        var f=fixture();var parent=account("PARENT");note(f);var file=upload(f.teacher(),"MATERIAL",f.booking(),"课件.pdf","application/pdf",PDF,201);
        var profile=call("GET","/api/v1/student/me/profile",f.student(),null,200);
        call("POST","/api/v1/parent/me/children",parent,Map.of("studentNo",profile.path("studentNo").asText(),"relation","母亲"),201);
        call("GET",download(file),parent,null,403);call("POST","/api/v1/parent/me/children:confirm",f.student(),Map.of(),200);
        var children=call("GET","/api/v1/parent/me/children",parent,null,200);var child=children.get(0).path("studentId").asText();
        assertThat(call("GET","/api/v1/learning/mistakes?childProfileId="+child,parent,null,200).path("totalElements").asInt()).isEqualTo(1);
        var response=mvc.perform(get(download(file)).header("Authorization","Bearer "+parent.accessToken())).andReturn().getResponse();assertThat(response.getStatus()).isEqualTo(200);
        call("GET","/api/v1/learning/files/quota",parent,null,403);upload(parent,"MATERIAL",f.booking(),"file.pdf","application/pdf",PDF,403);
        assertThat(db.update("update guardian_bindings set status='REVOKED' where id=?",UUID.fromString(children.get(0).path("bindingId").asText()))).isEqualTo(1);call("GET",download(file),parent,null,403);
        call("GET","/api/v1/learning/mistakes?childProfileId="+child,parent,null,403);
    }
    @Test void 文件与元数据随事务失败回滚() throws Exception {
        var f=fixture();long before=db.queryForObject("select count(*) from learning_files",Long.class);
        final String[] key={null};
        new TransactionTemplate(tm).executeWithoutResult(tx->{try{
            var file=upload(f.teacher(),"MATERIAL",f.booking(),"rollback.pdf","application/pdf",PDF,201);
            key[0]=db.queryForObject("select storage_key from learning_files where id=?",String.class,UUID.fromString(file.path("fileId").asText()));tx.setRollbackOnly();
            assertThat(java.nio.file.Files.exists(java.nio.file.Path.of("./target/it-files",key[0]))).isTrue();
        }catch(Exception e){throw new RuntimeException(e);}});
        assertThat(db.queryForObject("select count(*) from learning_files",Long.class)).isEqualTo(before);
        assertThat(java.nio.file.Files.exists(java.nio.file.Path.of("./target/it-files",key[0]))).isFalse();
    }
    @Test void 错题并发编辑防止覆盖() throws Exception {
        var f=fixture();var n=note(f);var body=updated(n,"REVIEWING");var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{List<Future<Integer>> jobs=new ArrayList<>();for(int i=0;i<2;i++)jobs.add(pool.submit(()->{gate.await();return mvc.perform(put("/api/v1/learning/mistakes/"+n.path("noteId").asText()).header("Authorization","Bearer "+f.student().accessToken()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(body))).andReturn().getResponse().getStatus();}));gate.countDown();assertThat(List.of(jobs.get(0).get(),jobs.get(1).get())).containsExactlyInAnyOrder(200,409);}
        finally{pool.shutdownNow();}
        assertThat(call("GET","/api/v1/learning/mistakes",f.student(),null,200).path("content").get(0).path("revision").asLong()).isEqualTo(1);
    }
}
