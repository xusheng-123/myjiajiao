package com.nurture.it;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class LearningRecordPrivacyIT {
 @Autowired MockMvc mvc;@Autowired ObjectMapper om;
 Support.Account account(String role)throws Exception{return Support.register(mvc,om,role.toLowerCase(),role,"17"+String.format("%09d",java.util.concurrent.ThreadLocalRandom.current().nextInt(1000000000)),"Delivery#2026");}
 String course(Support.Account student,Support.Account teacher,int day)throws Exception{
  var at=OffsetDateTime.now().plusDays(day);var slot=Support.postJson(mvc,om,"/api/v1/scheduling/availability-slots",teacher.accessToken(),Map.of("startAt",at.toString(),"endAt",at.plusHours(1).toString()));
  var b=Support.postJson(mvc,om,"/api/v1/booking/bookings",student.accessToken(),Map.of("teacherId",teacher.accountId(),"slotId",slot.path("slotId").asText(),"subject","课堂保密验证","bookingType","FORMAL"));String id=b.path("bookingId").asText();assertThat(id).isNotBlank();
  Support.postJson(mvc,om,"/api/v1/booking/booking-requests/"+id+":decide?decision=approve",teacher.accessToken(),Map.of());return id;
 }
 JsonNode read(Support.Account a,String path)throws Exception{var r=mvc.perform(get(path).header("Authorization","Bearer "+a.accessToken())).andReturn().getResponse();assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);return om.readTree(r.getContentAsString());}
 @Test void 内部备注仅记录作者可见学生与其他授课教师仅见公开反馈()throws Exception{
  var student=account("STUDENT");var author=account("TEACHER");var peer=account("TEACHER");String id=course(student,author,6);course(student,peer,7);
  Support.postJson(mvc,om,"/api/v1/booking/bookings/"+id+":start",author.accessToken(),Map.of());Support.postJson(mvc,om,"/api/v1/booking/bookings/"+id+":complete",author.accessToken(),Map.of());
  String secret="PRIVATE-TEACHER-NOTE-"+UUID.randomUUID();var body=new HashMap<String,Object>();body.put("bookingId",id);body.put("subject","课堂保密验证");body.put("knowledgePoints","公开知识点");body.put("performance","公开学习反馈");body.put("problems","公开问题");body.put("nextSuggestion","公开复习建议");body.put("note",secret);
  var created=Support.postJson(mvc,om,"/api/v1/learning/teacher/lesson-records",author.accessToken(),body);assertThat(created.path("recordId").asText()).isNotBlank();
  assertThat(read(author,"/api/v1/learning/teacher/lesson-records").toString()).contains(secret);
  for(var visible:List.of(read(student,"/api/v1/learning/archive"),read(peer,"/api/v1/learning/teacher/students/"+student.accountId()))){
   assertThat(visible.path("lessonRecords").size()).isEqualTo(1);assertThat(visible.toString()).contains("公开知识点","公开学习反馈","公开复习建议").doesNotContain(secret);assertThat(visible.path("lessonRecords").get(0).has("note")).isFalse();
  }
 }
}
