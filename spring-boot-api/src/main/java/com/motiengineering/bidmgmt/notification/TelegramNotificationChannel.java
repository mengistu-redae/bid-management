package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Map;

/**
 * The second NotificationChannel per the brief's "design notifications so a
 * Telegram bot channel can be added later" note - email is the only one
 * required for v1, this is additive. A recipient needs a bot token
 * configured (bidmgmt.notifications.telegram-bot-token, empty by default -
 * the channel supports no one until it's set) and their own chat id saved
 * on their profile (AppUser.telegramChatId, set from the user management
 * page - see the app's login README for how a person finds their own chat
 * id by messaging the bot).
 *
 * Telegram's Bot API only renders a small HTML subset, nothing like the
 * full markup EmailTemplates builds for email, so messages here are sent as
 * plain text (toPlainText strips tags rather than trying to map them onto
 * Telegram's subset) - simpler and never risks a malformed-entity send
 * failure from a tag Telegram doesn't support.
 */
@Component
public class TelegramNotificationChannel implements NotificationChannel {

    private final RestClient restClient;
    private final String botToken;

    public TelegramNotificationChannel(
            RestClient.Builder restClientBuilder,
            @Value("${bidmgmt.notifications.telegram-bot-token:}") String botToken) {
        this.restClient = restClientBuilder.build();
        this.botToken = botToken;
    }

    @Override
    public boolean supports(AppUser recipient) {
        return botToken != null && !botToken.isBlank()
                && recipient != null
                && recipient.getTelegramChatId() != null && !recipient.getTelegramChatId().isBlank();
    }

    @Override
    public void send(AppUser recipient, String subject, String htmlBody) {
        Map<String, Object> body = Map.of(
                "chat_id", recipient.getTelegramChatId(),
                "text", toPlainText(subject, htmlBody));
        try {
            restClient.post()
                    // A raw java.net.URI, not a "/bot{token}/..." template - Spring's
                    // template-variable encoding percent-encodes ':' in path segments,
                    // which would mangle every real bot token (always formatted
                    // "<numeric id>:<secret>") into a URL Telegram would 404 on.
                    .uri(URI.create("https://api.telegram.org/bot" + botToken + "/sendMessage"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new NotificationSendException("Failed to send Telegram message to chat " + recipient.getTelegramChatId(), e);
        }
    }

    static String toPlainText(String subject, String html) {
        String text = html
                .replaceAll("(?i)<li>", "\n- ")
                .replaceAll("(?i)</(p|h2|h3|li|ul)>", "\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&middot;", "-")
                .replace("&rarr;", "->");
        String collapsed = text.replaceAll("[ \\t]+", " ").replaceAll("\n{3,}", "\n\n").trim();
        return subject + "\n\n" + collapsed;
    }
}
