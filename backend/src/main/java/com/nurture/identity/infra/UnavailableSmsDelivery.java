package com.nurture.identity.infra;

import com.nurture.identity.app.SmsDelivery;
import com.nurture.shared.web.ServiceUnavailableException;
import org.springframework.stereotype.Component;

/** No supplier is configured. Fail honestly; there is no development bypass to OTP authentication. */
@Component
public class UnavailableSmsDelivery implements SmsDelivery {
    public boolean available() { return false; }
    public void send(String phone, String code) { throw new ServiceUnavailableException("短信服务尚未开通，请使用账号密码登录"); }
}
