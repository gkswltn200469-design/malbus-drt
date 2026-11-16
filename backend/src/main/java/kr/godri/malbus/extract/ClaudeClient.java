package kr.godri.malbus.extract;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;

import java.util.List;
import java.util.stream.Collectors;

// Claude API 호출. ANTHROPIC_API_KEY만 있을 때 씀 (기본은 Gemini)
public class ClaudeClient implements LlmClient {

    private final AnthropicClient client;
    private final String model;

    public ClaudeClient(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String name() {
        return "claude:" + model;
    }

    @Override
    public String complete(String system, String user) {
        JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
        LlmSchema.aiOutput().forEach((k, v) -> schema.putAdditionalProperty(k, JsonValue.from(v)));

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                // 정류장 목록·예시가 든 앞부분은 매번 같아서 캐시
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(system)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(user)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW) // 짧은 추출이라 낮게. 상담원이 기다리는 시간 줄임
                        .format(JsonOutputFormat.builder().schema(schema.build()).build())
                        .build())
                // 안전 분류기가 거절하면 서버가 다른 모델로 다시 시도
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
        Message res = client.messages().create(params);

        StopReason stop = res.stopReason().orElse(null);
        if (!StopReason.END_TURN.equals(stop)) {
            throw new IllegalStateException("Claude 응답이 끝까지 오지 않음: stop_reason=" + stop);
        }
        return res.content().stream()
                .flatMap(b -> b.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
    }
}
