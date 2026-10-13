package com.nurture.it;

import com.fasterxml.jackson.databind.*;
import com.nurture.payment.domain.PaymentAttempt;
import com.nurture.payment.domain.PaymentGateway;
import com.nurture.payment.infra.PaymentAttemptRepository;
import com.nurture.payment.infra.SimulatedPaymentGateway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import java.util.*;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
@TestPropertySource(properties="nurture.payment.sandbox.enabled=false")
class SandboxGatewayDisabledIT {
 @Autowired MockMvc mvc;@Autowired ObjectMapper om;@Autowired JdbcTemplate db;
 @Autowired List<PaymentGateway> gateways;@Autowired PaymentAttemptRepository attempts;
 Support.Account account(String role)throws Exception{return Support.register(mvc,om,role.toLowerCase(),role,"17"+String.format("%09d",java.util.concurrent.ThreadLocalRandom.current().nextInt(1000000000)),"Delivery#2026");}
 JsonNode call(String method,String path,String token,Object body,int status)throws Exception{
  var request=request(HttpMethod.valueOf(method),path).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON);
  if(token!=null)request.header("Authorization","Bearer "+token);if(body!=null)request.content(om.writeValueAsBytes(body));
  var r=mvc.perform(request).andReturn().getResponse();assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);return r.getContentAsString().isBlank()?om.nullNode():om.readTree(r.getContentAsString());
 }
 @Test void 沙箱关闭禁止三个模拟渠道和历史有效回调但线下收款仍可用()throws Exception{
  var s=account("STUDENT");var t=account("TEACHER");var p=account("PARENT");
  var at=OffsetDateTime.now().plusDays(5);var slot=call("POST","/api/v1/scheduling/availability-slots",t.accessToken(),Map.of("startAt",at.toString(),"endAt",at.plusHours(1).toString()),201);
  var b=call("POST","/api/v1/booking/bookings",s.accessToken(),Map.of("teacherId",t.accountId(),"slotId",slot.path("slotId").asText(),"subject","关闭沙箱验证","bookingType","FORMAL"),201);
  call("POST","/api/v1/booking/booking-requests/"+b.path("bookingId").asText()+":decide?decision=approve",t.accessToken(),Map.of(),200);
  var profile=call("GET","/api/v1/student/me/profile",s.accessToken(),null,200);
  call("POST","/api/v1/parent/me/children",p.accessToken(),Map.of("studentNo",profile.path("studentNo").asText(),"relation","母亲"),201);
  call("POST","/api/v1/parent/me/children:confirm",s.accessToken(),Map.of(),200);
  var order=call("POST","/api/v1/order/orders",p.accessToken(),Map.of("bookingId",b.path("bookingId").asText()),201);var oid=UUID.fromString(order.path("orderId").asText());
  assertThat(call("GET","/api/v1/payment/config",p.accessToken(),null,200).path("sandboxEnabled").asBoolean()).isFalse();
  for(var channel:List.of("MOCK","WECHAT","ALIPAY"))call("POST","/api/v1/payment/payments:initiate",p.accessToken(),Map.of("orderId",oid,"channel",channel),409);
  assertThat(db.queryForObject("select count(*) from payment_attempts where order_id=?",Long.class,oid)).isZero();
  // Reconstruct an old pending sandbox attempt; even a valid HMAC must not settle it now.
  var legacy=new PaymentAttempt(oid,PaymentAttempt.PaymentChannel.MOCK,order.path("amountCents").asLong());legacy.attachChannelRef("LEGACY-"+UUID.randomUUID());attempts.saveAndFlush(legacy);
  var gateway=(SimulatedPaymentGateway)gateways.stream().filter(g->g.channelName().equals("MOCK")).findFirst().orElseThrow();
  for(boolean success:List.of(true,false)){
   var signed=gateway.buildNotification(oid,legacy.getChannelRef(),order.path("amountCents").asLong(),success?"SUCCESS":"FAILED");
   call("POST","/api/v1/payment/callbacks/gateway",null,Map.of("orderId",oid,"channel","MOCK","payload",signed.payload(),"signature",signed.signature(),"success",success),409);
  }
  assertThat(call("GET","/api/v1/order/orders/"+oid,p.accessToken(),null,200).path("status").asText()).isEqualTo("PENDING_PAYMENT");
  assertThat(db.queryForObject("select count(*) from settlement_entries where order_id=?",Long.class,oid)).isZero();
  var login=call("POST","/api/v1/identity/auth/login:password",null,Map.of("role","ADMIN","account","13800000000","password","Admin#2026"),200);
  long balance=call("GET","/api/v1/parent/me/wallet",p.accessToken(),null,200).path("balanceCents").asLong();
  call("POST","/api/v1/admin/orders/"+oid+":confirm-offline-payment",login.path("accessToken").asText(),Map.of(),200);
  assertThat(call("GET","/api/v1/order/orders/"+oid,p.accessToken(),null,200).path("status").asText()).isEqualTo("PAID");
  assertThat(call("GET","/api/v1/parent/me/wallet",p.accessToken(),null,200).path("balanceCents").asLong()).isEqualTo(balance);
  assertThat(db.queryForObject("select count(*) from payment_attempts where order_id=? and channel='OFFLINE' and status='SUCCEEDED'",Long.class,oid)).isEqualTo(1);
 }
}
