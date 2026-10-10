package com.nurture.identity.app;
import com.nurture.identity.infra.SmsProperties;
import com.nurture.identity.infra.UnavailableSmsDelivery;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SmsDeliveryFailureTest {
    private SmsCodeStore store(SmsDelivery delivery) {
        var props=new SmsProperties(new SmsProperties.RateLimit(0,100,1000),5);
        return new SmsCodeStore(new SmsRateLimiter(props),props,delivery);
    }
    @Test void unavailableChannelDoesNotCreateUsableCode() {
        var s=store(new UnavailableSmsDelivery());
        assertThatThrownBy(()->s.send("STUDENT","13912345678")).isInstanceOf(com.nurture.shared.web.ServiceUnavailableException.class);
        assertThat(s.verify("STUDENT","13912345678","000000")).isFalse();
    }
    @Test void failedDeliveryDoesNotCacheCode() {
        var capture=new String[1];
        var s=store(new SmsDelivery() {
            public boolean available(){return true;}
            public void send(String phone,String code){capture[0]=code;throw new com.nurture.shared.web.ServiceUnavailableException("投递失败");}
        });
        assertThatThrownBy(()->s.send("STUDENT","13912345678")).isInstanceOf(com.nurture.shared.web.ServiceUnavailableException.class);
        assertThat(s.verify("STUDENT","13912345678",capture[0])).isFalse();
    }
    @Test void acceptedCodeIsSingleUseAndRoleScoped() {
        var capture=new String[1];
        var s=store(new SmsDelivery() { public boolean available(){return true;} public void send(String phone,String code){capture[0]=code;} });
        s.send("STUDENT","13912345678");
        assertThat(s.verify("PARENT","13912345678",capture[0])).isFalse();
        assertThat(s.verify("STUDENT","13912345678",capture[0])).isTrue();
        assertThat(s.verify("STUDENT","13912345678",capture[0])).isFalse();
    }
}
