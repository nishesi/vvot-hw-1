package ru.itis.zaripov.vvothw1;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@RequiredArgsConstructor
public class CustomWebhookController {

    private final CustomBot customBot;

    @PostMapping
    public BotApiMethod<?> handleWebhook(@RequestBody CloudFunctionRequest body) {
        return customBot.onWebhookUpdateReceived(body.body());
    }

    public record CloudFunctionRequest(Update body) {

    }
}
