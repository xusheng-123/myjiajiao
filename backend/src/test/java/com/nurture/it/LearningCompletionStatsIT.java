package com.nurture.it;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class LearningCompletionStatsIT{
 @Autowired MockMvc mvc;@Autowired ObjectMapper om;@Autowired JdbcTemplate db;
 @Test void 真实已完成状态不是枚举与字符串错误比较造成的零()throws Exception{
  var s=Support.register(mvc,om,"student","STUDENT","18"+String.format("%09d",new java.security.SecureRandom().nextInt(1000000000)),"Completion#2026");var t=Support.register(mvc,om,"teacher","TEACHER","18"+String.format("%09d",new java.security.SecureRandom().nextInt(1000000000)),"Completion#2026");var at=java.time.OffsetDateTime.now().plusDays(2);
  var slot=Support.postJson(mvc,om,"/api/v1/scheduling/availability-slots",t.accessToken(),Map.of("startAt",at.toString(),"endAt",at.plusMinutes(30).toString()));var b=Support.postJson(mvc,om,"/api/v1/booking/bookings",s.accessToken(),Map.of("teacherId",t.accountId(),"slotId",slot.path("slotId").asText(),"subject","数学","bookingType","FORMAL"));
  assertThat(db.update("update bookings set status='COMPLETED' where id=?",UUID.fromString(b.path("bookingId").asText()))).isEqualTo(1);
  var result=Support.getJson(mvc,om,"/api/v1/learning/archive",s.accessToken());assertThat(result.path("stats").path("completedLessons").asLong()).isEqualTo(1L);assertThat(result.path("history").get(0).path("status").asText()).isEqualTo("COMPLETED");
 }
}
