package com.academy.reschedu.domain.aisummary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gemini generateContent REST 호출만 담당하는 얇은 클라이언트. 통계 계산이나 캐싱 같은 다른 관심사는
 * {@link AiSummaryService}가 맡고, 여기서는 "프롬프트를 보내고 텍스트를 받는다"만 책임진다.
 * 호출이 실패(키 미설정/타임아웃/API 오류)해도 예외를 던지지 않고 Optional.empty()로 돌려줘, 호출부가
 * AI 문장 없이 통계만으로 우아하게 응답할 수 있게 한다 — 이 기능은 "있으면 좋은" 부가 기능이지
 * 실패했다고 학부모 대시보드 전체가 깨지면 안 되기 때문이다.
 */
@Slf4j
@Component
public class GeminiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_OUTPUT_TOKENS = 220;
    private static final double TEMPERATURE = 0.4;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-3.6-flash}")
    private String model;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * systemInstruction(역할/제약)과 userMessage(실제 데이터)를 보내 자연어 응답을 받는다.
     * 키가 설정되어 있지 않거나 호출이 실패하면 조용히 empty를 반환한다.
     */
    public Optional<String> generate(String systemInstruction, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userMessage)))),
                // thinkingBudget:0 — 이 요약 작업엔 추론이 필요 없어 꺼둔다. 안 끄면 답변 자체보다
                // "생각" 토큰이 수백 개 더 붙어(예: 실측 562 vs 44 totalTokenCount) 느리고 비용도 늘어난다.
                "generationConfig", Map.of(
                        "temperature", TEMPERATURE,
                        "maxOutputTokens", MAX_OUTPUT_TOKENS,
                        "thinkingConfig", Map.of("thinkingBudget", 0)
                )
        );

        try {
            String rawResponse = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={apiKey}", model, apiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return extractText(rawResponse);
        } catch (Exception e) {
            // 네트워크 오류/타임아웃/Gemini 측 오류 모두 이 부가 기능 하나 때문에 요청 전체를 실패시키지 않는다.
            log.warn("Gemini 호출 실패 — AI 요약 없이 통계만 내려갑니다.", e);
            return Optional.empty();
        }
    }

    private Optional<String> extractText(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.isNull()) {
                return Optional.empty();
            }
            String text = textNode.asText().trim();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            log.warn("Gemini 응답 파싱 실패", e);
            return Optional.empty();
        }
    }
}
