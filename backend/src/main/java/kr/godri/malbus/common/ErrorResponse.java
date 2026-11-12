package kr.godri.malbus.common;

// { "error": { "code", "message", "details" } }
public record ErrorResponse(Body error) {

    public record Body(String code, String message, Object details) {
    }

    public static ErrorResponse of(String code, String message, Object details) {
        return new ErrorResponse(new Body(code, message, details));
    }
}
