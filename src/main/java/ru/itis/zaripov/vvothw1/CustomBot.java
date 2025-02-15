package ru.itis.zaripov.vvothw1;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.starter.SpringWebhookBot;

import java.io.Serializable;
import java.util.List;

@Getter
@Component
public class CustomBot extends SpringWebhookBot {

    private final String botUsername;
    private final String botPath;

    private final YandexGptService yandexGptService;

    public CustomBot(SetWebhook setWebhook,
                     @Value("${app.telegram.bot.username}") String botUsername,
                     @Value("${app.telegram.bot.webhook-path}") String botPath,
                     @Value("${app.telegram.bot.token}") String token,
                     YandexGptService yandexGptService) {
        super(setWebhook, token);
        this.botUsername = botUsername;
        this.botPath = botPath;
        this.yandexGptService = yandexGptService;
    }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        if (update.hasMessage()) {
            var message = update.getMessage();
            var chatId = message.getChatId().toString();

            if (message.hasText()) {
                handleTextMessage(chatId, message.getText());
            } else if (message.hasPhoto()) {
                handlePhotoMessage(chatId, message.getPhoto());
            } else {
                sendTextMessage(chatId, "Я могу обработать только текстовое сообщение или фотографию.");
            }
        }
        return new BotApiMethod<>() {
            @Override
            public Serializable deserializeResponse(String answer) {
                return null;
            }

            @Override
            public String getMethod() {
                return "";
            }
        };
    }

    private void handleTextMessage(String chatId, String text) {
        String message;
        if ("/start".equals(text) || "/help".equals(text)) {
            message = """
                Я помогу подготовить ответ на экзаменационный вопрос по дисциплине "Операционные системы".
                Пришлите мне фотографию с вопросом или наберите его текстом.""";
        } else {
            try {
                message = yandexGptService.generateText(text);
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                message = "Я не смог подготовить ответ на экзаменационный вопрос.";
            }
        }
        sendTextMessage(chatId, message);
    }

    private void handlePhotoMessage(String chatId, List<PhotoSize> photos) {
        if (photos.size() != 1) {
            sendTextMessage(chatId, "Я могу обработать только одну фотографию.");
            return;
        }

        PhotoSize bestPhoto = photos.getFirst();
        String extractedText = extractTextFromYandexVision(bestPhoto.getFileId());
        if (extractedText != null) {
            handleTextMessage(chatId, extractedText);
        } else {
            sendTextMessage(chatId, "Я не могу обработать эту фотографию.");
        }
    }

    private void sendTextMessage(String chatId, String text) {
        SendMessage message = new SendMessage(chatId, text);
        message.setParseMode(ParseMode.MARKDOWN);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String extractTextFromYandexVision(String fileId) {
        // Логика взаимодействия с Yandex Vision OCR (распознавание текста с фото)
        return "Распознанный текст с фотографии";
    }
}
