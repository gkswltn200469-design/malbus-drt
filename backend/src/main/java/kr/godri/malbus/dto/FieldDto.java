package kr.godri.malbus.dto;

// api.md Field<T>
public record FieldDto<T>(
        T value,
        String said,
        boolean needsCheck,
        String reason,
        String askHint,
        Evidence evidence,
        T previous,
        String source,
        String decidedAt,
        String note) {

    public record Evidence(int lineNo, String text) {
    }
}
