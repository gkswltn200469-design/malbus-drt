package kr.godri.malbus.config;

import kr.godri.malbus.extract.Extractor;
import kr.godri.malbus.extract.RuleExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExtractorConfig {

    private static final Logger log = LoggerFactory.getLogger(ExtractorConfig.class);

    // 키가 있으면 여기서 llm 구현으로 바꾸면 됨 (곽도윤). 아직 없어서 규칙 모드
    @Bean
    public Extractor extractor(@Value("${malbus.llm.api-key:}") String apiKey) {
        if (!apiKey.isBlank()) {
            log.warn("LLM 키는 있지만 LLM 추출기가 아직 없어서 규칙 모드로 동작");
        }
        return new RuleExtractor();
    }
}
