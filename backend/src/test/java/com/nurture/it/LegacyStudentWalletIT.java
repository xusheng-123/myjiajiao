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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class LegacyStudentWalletIT{
 @Autowired MockMvc mvc;@Autowired ObjectMapper om;@Autowired JdbcTemplate db;
 Support.Account account(String role)throws Exception{return Support.register(mvc,om,role.toLowerCase(),role,"18"+String.format("%09d",new java.security.SecureRandom().nextInt(1000000000)),"LegacyWallet#2026");}
 @Test void 无旧钱包只读不创建且家长不能进入学生接口()throws Exception{
  var s=account("STUDENT");var p=account("PARENT");long before=db.queryForObject("select count(*) from family_wallets",Long.class);var w=Support.getJson(mvc,om,"/api/v1/student/me/legacy-wallet",s.accessToken());assertThat(w.path("present").asBoolean()).isFalse();assertThat(w.path("balanceCents").isNull()).isTrue();assertThat(db.queryForObject("select count(*) from family_wallets",Long.class)).isEqualTo(before);
  assertThat(mvc.perform(get("/api/v1/student/me/legacy-wallet").header("Authorization","Bearer "+p.accessToken())).andReturn().getResponse().getStatus()).isEqualTo(403);
 }
 @Test void 旧学生余额与流水分页仅归属人可读且资金不变化()throws Exception{
  var s=account("STUDENT");var other=account("STUDENT");UUID wallet=UUID.randomUUID();UUID profile=UUID.randomUUID();
  db.update("insert into parent_profiles(id,created_at,updated_at,account_id,parent_no) values (?,now(),now(),?,?)",profile,s.accountId(),"LEG-"+profile.toString().substring(0,16));
  db.update("insert into family_wallets(id,created_at,updated_at,parent_id,balance_cents,currency,version) values (?,now(),now(),?,77700,'CNY',0)",wallet,profile);
  for(int i=0;i<21;i++)db.update("insert into wallet_transactions(id,created_at,updated_at,wallet_id,txn_type,amount_cents,balance_after_cents,memo) values (?,now(),now(),?,'TOPUP',100,77700,'历史夹具')",UUID.randomUUID(),wallet);
  var w=Support.getJson(mvc,om,"/api/v1/student/me/legacy-wallet",s.accessToken());assertThat(w.path("present").asBoolean()).isTrue();assertThat(w.path("balanceCents").asLong()).isEqualTo(77700);assertThat(w.path("transactions").path("content").size()).isEqualTo(20);
  var next=Support.getJson(mvc,om,"/api/v1/student/me/legacy-wallet?page=1",s.accessToken());assertThat(next.path("transactions").path("content").size()).isEqualTo(1);assertThat(w.findValuesAsText("txnId")).doesNotContainAnyElementsOf(next.findValuesAsText("txnId"));
  assertThat(Support.getJson(mvc,om,"/api/v1/student/me/legacy-wallet",other.accessToken()).path("present").asBoolean()).isFalse();assertThat(db.queryForObject("select balance_cents from family_wallets where id=?",Long.class,wallet)).isEqualTo(77700);
 }
}
