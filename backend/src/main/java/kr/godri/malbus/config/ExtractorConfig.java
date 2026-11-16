package kr.godri.malbus.config;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import kr.godri.malbus.extract.ClaudeClient;
import kr.godri.malbus.extract.Extractor;
import kr.godri.malbus.extract.GeminiClient;
import kr.godri.malbus.extract.LlmClient;
import kr.godri.malbus.extract.LlmExtractor;
import kr.godri.malbus.extract.RuleExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class ExtractorConfig {

    private static final Logger log = LoggerFactory.getLogger(ExtractorConfig.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    // GEMINI_API_KEY 있으면 Gemini, 없고 ANTHROPIC_API_KEY 있으면 Claude, 둘 다 없으면 규칙 모드 (키 없이도 시연 가능)
    @Bean
    public Extractor extractor(@Value("${malbus.llm.gemini.api-key:}") String geminiKey,
                               @Value("${malbus.llm.gemini.model:gemini-3.6-flash}") String geminiModel,
                               @Value("${malbus.llm.claude.api-key:}") String claudeKey,
                               @Value("${malbus.llm.claude.model:claude-sonnet-5-5}") String claudeModel,
                               @Value("${malbus.llm.fallback-to-rule:true}") boolean fallbackToRule) {
        LlmClient client;
        if (!geminiKey.isBlank()) {
            SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
            timeouts.setConnectTimeout(TIMEOUT);
            timeouts.setReadTimeout(TIMEOUT);
            client = new GeminiClient(GeminiClient.http(geminiKey).requestFactory(timeouts).build(), geminiModel);
        } else if (!claudeKey.isBlank()) {
            client = new ClaudeClient(
                    AnthropicOkHttpClient.builder().apiKey(claudeKey).timeout(TIMEOUT).maxRetries(1).build(),
                    claudeModel);
        } else {
            log.info("LLM 키 없음, 규칙 모드로 동작");
            return new RuleExtractor();
        }
        log.info("LLM 모드로 동작: {}", client.name());
        return new LlmExtractor(client, fallbackToRule ? new RuleExtractor() : null);
    }
}
