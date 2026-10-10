package com.nurture.identity.app;

/** Supplier adapter port. Implementations must not log OTPs or claim delivery without acceptance. */
public interface SmsDelivery {
    boolean available();
    void send(String phone, String code);
}
