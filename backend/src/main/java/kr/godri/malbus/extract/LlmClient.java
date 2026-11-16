package kr.godri.malbus.extract;

// LLM 한 번 호출. 시스템 프롬프트 + 사용자 메시지 -> 스키마(LlmSchema)에 맞는 JSON 문자열
// 구현: GeminiClient(기본), ClaudeClient
public interface LlmClient {

    String name();

    String complete(String system, String user);
}
