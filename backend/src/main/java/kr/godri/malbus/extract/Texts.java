package kr.godri.malbus.extract;

import java.util.List;

// 정류장 비교용 문자열 처리
public final class Texts {

    private static final List<String> TAILS = List.of("에서요", "까지요", "으로요", "이요", "에서", "까지", "으로", "요");

    private Texts() {
    }

    // 고객이 한 말: 조사 떼고 공백 없앰
    public static String norm(String said) {
        return key(stripParticles(said.trim()));
    }

    // 정류장명·별칭: 공백 없애고 건너편/맞은편은 건너로
    public static String key(String s) {
        return s.replaceAll("\\s+", "").replace("건너편", "건너").replace("맞은편", "건너");
    }

    public static String stripParticles(String s) {
        String t = s.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String tail : TAILS) {
                if (t.length() > tail.length() + 1 && t.endsWith(tail)) {
                    t = t.substring(0, t.length() - tail.length()).trim();
                    changed = true;
                    break;
                }
            }
            // 로는 받침 없거나 ㄹ받침 뒤에서만 조사 (하늘채로 o, 중앙로 x)
            if (!changed && t.length() > 2 && t.endsWith("로") && takesRo(t.charAt(t.length() - 2))) {
                t = t.substring(0, t.length() - 1).trim();
                changed = true;
            }
        }
        return t;
    }

    static boolean takesRo(char c) {
        if (c < 0xAC00 || c > 0xD7A3) return false;
        int jong = (c - 0xAC00) % 28;
        return jong == 0 || jong == 8;
    }

    // 끝이 앞/건너면 방향
    public static String direction(String key) {
        if (key.endsWith("건너")) return "건너";
        if (key.endsWith("앞")) return "앞";
        return null;
    }

    public static String withoutDirection(String key) {
        String dir = direction(key);
        return dir == null || key.length() == dir.length() ? key : key.substring(0, key.length() - dir.length());
    }

    // 가장 긴 공통 부분 문자열
    public static String longestCommon(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        int best = 0, end = 0;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    if (dp[i][j] > best) {
                        best = dp[i][j];
                        end = i;
                    }
                }
            }
        }
        return a.substring(end - best, end);
    }

    // 편집 거리 기준 비슷한 정도 0~1
    public static double similarity(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        int max = Math.max(a.length(), b.length());
        return max == 0 ? 1 : 1 - (double) dp[a.length()][b.length()] / max;
    }
}
