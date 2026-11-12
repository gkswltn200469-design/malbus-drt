package kr.godri.malbus.extract;

import kr.godri.malbus.domain.TranscriptLine;
import kr.godri.malbus.dto.StopDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 키 없을 때 쓰는 규칙 추출. 고객 말만 보고, 같은 칸을 여러 번 말하면 마지막 말을 씀
// ponytail: 정규식이라 자유 발화는 잘 못 잡음. llm 붙으면 그쪽이 주력
public class RuleExtractor implements Extractor {

    private static final String NUM = "(\\d{1,2}|열두|열한|열|한|두|세|네|다섯|여섯|일곱|여덟|아홉)";
    private static final Pattern TIME = Pattern.compile(
            "(?:(오전|오후|아침|저녁|낮|밤|새벽)\\s*|(?<![가-힣]))" + NUM
                    + "\\s*시(?=$|[\\s\\d반에로쯤까요부정,.?!~])\\s*(반|(\\d{1,2})\\s*분|(십|이십|삼십|사십|오십)\\s*분)?");
    private static final Pattern DATE = Pattern.compile(
            "내일\\s*모레|오늘|내일|모레|(?:(?:다음|이번)\\s*주\\s*)?[월화수목금토일]요일|다음\\s*주");
    // 인원은 이번 범위에서 추출 안 함. "둘이" 같은 말을 장소로 잡지 않게 지우는 데만 씀
    private static final Pattern PEOPLE = Pattern.compile(
            "혼자|(둘|셋|넷)\\s*이|(\\d{1,2})\\s*명|(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)\\s*(?:명|분|사람)");
    private static final Pattern CANCEL = Pattern.compile("취소");
    private static final Pattern CHANGE = Pattern.compile("바꿔|바꾸|바꿀|변경|옮겨|기로\\s*한\\s*거|예약한\\s*거|예약했는데");
    private static final Pattern INQUIRY = Pattern.compile("몇\\s*시에?\\s*(있|와|오|다니)|있어요\\?|있나요|있습니까|얼마|문의|궁금");

    // 장소 구간 나눌 때 지우는 것: 시간(+조사), 날짜, 인원, "몇 시", 문장부호
    private static final Pattern MASK = Pattern.compile(
            TIME.pattern() + "\\s*(?:쯤|정각)?(?:에|으로|로|까지|부터)?"
                    + "|(?:" + DATE.pattern() + ")(?:에|은|는)?|(?:" + PEOPLE.pattern() + ")\\S*|몇\\s*시\\S*|[,.?!~]");
    private static final Pattern VERB = Pattern.compile(
            "^(가|타|와|오)$|^(가|타)(요|는|고|서|도|기|려|야|면|ㅂ)|^(갈|갑|갔|탈|탑|탔|올|온|내려|내릴)|^오(는|고|면|세요)");
    private static final Pattern TO_MARK = Pattern.compile("^(.*?)(까지요?|으로요?|에요?)$");
    private static final Set<String> FILLER = Set.of("아", "어", "음", "그", "저", "제가", "저는", "나", "나는", "그리고",
            "그럼", "네", "예", "아니", "아니요", "아니다", "좀", "그냥", "지금", "이제", "안", "못", "다시", "또", "같이");
    private static final Map<String, Integer> KNUM = Map.ofEntries(
            Map.entry("한", 1), Map.entry("두", 2), Map.entry("세", 3), Map.entry("네", 4), Map.entry("다섯", 5),
            Map.entry("여섯", 6), Map.entry("일곱", 7), Map.entry("여덟", 8), Map.entry("아홉", 9), Map.entry("열", 10),
            Map.entry("열한", 11), Map.entry("열두", 12),
            Map.entry("십", 10), Map.entry("이십", 20), Map.entry("삼십", 30), Map.entry("사십", 40), Map.entry("오십", 50));

    @Override
    public String mode() {
        return "RULE";
    }

    @Override
    public AiOutput extract(List<TranscriptLine> transcript, List<StopDto> stops) {
        AiOutput.Purpose purpose = new AiOutput.Purpose("RESERVE", null);
        int purposeRank = 0;
        AiOutput.Place from = null, to = null;
        AiOutput.DateSaid date = null;
        AiOutput.TimeSaid time = null;

        for (TranscriptLine line : transcript) {
            if (!"CUSTOMER".equals(line.getSpeaker())) continue; // 상담원 말("몇 분 타세요?")은 안 봄
            String t = line.getText();
            int no = line.getLineNo();

            int rank = CANCEL.matcher(t).find() ? 3 : CHANGE.matcher(t).find() ? 2 : INQUIRY.matcher(t).find() ? 1 : 0;
            if (rank > purposeRank) {
                purposeRank = rank;
                purpose = new AiOutput.Purpose(List.of("RESERVE", "INQUIRY", "CHANGE", "CANCEL").get(rank), no);
            }

            String d = last(DATE.matcher(t));
            if (d != null) date = new AiOutput.DateSaid(d, no);

            AiOutput.TimeSaid tm = time(t, no);
            if (tm != null) time = tm;

            String[] places = places(t);
            if (places[0] != null) from = new AiOutput.Place(places[0], no, List.of(), null);
            if (places[1] != null) to = new AiOutput.Place(places[1], no, List.of(), null);
        }
        return new AiOutput(purpose, from, to, date, time, null);
    }

    private static String last(Matcher m) {
        String found = null;
        while (m.find()) found = m.group();
        return found;
    }

    static AiOutput.TimeSaid time(String t, int no) {
        Matcher m = TIME.matcher(t);
        AiOutput.TimeSaid found = null;
        while (m.find()) {
            String part = m.group(1);
            String meridiem = part == null ? null
                    : Set.of("오전", "아침", "새벽").contains(part) ? "AM" : "PM";
            int hour = number(m.group(2));
            int minute = m.group(3) == null ? 0
                    : m.group(3).equals("반") ? 30
                    : m.group(4) != null ? Integer.parseInt(m.group(4)) : KNUM.get(m.group(5));
            found = new AiOutput.TimeSaid(m.group().trim(), hour, minute, meridiem, no);
        }
        return found;
    }

    private static int number(String s) {
        return s.chars().allMatch(Character::isDigit) ? Integer.parseInt(s) : KNUM.get(s);
    }

    // [출발, 도착]. "X에서" 앞을 출발, 그 뒤 "Y까지/으로/가요" 를 도착으로 봄
    static String[] places(String t) {
        String from = null, to = null;
        for (String clause : MASK.matcher(t).replaceAll(" | ").split("\\|")) {
            String[] tk = clause.trim().split("\\s+");
            boolean anchored = false;
            for (int i = 0; i < tk.length; i++) {
                int at = tk[i].indexOf("에서");
                if (at >= 0) {
                    String f = backward(tk, i, tk[i].substring(0, at));
                    if (f != null) from = f;
                    String g = forward(tk, i + 1);
                    if (g != null) to = g;
                    anchored = true;
                }
            }
            if (anchored) continue;
            // 출발 없이 도착만 말한 경우: 표시(까지/으로/에)가 있거나 바로 뒤가 "가요" 같은 말
            for (int i = 0; i < tk.length; i++) {
                if (tk[i].isEmpty() || FILLER.contains(tk[i]) || VERB.matcher(tk[i]).find()) continue;
                String head = toHead(tk[i]);
                boolean beforeVerb = i + 1 < tk.length && VERB.matcher(tk[i + 1]).find();
                if (head == null && !beforeVerb) continue;
                String g = backward(tk, i, head == null ? tk[i] : head);
                if (g != null) to = g;
            }
        }
        return new String[]{from, to};
    }

    // i번째 단어(head까지) 앞쪽으로 최대 4단어
    private static String backward(String[] tk, int i, String head) {
        List<String> words = new ArrayList<>();
        if (!head.isEmpty()) words.add(head);
        for (int j = i - 1; j >= 0 && words.size() < 4; j--) {
            String w = tk[j];
            if (w.isEmpty() || FILLER.contains(w) || w.endsWith("데") || w.endsWith("다") || VERB.matcher(w).find()) break;
            words.add(0, w);
        }
        return words.isEmpty() ? null : Texts.stripParticles(String.join(" ", words));
    }

    // "에서" 뒤: 표시나 동사가 나올 때까지, 끝까지 가도 인정 ("청구아파트에서 2동 주민센터요")
    private static String forward(String[] tk, int start) {
        List<String> words = new ArrayList<>();
        for (int j = start; j < tk.length; j++) {
            String w = tk[j];
            if (w.isEmpty()) continue;
            if (VERB.matcher(w).find()) break;
            if (words.isEmpty() && FILLER.contains(w)) continue;
            String head = toHead(w);
            if (head != null) {
                if (!head.isEmpty()) words.add(head);
                break;
            }
            words.add(w);
            if (words.size() > 4) return null;
        }
        return words.isEmpty() ? null : Texts.stripParticles(String.join(" ", words));
    }

    // 도착 표시 떼기. 없으면 null
    private static String toHead(String w) {
        Matcher m = TO_MARK.matcher(w);
        if (m.matches()) return m.group(1);
        String s = w.endsWith("로요") ? w.substring(0, w.length() - 1) : w;
        if (s.length() >= 2 && s.endsWith("로") && Texts.takesRo(s.charAt(s.length() - 2))) {
            return s.substring(0, s.length() - 1);
        }
        return null;
    }
}
