package kr.godri.malbus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 대본 20개를 /extract(규칙 모드)에 넣고 정답지와 비교 -> test/채점결과_RULE.md
// ./gradlew grade 로만 돌림. 배운 별칭이 섞이지 않게 db 따로 씀
@Tag("grading")
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:build/grading.db?foreign_keys=on")
@AutoConfigureMockMvc
class ScriptGradingTest {

    private static final Path ANSWERS = Path.of("../test/대본_정답지.csv");
    private static final Path RESULT = Path.of("../test/채점결과_RULE.md");
    private static final List<String> COLS = List.of("목적", "출발", "도착", "날짜", "시간", "인원", "확인필요");
    private static final Map<String, String> PURPOSE = Map.of("예약", "RESERVE", "변경", "CHANGE", "취소", "CANCEL", "문의", "INQUIRY");
    private static final List<String> KEYS = List.of("from", "to", "date", "time", "people");
    private static final List<String> KO = List.of("출발", "도착", "날짜", "시간", "인원");

    private static final Comparator<String> BY_FIELD = Comparator.comparingInt(KO::indexOf);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    void 대본_채점() throws Exception {
        List<String> lines = Files.readAllLines(ANSWERS, StandardCharsets.UTF_8);
        List<String> header = parse(lines.get(0).replace("﻿", ""));

        Map<String, Integer> right = new LinkedHashMap<>();
        COLS.forEach(c -> right.put(c, 0));
        List<String> rows = new ArrayList<>();
        int total = 0, peopleBlank = 0, onlyPeopleCheck = 0, notReserveCheck = 0;

        for (String raw : lines.subList(1, lines.size())) {
            if (raw.isBlank()) continue;
            List<String> cells = parse(raw);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) row.put(header.get(i), i < cells.size() ? cells.get(i).trim() : "");
            JsonNode out = extract(row);
            assertThat(out.get("mode").asText()).isEqualTo("RULE");
            total++;

            Map<String, String> marks = new LinkedHashMap<>();
            marks.put("목적", mark(out.at("/purpose/value").asText(), PURPOSE.get(row.get("목적")), row.get("목적")));
            marks.put("출발", stopMark(out.get("from"), row.get("출발")));
            marks.put("도착", stopMark(out.get("to"), row.get("도착")));
            marks.put("날짜", mark(text(out.at("/date/value")), row.get("날짜"), row.get("날짜")));
            marks.put("시간", mark(text(out.at("/time/value")), row.get("시간"), row.get("시간")));
            marks.put("인원", mark(text(out.at("/people/value")), row.get("인원"), row.get("인원")));

            Set<String> expected = new TreeSet<>(BY_FIELD);
            if (!row.get("확인필요").isEmpty()) expected.addAll(Arrays.asList(row.get("확인필요").split("\\|")));
            Set<String> got = new TreeSet<>(BY_FIELD);
            for (int i = 0; i < KEYS.size(); i++) {
                if (out.at("/" + KEYS.get(i) + "/needsCheck").asBoolean()) got.add(KO.get(i));
            }
            marks.put("확인필요", expected.equals(got) ? "O"
                    : "X: " + join(got) + " (정답 " + join(expected) + ")");

            if (out.at("/people/value").isNull() && !row.get("인원").isEmpty()) peopleBlank++;
            Set<String> withoutPeople = new TreeSet<>(got);
            withoutPeople.remove("인원");
            if (!expected.equals(got) && expected.equals(withoutPeople)) onlyPeopleCheck++;
            if (!expected.equals(got) && !row.get("목적").equals("예약")) notReserveCheck++;

            marks.forEach((c, m) -> {
                if (m.equals("O")) right.merge(c, 1, Integer::sum);
            });
            rows.add("| " + row.get("번호") + " | " + row.get("유형") + " | " + String.join(" | ", marks.values()) + " |");
        }
        assertThat(total).isEqualTo(20);
        int n = total;

        StringBuilder md = new StringBuilder();
        md.append("# 채점 결과 (RULE 모드)\n\n");
        md.append("- 대상: `test/대본_정답지.csv` 대본 ").append(total).append("개를 `POST /extract`에 넣은 결과 (규칙 모드, 배운 별칭 없음)\n");
        md.append("- 다시 만들기: `cd backend && ./gradlew grade` (이 파일을 덮어씀)\n");
        md.append("- 판정 기준\n");
        md.append("  - 목적·날짜·시간·인원: `value`가 정답과 같으면 O. 정답 빈칸은 `null`이어야 O\n");
        md.append("  - 출발·도착: 정답이 ID 하나면 `value`가 같아야 O. `S01|S12`처럼 여럿이면 `value`가 비고 후보가 정확히 그 정류장들이어야 O. 정답이 빈칸이면 `value`도 비고 후보도 없어야 O\n");
        md.append("  - 확인필요: 출발·도착·날짜·시간·인원 중 `needsCheck = true`인 칸 묶음이 정답지 `확인필요`와 똑같아야 O\n\n");

        md.append("## 항목별\n\n| 항목 | 맞음 | 정확도 |\n|---|---|---|\n");
        right.forEach((c, ok) -> md.append("| ").append(c).append(" | ").append(ok).append("/").append(n)
                .append(" | ").append(Math.round(ok * 100.0 / n)).append("% |\n"));
        int cells = total * COLS.size();
        int sum = right.values().stream().mapToInt(Integer::intValue).sum();
        md.append("| 전체 칸 | ").append(sum).append("/").append(cells).append(" | ")
                .append(Math.round(sum * 100.0 / cells)).append("% |\n\n");

        md.append("## 대본별\n\n");
        md.append("O는 맞음, X는 `결과 (정답)`. 출발·도착의 `[S01, S12]`는 확정 없이 후보만 낸 경우.\n\n");
        md.append("| 번호 | 유형 | ").append(String.join(" | ", COLS)).append(" |\n");
        md.append("|---|---|").append("---|".repeat(COLS.size())).append("\n");
        rows.forEach(r -> md.append(r).append("\n"));

        md.append("\n## 참고\n\n");
        md.append("- 인원 결과가 빈칸인데 정답지에는 값이 있는 경우: ").append(peopleBlank)
                .append("건. `docs/api.md`·기능 규칙서는 인원을 말하지 않으면 1을 넣지 않고 빈칸 + 확인 필요로 둠. 정답지는 인원을 말하지 않은 대본도 1로 적음\n");
        md.append("- 확인필요가 인원 칸 하나 때문에만 틀린 경우: ").append(onlyPeopleCheck).append("건\n");
        md.append("- 변경·취소·문의 대본에서 확인필요가 틀린 경우: ").append(notReserveCheck)
                .append("건. 정답지는 이 대본들의 확인필요를 비워 두고, BE는 빈칸도 `needsCheck = true`로 둠 (`docs/api.md` Field 정의)\n");
        Files.writeString(RESULT, md.toString(), StandardCharsets.UTF_8);
    }

    private JsonNode extract(Map<String, String> row) throws Exception {
        List<Map<String, String>> transcript = new ArrayList<>();
        for (String part : row.get("대본").split(" / ")) {
            int at = part.indexOf(": ");
            String who = part.substring(0, at).trim();
            transcript.add(Map.of("speaker", who.equals("상담원") ? "AGENT" : "CUSTOMER", "text", part.substring(at + 2).trim()));
        }
        String receivedAt = row.get("접수일시").replace(" ", "T") + ":00+09:00";
        String body = om.writeValueAsString(Map.of("receivedAt", receivedAt, "transcript", transcript));
        String res = mvc.perform(post("/extract").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return om.readTree(res);
    }

    private static String mark(String got, String expected, String expectedShown) {
        String g = got == null ? "" : got;
        String e = expected == null ? "" : expected;
        return g.equals(e) ? "O" : "X: " + show(g) + " (정답 " + show(expectedShown) + ")";
    }

    private static String stopMark(JsonNode f, String expected) {
        String value = text(f.get("value"));
        List<String> ids = new ArrayList<>();
        f.get("candidates").forEach(c -> ids.add(c.get("stopId").asText()));
        String got = value != null ? value : ids.isEmpty() ? "" : ids.toString();
        boolean ok;
        if (expected.isEmpty()) {
            ok = value == null && ids.isEmpty();
        } else if (expected.contains("|")) {
            ok = value == null && new TreeSet<>(ids).equals(new TreeSet<>(Arrays.asList(expected.split("\\|"))));
        } else {
            ok = expected.equals(value);
        }
        return ok ? "O" : "X: " + show(got) + " (정답 " + show(expected) + ")";
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode() ? null : n.asText();
    }

    private static String show(String s) {
        return s == null || s.isEmpty() ? "빈칸" : s;
    }

    private static String join(Set<String> s) {
        return s.isEmpty() ? "없음" : String.join("·", s);
    }

    // 따옴표 안 쉼표 처리하는 csv 한 줄 파싱
    private static List<String> parse(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
