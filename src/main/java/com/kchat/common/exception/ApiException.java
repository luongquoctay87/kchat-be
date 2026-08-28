package com.kchat.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static ApiException of(HttpStatus status, String code, String message) {
        return new ApiException(code, message, status);
    }

    public static ApiException badRequest(String code, String message) {
        return of(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException unauthorized(String message) {
        return of(HttpStatus.UNAUTHORIZED, "unauthorized", message);
    }

    public static ApiException forbidden(String message) {
        return of(HttpStatus.FORBIDDEN, "forbidden", message);
    }

    public static ApiException notFound(String message) {
        return of(HttpStatus.NOT_FOUND, "not_found", message);
    }

    public static ApiException conflict(String message) {
        return of(HttpStatus.CONFLICT, "conflict", message);
    }

    public static ApiException tooManyRequests(String message) {
        return of(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", message);
    }

    public static ApiException serviceUnavailable(String code, String message) {
        return of(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
