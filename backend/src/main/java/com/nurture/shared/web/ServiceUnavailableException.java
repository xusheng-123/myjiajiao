package com.nurture.shared.web;

public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) { super(message); }
}
