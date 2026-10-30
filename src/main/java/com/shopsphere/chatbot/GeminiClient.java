
package com.shopsphere.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GeminiClient implements LlmClient {

    private final RestClient http;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public GeminiClient(
            @Value("${shopsphere.ai.api-key}") String apiKey,
            @Value("${shopsphere.ai.model}") String model,
            @Value("${shopsphere.ai.base-url}") String baseUrl,
            @Value("${shopsphere.ai.timeout-seconds}") int timeout,
            @Value("${shopsphere.ai.max-output-tokens}") int maxTokens) {

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(timeout));

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(String system, List<Message> messages) {

        if (!configured()) {
            throw new Unavailable("AI_API_KEY is not configured");
        }

        try {
            List<Map<String, Object>> contents = messages.stream()
                    .map(message -> Map.<String, Object>of(
                            "role",
                            message.role().equalsIgnoreCase("assistant")
                                    ? "model" : "user",
                            "parts",
                            List.of(Map.of("text", message.content()))
                    ))
                    .toList();

            Map<String, Object> requestBody = Map.of(
                    "systemInstruction",
                    Map.of("parts", List.of(Map.of("text", system))),
                    "contents", contents,
                    "generationConfig",
                    Map.of("maxOutputTokens", maxTokens)
            );

            JsonNode response = http.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            StringBuilder result = new StringBuilder();

            if (response != null) {
                JsonNode parts = response.path("candidates")
                        .path(0)
                        .path("content")
                        .path("parts");

                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        if (part.has("text")) {
                            result.append(part.get("text").asText());
                        }
                    }
                }
            }

            if (result.isEmpty()) {
                throw new Unavailable("Empty AI response");
            }

            return result.toString().trim();

        } catch (Unavailable exception) {
            throw exception;
        } catch (Exception exception) {
            throw new Unavailable(
                    exception.getClass().getSimpleName() + ": " +
                            (exception.getMessage() == null
                                    ? "No additional details"
                                    : exception.getMessage())
            );
        }
    }
}
