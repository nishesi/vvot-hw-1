package ru.itis.zaripov.vvothw1;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class YandexGptService {

    private final RestClient restClient;

    private final RequirementsService requirementsService;

    @Value("${app.yandex.llm.token}")
    private final String token;

    @Value("${app.folder-id}")
    private final String folderId;

    public String generateText(String query) {
        ResponseEntity<ResponseBodyDto> response = restClient.post()
            .uri("https://llm.api.cloud.yandex.net/foundationModels/v1/completion")
            .header("Authorization", "Bearer " + token)
            .body(RequestBodyDto.builder()
                .modelUri("gpt://" + folderId + "/yandexgpt/rc")
                .completionOptions(new RequestBodyDto.CompletionOptions(0.5, 2000))
                .messages(List.of(
                    new Message("system", requirementsService.getRequirements()),
                    new Message("user", query)))
                .build())
            .retrieve()
            .toEntity(ResponseBodyDto.class);

        return response.getBody().result().alternatives().getFirst().message().text();
    }

    @Builder
    public record RequestBodyDto(
        String modelUri,
        CompletionOptions completionOptions,
        List<Message> messages
    ) {

        public record CompletionOptions(double temperature, int maxTokens) {

        }
    }

    public record ResponseBodyDto(Result result) {

        public record Result(List<Alternative> alternatives) {

            public record Alternative(Message message, String status) {

            }
        }
    }

    public record Message(String role, String text) {

    }
}
