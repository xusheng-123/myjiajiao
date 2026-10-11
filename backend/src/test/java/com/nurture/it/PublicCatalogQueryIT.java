package com.nurture.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.matching.app.MatchingApplicationService;
import com.nurture.matching.app.MatchingApplicationService.TeacherCandidate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class PublicCatalogQueryIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper om; @Autowired JdbcTemplate db;
    @Autowired MatchingApplicationService matching;
    @SpyBean NamedParameterJdbcTemplate projectionJdbc;
    String subject; List<Support.Account> teachers;

    @BeforeEach void setup() throws Exception {
        subject=UUID.randomUUID().toString().replace("-",""); teachers=new ArrayList<>();
        for(int i=0;i<3;i++) {
            var teacher=Support.register(mvc,om,"teacher","TEACHER","17"+String.format("%09d",new java.security.SecureRandom().nextInt(1000000000)),"Catalog#2026");
            teachers.add(teacher);
            Support.postJson(mvc,om,"/api/v1/teacher/me/subjects",teacher.accessToken(),Map.of("subject",subject,"gradeBand","初中","pricePerHourCents",15000+i*7000));
            Support.postJson(mvc,om,"/api/v1/teacher/me/subjects",teacher.accessToken(),Map.of("subject",subject,"gradeBand","高中","pricePerHourCents",21000+i*3000));
            db.update("update teacher_profiles set school=?,highest_degree='硕士',years_of_teaching=3 where account_id=?",i==0?"包含%_字符的学校":"学校"+i,teacher.accountId());
            var profile=db.queryForObject("select id from teacher_profiles where account_id=?",UUID.class,teacher.accountId());
            db.update("insert into teacher_style_tags(teacher_id,style_tag) values (?,?)",profile,i==0?"耐心细致":"思维推导");
            db.update("insert into verification_cases(id,created_at,updated_at,teacher_id,submitted_at,status) values (?,now(),now(),?,now(),'APPROVED')",UUID.randomUUID(),teacher.accountId());
            // Both past and future slots: only the future OPEN time is public evidence.
            for(int day:new int[]{-1,2+i})db.update("insert into availability_slots(id,created_at,updated_at,teacher_id,start_at,end_at,status,version) values (?,now(),now(),?,?,?,'OPEN',0)",UUID.randomUUID(),teacher.accountId(),OffsetDateTime.now().plusDays(day),OffsetDateTime.now().plusDays(day).plusHours(1));
        }
    }
    List<TeacherCandidate> oracle(String grade,Long max,String style,String keyword) {return matching.search(subject,grade,max,style,keyword,false);}
    @Test void 条目数与组合筛选兼容旧小数据检索() {
        for(var filter:List.of(new Object[]{null,null,null,null},new Object[]{"高中",26000L,"思维推导","学校"},new Object[]{"初中",15000L,"耐心细致","%_"})) {
            var actual=matching.publicPage(subject,(String)filter[0],(Long)filter[1],(String)filter[2],(String)filter[3],"price-asc",0,50);
            assertThat(actual.getTotalElements()).isEqualTo(oracle((String)filter[0],(Long)filter[1],(String)filter[2],(String)filter[3]).size());
            assertThat(actual.getContent()).containsExactlyInAnyOrderElementsOf(oracle((String)filter[0],(Long)filter[1],(String)filter[2],(String)filter[3]));
        }
    }
    @Test void 五种排序及并列项分页稳定无重复() {
        for(String sort:List.of("price-asc","price-desc","rating-desc","reviews-desc","recommended")) {
            var all=new ArrayList<>(oracle(null,null,null,null));
            Comparator<TeacherCandidate> comparator=switch(sort){
                case "price-asc"->Comparator.comparingLong(TeacherCandidate::pricePerHourCents);
                case "price-desc"->Comparator.comparingLong(TeacherCandidate::pricePerHourCents).reversed();
                case "rating-desc"->Comparator.comparingDouble((TeacherCandidate c)->c.averageRating()==null?0:c.averageRating()).reversed();
                case "reviews-desc"->Comparator.comparingLong(TeacherCandidate::reviewCount).reversed();
                default->Comparator.<TeacherCandidate>comparingInt(c->2+(c.reviewCount()>0?1:0)+(c.nextOpenSlotAt()!=null?1:0)).reversed().thenComparing(Comparator.comparingInt((TeacherCandidate c)->c.reasons().size()).reversed());
            };
            all.sort(comparator.thenComparing(c->c.teacherId().toString()).thenComparing(TeacherCandidate::subject).thenComparing(TeacherCandidate::gradeBand));
            List<TeacherCandidate> paged=new ArrayList<>();
            for(int page=0;page<3;page++)paged.addAll(matching.publicPage(subject,null,null,null,null,sort,page,2).getContent());
            assertThat(paged).containsExactlyElementsOf(all);
        }
    }
    @Test void 最新审核及锁定下架保持公开权限() {
        var teacher=teachers.get(0);
        db.update("insert into verification_cases(id,created_at,updated_at,teacher_id,submitted_at,status) values (?,now(),now(),?,now()+interval '1 day','REJECTED')",UUID.randomUUID(),teacher.accountId());
        db.update("update accounts set status='LOCKED' where id=?",teachers.get(1).accountId());
        db.update("update subject_offerings set active=false where teacher_id=(select id from teacher_profiles where account_id=?)",teachers.get(2).accountId());
        assertThat(matching.publicPage(subject,null,null,null,null,"recommended",0,50).getTotalElements()).isZero();
    }
    @Test void 页面超出范围返回真实总数且不暴露敏感字段() throws Exception {
        var response=mvc.perform(get("/api/v1/matching/public/teachers-page").param("subject",subject).param("page","100")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var page=om.readTree(response);assertThat(page.path("totalElements").asInt()).isEqualTo(6);assertThat(page.path("content")).isEmpty();
        var first=om.readTree(mvc.perform(get("/api/v1/matching/public/teachers-page").param("subject",subject)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(first.toString()).doesNotContain("phone","storageKey","decisionNote","contact");
    }
    @Test void 数据库访问固定为总数和当页两条查询() throws Exception {
        for(String size:List.of("1","50")) {
            clearInvocations(projectionJdbc);
            mvc.perform(get("/api/v1/matching/public/teachers-page").param("subject",subject).param("size",size)).andExpect(status().isOk());
            verify(projectionJdbc,times(1)).queryForObject(anyString(),any(SqlParameterSource.class),eq(Long.class));
            verify(projectionJdbc,times(1)).query(anyString(),any(SqlParameterSource.class),any(RowMapper.class));
        }
    }
    @Test void 关键词特殊字符为字面值且不拼接输入SQL() {
        assertThat(matching.publicPage(subject,null,null,null,"%_","recommended",0,50).getTotalElements()).isEqualTo(2);
        assertThat(matching.publicPage(subject,null,null,null,"' OR 1=1 --","recommended",0,50).getTotalElements()).isZero();
    }
    @Test void 真实评价聚合评分舍入与未来档期排序一致() throws Exception {
        var student=Support.register(mvc,om,"student","STUDENT","17"+String.format("%09d",new java.security.SecureRandom().nextInt(1000000000)),"Catalog#2026");
        int offset=10;
        for(int teacherIndex=0;teacherIndex<2;teacherIndex++) {
            var teacher=teachers.get(teacherIndex);
            for(int rating:teacherIndex==0?new int[]{3,4,5,5}:new int[]{5}) {
                var start=OffsetDateTime.now().plusDays(offset++);
                var slot=Support.postJson(mvc,om,"/api/v1/scheduling/availability-slots",teacher.accessToken(),Map.of("startAt",start.toString(),"endAt",start.plusHours(1).toString()));
                var lesson=Support.postJson(mvc,om,"/api/v1/booking/bookings",student.accessToken(),Map.of("teacherId",teacher.accountId(),"slotId",slot.path("slotId").asText(),"subject",subject,"bookingType","FORMAL"));
                var booking=UUID.fromString(lesson.path("bookingId").asText());
                db.update("update bookings set status='COMPLETED' where id=?",booking);
                var review=Support.postJson(mvc,om,"/api/v1/review/reviews",student.accessToken(),Map.of("bookingId",booking,"rating",rating,"tags",List.of("思路清楚"),"comment","查询聚合验收"));
                assertThat(review.path("reviewId").asText()).isNotBlank();
            }
        }
        db.update("update availability_slots set status='BLOCKED' where teacher_id=?",teachers.get(2).accountId());
        var ratings=matching.publicPage(subject,null,null,null,null,"rating-desc",0,50).getContent();
        assertThat(ratings.get(0).teacherId()).isEqualTo(teachers.get(1).accountId());
        assertThat(ratings.stream().filter(c->c.teacherId().equals(teachers.get(0).accountId())).findFirst().orElseThrow().averageRating()).isEqualTo(4.3);
        var counts=matching.publicPage(subject,null,null,null,null,"reviews-desc",0,50).getContent();
        assertThat(counts.get(0).teacherId()).isEqualTo(teachers.get(0).accountId());
        assertThat(counts.get(0).reviewCount()).isEqualTo(4);
        assertThat(counts.get(counts.size()-1).nextOpenSlotAt()).isNull();
        assertThat(counts).containsExactlyInAnyOrderElementsOf(oracle(null,null,null,null));
    }
}
