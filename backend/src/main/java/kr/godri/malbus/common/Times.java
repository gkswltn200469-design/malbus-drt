package kr.godri.malbus.common;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

// 시각은 ISO 8601 +09:00 문자열로 저장
public final class Times {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private Times() {
    }

    public static String now() {
        return format(OffsetDateTime.now(SEOUL));
    }

    public static String format(OffsetDateTime t) {
        return t.atZoneSameInstant(SEOUL).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    // 요청으로 받은 시각. 형식 틀리면 400
    public static OffsetDateTime parse(String s) {
        try {
            return OffsetDateTime.parse(s);
        } catch (DateTimeParseException | NullPointerException e) {
            throw ApiException.invalid("시각 형식은 2026-11-17T14:00:00+09:00");
        }
    }

    public static LocalDate dateOf(String iso) {
        return OffsetDateTime.parse(iso).atZoneSameInstant(SEOUL).toLocalDate();
    }
}
