package kr.godri.malbus.config;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import kr.godri.malbus.extract.Extractor;
import kr.godri.malbus.extract.LlmExtractor;
import kr.godri.malbus.extract.RuleExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ExtractorConfig {

    private static final Logger log = LoggerFactory.getLogger(ExtractorConfig.class);

    // ANTHROPIC_API_KEY가 있으면 LLM, 없으면 규칙 모드 (키 없이도 시연 가능)
    @Bean
    public Extractor extractor(@Value("${malbus.llm.api-key:}") String apiKey,
                               @Value("${malbus.llm.model:claude-sonnet-5-5}") String model,
                               @Value("${malbus.llm.fallback-to-rule:true}") boolean fallbackToRule) {
        if (apiKey.isBlank()) {
            log.info("LLM 키 없음, 규칙 모드로 동작");
            return new RuleExtractor();
        }
        log.info("LLM 모드로 동작: {}", model);
        return new LlmExtractor(
                AnthropicOkHttpClient.builder()
                        .apiKey(apiKey)
                        .timeout(Duration.ofSeconds(30))
                        .maxRetries(1)
                        .build(),
                model,
                fallbackToRule ? new RuleExtractor() : null);
    }
}
