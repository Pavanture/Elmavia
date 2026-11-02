package com.shopsphere.exception;

public class QikinkApiException extends RuntimeException {

    public QikinkApiException(String message) {
        super(message);
    }

    public QikinkApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
