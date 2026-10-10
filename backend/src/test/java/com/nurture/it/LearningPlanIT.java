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
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class LearningPlanIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate db;
    record Fixture(Support.Account student,Support.Account teacher,String booking) {}
    Support.Account account(String role) throws Exception {
        String phone;
        do { phone="19"+String.format("%09d",ThreadLocalRandom.current().nextInt(1_000_000_000)); }
        while(db.queryForObject("select count(*) from accounts where phone=?",Long.class,phone)>0);
        return Support.register(mvc,om,role.toLowerCase(),role,phone,"PlanSafe#12");
    }
    JsonNode call(String method,String url,Support.Account a,Object body,int status) throws Exception {
        return call(method,url,a,body,status,UUID.randomUUID().toString());
    }
    JsonNode call(String method,String url,Support.Account a,Object body,int expected,String key) throws Exception {
        var r=mvc.perform(request(HttpMethod.valueOf(method),url).header("Authorization","Bearer "+a.accessToken())
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
                .content(body==null?new byte[0]:om.writeValueAsBytes(body))).andReturn().getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(expected);
        return r.getContentAsString().isBlank()?om.nullNode():om.readTree(r.getContentAsString());
    }
    Fixture fixture() throws Exception {
        return lesson(account("STUDENT"),account("TEACHER"),10);
    }
    Fixture lesson(Support.Account student,Support.Account teacher,int days) throws Exception {
        var at=OffsetDateTime.now().plusDays(days);
        var slot=call("POST","/api/v1/scheduling/availability-slots",teacher,
                Map.of("startAt",at.toString(),"endAt",at.plusHours(1).toString()),201);
        var b=call("POST","/api/v1/booking/bookings",student,
                Map.of("teacherId",teacher.accountId(),"slotId",slot.path("slotId").asText(),"subject","数学","bookingType","FORMAL"),201);
        var id=b.path("bookingId").asText();
        call("POST","/api/v1/booking/booking-requests/"+id+":decide?decision=approve",teacher,Map.of(),200);
        return new Fixture(student,teacher,id);
    }
    Map<String,Object> planBody(String title) { return Map.of("subject","数学","title",title,"longTermGoal","形成稳定的解题步骤"); }
    JsonNode create(Fixture f) throws Exception { return call("POST","/api/v1/learning/plans",f.student(),planBody("数学阶段计划"),201); }
    String url(JsonNode p) { return "/api/v1/learning/plans/"+p.path("planId").asText(); }
    Map<String,Object> update(JsonNode p,String title,String status) { return Map.of("revision",p.path("revision").asLong(),
            "subject","数学","title",title,"longTermGoal","先独立列式，再检查单位","status",status); }
    Map<String,Object> milestone(JsonNode p,boolean completed) { return Map.of("revision",p.path("revision").asLong(),
            "title","一次函数","goal","独立解释图像与表达式的关系","targetDate","2027-01-01","completed",completed); }
    String task(Fixture f) throws Exception { return call("POST","/api/v1/learning/teacher/tasks",f.teacher(),
            Map.of("bookingId",f.booking(),"title","函数练习","description","写清计算步骤","priority","NORMAL"),201).path("taskId").asText(); }

    @Test void 本人建立教师调整关联真实任务进度与目标记录() throws Exception {
        var f=fixture(); var p=create(f); var u=url(p);
        assertThat(p.path("tasksCompleted").asLong()).isZero();
        p=call("PUT",u,f.teacher(),update(p,"教师协同目标","ACTIVE"),200);
        p=call("POST",u+"/milestones",f.teacher(),milestone(p,false),201);
        var task=task(f);
        p=call("POST",u+"/tasks",f.student(),Map.of("revision",p.path("revision").asLong(),"taskId",task),201);
        assertThat(p.path("tasks").size()).isEqualTo(1); assertThat(p.path("tasksCompleted").asLong()).isZero();
        call("POST","/api/v1/learning/tasks/"+task+":submit",f.student(),Map.of("content","已经写出推导过程"),200);
        assertThat(call("GET",u,f.student(),null,200).path("tasksCompleted").asLong()).isZero();
        call("POST","/api/v1/learning/tasks/"+task+":feedback",f.teacher(),Map.of("feedback","步骤清楚，可以完成"),200);
        p=call("GET",u,f.student(),null,200); assertThat(p.path("tasksCompleted").asLong()).isEqualTo(1);
        p=call("PUT",u+"/milestones/"+p.path("milestones").get(0).path("milestoneId").asText(),f.student(),milestone(p,true),200);
        assertThat(p.path("milestonesCompleted").asLong()).isEqualTo(1);
        call("POST","/api/v1/booking/bookings/"+f.booking()+":start",f.teacher(),Map.of(),200);
        call("POST","/api/v1/booking/bookings/"+f.booking()+":complete",f.teacher(),Map.of(),200);
        assertThat(call("GET",u,f.student(),null,200).path("lessonsCompleted").asLong()).isEqualTo(1);
        var history=call("GET",u+"/history",f.student(),null,200);
        assertThat(history.path("totalElements").asLong()).isEqualTo(5);
        assertThat(history.path("content").get(0).path("action").asText()).isEqualTo("MILESTONE_UPDATE");
        assertThat(history.path("content").toString()).contains("教师协同目标","形成稳定的解题步骤","函数练习");
    }
    @Test void 有效家长只读且跨学生教师与子女ID混用被拒绝() throws Exception {
        var f=fixture(); var p=create(f); var u=url(p); var parent=account("PARENT");
        var outsider=account("STUDENT"); var otherTeacher=account("TEACHER");
        var profile=call("GET","/api/v1/student/me/profile",f.student(),null,200);
        call("POST","/api/v1/parent/me/children",parent,Map.of("studentNo",profile.path("studentNo").asText(),"relation","母亲"),201);
        call("GET",u,parent,null,403);
        call("POST","/api/v1/parent/me/children:confirm",f.student(),Map.of(),200);
        var children=call("GET","/api/v1/parent/me/children",parent,null,200);
        var child=children.get(0).path("studentId").asText();
        var list=call("GET","/api/v1/learning/plans?childProfileId="+child,parent,null,200);
        assertThat(list.path("content").get(0).path("planId")).isEqualTo(p.path("planId"));
        call("GET",u+"/history",parent,null,200);
        call("PUT",u,parent,update(p,"不应修改","ACTIVE"),403);
        call("POST","/api/v1/learning/plans",parent,Map.of("studentId",f.student().accountId(),"subject","数学","title","不应创建","longTermGoal","不应写入"),403);
        for(var actor:List.of(outsider,otherTeacher)) {
            call("GET",u,actor,null,403); call("GET",u+"/history",actor,null,403);
            call("PUT",u,actor,update(p,"不应修改","ACTIVE"),403);
        }
        call("GET","/api/v1/learning/plans?childProfileId="+child,f.student(),null,403);
        call("GET","/api/v1/learning/plans?studentId="+f.student().accountId()+"&childProfileId="+child,parent,null,400);
        db.update("update guardian_bindings set status='REVOKED' where id=?",UUID.fromString(children.get(0).path("bindingId").asText()));
        call("GET",u,parent,null,403); call("GET",u+"/history",parent,null,403);
    }
    @Test void 幂等重试输入校验与分页边界() throws Exception {
        var f=fixture(); var key=UUID.randomUUID().toString(); var body=planBody("幂等目标");
        var p=call("POST","/api/v1/learning/plans",f.student(),body,201,key);
        assertThat(call("POST","/api/v1/learning/plans",f.student(),body,201,key).path("planId")).isEqualTo(p.path("planId"));
        call("POST","/api/v1/learning/plans",f.student(),planBody("另一目标"),409,key);
        call("POST","/api/v1/learning/plans",f.student(),Map.of("subject","数学","title"," ","longTermGoal","目标"),400);
        call("POST","/api/v1/learning/plans",f.student(),planBody("长".repeat(121)),400);
        call("PUT",url(p),f.student(),Map.of("subject","数学","title","标题","longTermGoal","目标","status","ACTIVE"),400);
        call("GET","/api/v1/learning/plans?page=-1",f.student(),null,400);
        call("GET","/api/v1/learning/plans?size=51",f.student(),null,400);
        assertThat(call("GET",url(p)+"/history",f.student(),null,200).path("totalElements").asLong()).isEqualTo(1);
    }
    @Test void 不同学生任务与其他计划目标不能串联() throws Exception {
        var f=fixture(); var other=fixture(); var p=create(f); var another=create(f); var otherTask=task(other);
        call("POST",url(p)+"/tasks",f.teacher(),Map.of("revision",0,"taskId",otherTask),403);
        another=call("POST",url(another)+"/milestones",f.student(),milestone(another,false),201);
        call("PUT",url(p)+"/milestones/"+another.path("milestones").get(0).path("milestoneId").asText(),f.student(),milestone(p,true),403);
        assertThat(call("GET",url(p),f.student(),null,200).path("revision").asLong()).isZero();
        assertThat(call("GET",url(p)+"/history",f.student(),null,200).path("totalElements").asLong()).isEqualTo(1);
    }
    @Test void 取消关联不删除任务归档需先恢复才可维护() throws Exception {
        var f=fixture(); var p=create(f); var u=url(p); var t=task(f);
        p=call("POST",u+"/tasks",f.teacher(),Map.of("revision",0,"taskId",t),201);
        call("POST",u+"/tasks",f.teacher(),Map.of("revision",1,"taskId",t),409);
        p=call("DELETE",u+"/tasks/"+t+"?revision=1",f.student(),null,200);
        assertThat(p.path("tasks").size()).isZero();
        assertThat(db.queryForObject("select count(*) from learning_tasks where id=?",Long.class,UUID.fromString(t))).isEqualTo(1);
        p=call("PUT",u,f.student(),update(p,"归档计划","ARCHIVED"),200);
        call("POST",u+"/milestones",f.teacher(),milestone(p,false),409);
        p=call("PUT",u,f.teacher(),update(p,"恢复计划","ACTIVE"),200);
        call("POST",u+"/milestones",f.teacher(),milestone(p,false),201);
    }
    @RepeatedTest(3) void 同一版本并发调整仅一胜不覆盖且历史守恒() throws Exception {
        var f=fixture(); var p=create(f); var u=url(p); var barrier=new CyclicBarrier(2); var pool=Executors.newFixedThreadPool(2);
        try {
            var futures=new ArrayList<Future<Integer>>();
            for(var actor:List.of(f.student(),f.teacher())) futures.add(pool.submit(()-> {
                barrier.await(10,TimeUnit.SECONDS);
                return mvc.perform(request(HttpMethod.PUT,u).header("Authorization","Bearer "+actor.accessToken())
                        .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(update(p,actor.phone(),"ACTIVE")))).andReturn().getResponse().getStatus();
            }));
            assertThat(List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(call("GET",u,f.student(),null,200).path("revision").asLong()).isEqualTo(1);
            assertThat(call("GET",u+"/history",f.student(),null,200).path("totalElements").asLong()).isEqualTo(2);
        } finally { pool.shutdownNow(); }
    }
    @Test void 超二十计划与调整记录可完整翻页且无重复() throws Exception {
        var f=fixture(); JsonNode p=null;
        for(int i=0;i<21;i++) p=call("POST","/api/v1/learning/plans",f.student(),planBody("目标"+i),201);
        var first=call("GET","/api/v1/learning/plans",f.student(),null,200);
        var second=call("GET","/api/v1/learning/plans?page=1",f.student(),null,200);
        assertThat(first.path("totalElements").asLong()).isEqualTo(21);
        var ids=new HashSet<String>(); for(var page:List.of(first,second)) for(var row:page.path("content")) ids.add(row.path("planId").asText());
        assertThat(ids).hasSize(21);
        var u=url(p);
        for(int i=0;i<21;i++) p=call("PUT",u,f.teacher(),update(p,"调整"+i,"ACTIVE"),200);
        var history=call("GET",u+"/history",f.student(),null,200);
        var next=call("GET",u+"/history?page=1",f.student(),null,200);
        assertThat(history.path("totalElements").asLong()).isEqualTo(22);
        assertThat(history.path("content").size()+next.path("content").size()).isEqualTo(22);
        assertThat(next.path("content").get(1).path("action").asText()).isEqualTo("CREATE");
    }
    @Test void 课程计数排除取消关闭课和其他学科() throws Exception {
        var f=fixture(); var p=create(f); var u=url(p);
        db.update("update bookings set status='CLOSED',cancel_reason='取消后关闭' where id=?",UUID.fromString(f.booking()));
        assertThat(call("GET",u,f.student(),null,200).path("lessonsCompleted").asLong()).isZero();
        db.update("update bookings set status='CLOSED',cancel_reason=null where id=?",UUID.fromString(f.booking()));
        assertThat(call("GET",u,f.student(),null,200).path("lessonsCompleted").asLong()).isEqualTo(1);
        db.update("update bookings set subject='物理' where id=?",UUID.fromString(f.booking()));
        assertThat(call("GET",u,f.student(),null,200).path("lessonsCompleted").asLong()).isZero();
    }

    @Test void 计划内发布并关联任务原子幂等且学习工作权限真实() throws Exception {
        var f=fixture(); var body=new HashMap<String,Object>(planBody("教师建立计划")); body.put("studentId",f.student().accountId());
        var p=call("POST","/api/v1/learning/plans",f.teacher(),body,201); var u=url(p);
        var lessons=call("GET",u+"/eligible-lessons",f.teacher(),null,200);
        assertThat(lessons.get(0).path("bookingId").asText()).isEqualTo(f.booking());
        call("GET",u+"/eligible-lessons",f.student(),null,403);
        var taskBody=Map.of("revision",0,"bookingId",f.booking(),"title","计划内函数练习","description","画图并解释斜率");
        var key=UUID.randomUUID().toString(); p=call("POST",u+"/tasks/new",f.teacher(),taskBody,201,key);
        var replay=call("POST",u+"/tasks/new",f.teacher(),taskBody,201,key);
        assertThat(replay.path("tasks").get(0).path("taskId")).isEqualTo(p.path("tasks").get(0).path("taskId"));
        assertThat(p.path("tasks").size()).isEqualTo(1); assertThat(p.path("tasks").get(0).path("canFeedback").asBoolean()).isTrue();
        p=call("GET",u,f.student(),null,200);
        assertThat(p.path("tasks").get(0).path("canSubmit").asBoolean()).isTrue();
        assertThat(p.path("tasks").get(0).path("canFeedback").asBoolean()).isFalse();
        var t=p.path("tasks").get(0).path("taskId").asText();
        call("POST","/api/v1/learning/tasks/"+t+":submit",f.student(),Map.of("content"," "),400);
        call("POST","/api/v1/learning/tasks/"+t+":submit",f.student(),Map.of("content","已画出函数图像"),200);
        call("POST","/api/v1/learning/tasks/"+t+":feedback",f.teacher(),Map.of("feedback","长".repeat(4001)),400);
        call("POST","/api/v1/learning/tasks/"+t+":feedback",f.teacher(),Map.of("feedback","斜率理解清楚"),200);
        p=call("GET",u,f.student(),null,200);
        assertThat(p.path("tasksCompleted").asLong()).isEqualTo(1);
        assertThat(p.path("tasks").get(0).path("submission").asText()).isEqualTo("已画出函数图像");
        assertThat(p.path("tasks").get(0).path("feedback").asText()).isEqualTo("斜率理解清楚");
        assertThat(call("GET",u+"/history",f.student(),null,200).path("totalElements").asLong()).isEqualTo(2);
    }
    @Test void 发布任务越权或非法课程失败不留任务关联及历史() throws Exception {
        var f=fixture(); var other=lesson(account("STUDENT"),f.teacher(),12); var p=create(f); var u=url(p);
        var bad=Map.of("revision",0,"bookingId",other.booking(),"title","错误课程","description","不得串学生");
        call("POST",u+"/tasks/new",f.teacher(),bad,403);
        call("POST",u+"/tasks/new",f.student(),bad,403);
        lesson(f.student(),f.teacher(),14); // Remaining confirmed course preserves legitimate teacher access.
        db.update("update bookings set status='CANCELLED',cancel_reason='取消' where id=?",UUID.fromString(f.booking()));
        call("POST",u+"/tasks/new",f.teacher(),Map.of("revision",0,"bookingId",f.booking(),"title","已取消课程","description","不应发布"),409);
        assertThat(db.queryForObject("select count(*) from learning_tasks where booking_id in (?,?)",Long.class,UUID.fromString(f.booking()),UUID.fromString(other.booking()))).isZero();
        assertThat(call("GET",u,f.student(),null,200).path("revision").asLong()).isZero();
        assertThat(call("GET",u+"/history",f.student(),null,200).path("totalElements").asLong()).isEqualTo(1);
        call("DELETE",u+"/tasks/"+UUID.randomUUID()+"?revision=-1",f.student(),null,400);
    }
    @Test void 未确认或已取消预约不授予教师查看计划的权限() throws Exception {
        var f=fixture(); var p=create(f); var u=url(p);
        assertThat(call("GET","/api/v1/learning/plans/students",f.teacher(),null,200).size()).isEqualTo(1);
        db.update("update bookings set status='REQUESTED' where id=?",UUID.fromString(f.booking()));
        call("GET",u,f.teacher(),null,403);
        assertThat(call("GET","/api/v1/learning/plans/students",f.teacher(),null,200).size()).isZero();
        db.update("update bookings set status='CANCELLED',cancel_reason='未开课取消' where id=?",UUID.fromString(f.booking()));
        call("GET",u,f.teacher(),null,403); call("GET",u,f.student(),null,200);
    }
}
