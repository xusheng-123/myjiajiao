package com.nurture.it;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class IdempotencyContractIT{
 @Autowired MockMvc mvc;@Autowired ObjectMapper om;
 @Test void 强制完整UUIDv4及RFC变体且保留大写规范值()throws Exception{
  var s=Support.register(mvc,om,"student","STUDENT","18"+String.format("%09d",new java.security.SecureRandom().nextInt(1000000000)),"Contract#2026");
  for(String key:List.of("1-1-4-8-1","00000000-0000-1000-8000-000000000000","00000000-0000-4000-0000-000000000000","00000000-0000-4000-c000-000000000000","not-uuid")){
   var r=mvc.perform(put("/api/v1/learning/profile").header("Authorization","Bearer "+s.accessToken()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\"测试\"}")).andReturn().getResponse();assertThat(r.getStatus()).as(key+" "+r.getContentAsString()).isEqualTo(400);
  }
  var key=UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
  for(int i=0;i<2;i++)assertThat(mvc.perform(put("/api/v1/learning/profile").header("Authorization","Bearer "+s.accessToken()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\"保留规范UUIDv4\"}")).andReturn().getResponse().getStatus()).isEqualTo(200);
 }
}
