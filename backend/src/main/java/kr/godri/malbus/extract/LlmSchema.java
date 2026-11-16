package kr.godri.malbus.extract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// AI 모듈 출력(docs/api.md)의 JSON Schema. Claude·Gemini 둘 다 이 스키마로 응답 형식을 고정
// 모든 칸 required + null 허용이라 "말 안 함"은 null로 옴. 인원은 이번 범위 밖이라 뺌
// 순서가 바뀌면 프롬프트 캐시가 깨져서 Map.of 대신 순서 고정(ordered)
public final class LlmSchema {

    private LlmSchema() {
    }

    public static Map<String, Object> aiOutput() {
        Map<String, Object> place = object(
                "said", nullable("string"),
                "evidenceLineNo", nullable("integer"),
                "rankedStopIds", ordered("type", "array", "items", ordered("type", "string")),
                "askHint", nullable("string"));

        return object(
                "purpose", object(
                        "value", ordered("type", "string", "enum", List.of("RESERVE", "CHANGE", "CANCEL", "INQUIRY")),
                        "evidenceLineNo", nullable("integer")),
                "from", place,
                "to", place,
                "date", object("said", nullable("string"), "evidenceLineNo", nullable("integer")),
                "time", object(
                        "said", nullable("string"),
                        "hour", nullable("integer"),
                        "minute", nullable("integer"),
                        "meridiem", ordered("type", List.of("string", "null"), "enum", List.of("AM", "PM")),
                        "evidenceLineNo", nullable("integer")));
    }

    private static Map<String, Object> object(Object... kv) {
        Map<String, Object> props = ordered(kv);
        return ordered("type", "object", "properties", props,
                "required", new ArrayList<>(props.keySet()), "additionalProperties", false);
    }

    private static Map<String, Object> nullable(String type) {
        return ordered("type", List.of(type, "null"));
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
