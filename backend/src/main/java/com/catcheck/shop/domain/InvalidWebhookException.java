package com.catcheck.shop.domain;

public class InvalidWebhookException extends RuntimeException {
    public InvalidWebhookException(String message) { super(message); }
}
