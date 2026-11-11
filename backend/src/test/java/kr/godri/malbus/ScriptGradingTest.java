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
    // 채점 칸은 5개. 정답지에 목적·인원 열이 있어도 안 봄 (목적 고정, 인원 화면 제외)
    private static final List<String> COLS = List.of("출발", "도착", "날짜", "시간", "확인필요");
    private static final List<String> KEYS = List.of("from", "to", "date", "time");
    private static final List<String> KO = List.of("출발", "도착", "날짜", "시간");

    private static final Comparator<String> BY_FIELD = Comparator.comparingInt(KO::indexOf);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @Test
    void 대본_채점() throws Exception {
        List<String> lines = Files.readAllLines(ANSWERS, StandardCharsets.UTF_8);
        List<String> header = parse(lines.get(0).replace("\uFEFF", ""));

        Map<String, Integer> right = new LinkedHashMap<>();
        COLS.forEach(c -> right.put(c, 0));
        List<String> rows = new ArrayList<>();
        List<String> checkWrong = new ArrayList<>();
        int total = 0;

        for (String raw : lines.subList(1, lines.size())) {
            if (raw.isBlank()) continue;
            List<String> cells = parse(raw);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) row.put(header.get(i).trim(), i < cells.size() ? cells.get(i).trim() : "");
            JsonNode out = extract(row);
            assertThat(out.get("mode").asText()).isEqualTo("RULE");
            total++;

            Map<String, String> marks = new LinkedHashMap<>();
            marks.put("출발", stopMark(out.get("from"), col(row, "출발")));
            marks.put("도착", stopMark(out.get("to"), col(row, "도착")));
            marks.put("날짜", mark(text(out.at("/date/value")), col(row, "날짜")));
            marks.put("시간", mark(text(out.at("/time/value")), col(row, "시간")));

            // 확인필요는 출발·도착·날짜·시간만 비교
            Set<String> expected = new TreeSet<>(BY_FIELD);
            for (String f : col(row, "확인필요").split("\\|")) {
                if (KO.contains(f.trim())) expected.add(f.trim());
            }
            Set<String> got = new TreeSet<>(BY_FIELD);
            for (int i = 0; i < KEYS.size(); i++) {
                if (out.at("/" + KEYS.get(i) + "/needsCheck").asBoolean()) got.add(KO.get(i));
            }
            boolean checkOk = expected.equals(got);
            marks.put("확인필요", checkOk ? "O" : "X: " + join(got) + " (정답 " + join(expected) + ")");
            if (!checkOk) checkWrong.add(col(row, "번호") + "번(" + col(row, "유형") + ")");

            marks.forEach((c, m) -> {
                if (m.equals("O")) right.merge(c, 1, Integer::sum);
            });
            rows.add("| " + col(row, "번호") + " | " + col(row, "유형") + " | " + String.join(" | ", marks.values()) + " |");
        }
        assertThat(total).isEqualTo(20);
        int n = total;

        StringBuilder md = new StringBuilder();
        md.append("# 채점 결과 (RULE 모드)\n\n");
        md.append("- 대상: `test/대본_정답지.csv` 대본 ").append(total).append("개를 `POST /extract`에 넣은 결과 (규칙 모드, 배운 별칭 없음)\n");
        md.append("- 다시 만들기: `cd backend && ./gradlew grade` (이 파일을 덮어씀)\n");
        md.append("- 채점 칸: 출발, 도착, 날짜, 시간, 확인필요. 목적('이동수단 예약' 고정)과 인원(화면 제외)은 채점하지 않음\n");
        md.append("- 판정 기준\n");
        md.append("  - 날짜·시간: `value`가 정답과 같으면 O. 정답 빈칸은 `null`이어야 O\n");
        md.append("  - 출발·도착: 정답이 ID 하나면 `value`가 같아야 O. `S01|S12`처럼 여럿이면 `value`가 비고 후보가 정확히 그 정류장들이어야 O. 정답이 빈칸이면 `value`도 비고 후보도 없어야 O\n");
        md.append("  - 확인필요: 출발·도착·날짜·시간 중 `needsCheck = true`인 칸 묶음이 정답지 `확인필요`와 똑같아야 O\n\n");

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
        md.append("- 확인필요가 틀린 대본: ").append(checkWrong.isEmpty() ? "없음" : String.join(", ", checkWrong)).append("\n");
        md.append("- BE는 빈칸도 `needsCheck = true`로 둠 (`docs/api.md` Field 정의)\n");
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

    private static String mark(String got, String expected) {
        String g = got == null ? "" : got;
        String e = expected == null ? "" : expected;
        return g.equals(e) ? "O" : "X: " + show(g) + " (정답 " + show(e) + ")";
    }

    // 정답지 열 이름으로 읽음. 없는 열은 빈칸
    private static String col(Map<String, String> row, String name) {
        return row.getOrDefault(name, "");
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
