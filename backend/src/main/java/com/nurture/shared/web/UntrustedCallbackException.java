package com.nurture.shared.web;

/**
 * 外部回调凭据不可信异常 — 签名/金额/时效校验失败，映射为 401 并拒绝处理。
 */
public class UntrustedCallbackException extends RuntimeException {

    public UntrustedCallbackException(String message) {
        super(message);
    }
}
