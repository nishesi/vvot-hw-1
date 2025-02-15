package ru.itis.zaripov.vvothw1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;

@Configuration
public class CustomConfig {

    @Bean
    public SetWebhook setWebhook(@Value("${app.telegram.bot.webhook-path}") String webhookPath,
                                 @Value("${app.telegram.bot.username}") String username) {
        return SetWebhook.builder()
            .url(webhookPath)
            .build();
    }
}
