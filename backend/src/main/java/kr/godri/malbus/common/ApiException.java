package kr.godri.malbus.common;

import org.springframework.http.HttpStatus;

// 에러 코드는 docs/api.md 에러 표 기준
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Object details;

    public ApiException(HttpStatus status, String code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException invalid(String message) {
        return badRequest("VALIDATION_FAILED", message);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Object getDetails() { return details; }
}
