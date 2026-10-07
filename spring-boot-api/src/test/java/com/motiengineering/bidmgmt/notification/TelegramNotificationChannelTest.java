package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/** Covers supports()'s two-part gate and the actual HTTP shape sent to Telegram's Bot API - can't live-verify without a real bot token (see memory). */
class TelegramNotificationChannelTest {

    private AppUser recipientWithChatId(String chatId) {
        AppUser user = new AppUser();
        user.setFullName("Selam Girma");
        user.setTelegramChatId(chatId);
        return user;
    }

    @Test
    void supportsNobodyWhenNoBotTokenIsConfigured() {
        RestClient.Builder builder = RestClient.builder();
        TelegramNotificationChannel channel = new TelegramNotificationChannel(builder, "");

        assertThat(channel.supports(recipientWithChatId("12345"))).isFalse();
    }

    @Test
    void supportsOnlyRecipientsWithAChatId() {
        RestClient.Builder builder = RestClient.builder();
        TelegramNotificationChannel channel = new TelegramNotificationChannel(builder, "bot-token-123");

        assertThat(channel.supports(recipientWithChatId("12345"))).isTrue();
        assertThat(channel.supports(recipientWithChatId(null))).isFalse();
        assertThat(channel.supports(recipientWithChatId(""))).isFalse();
    }

    @Test
    void sendPostsChatIdAndPlainTextToTelegramsSendMessageEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramNotificationChannel channel = new TelegramNotificationChannel(builder, "123:ABC-token");

        server.expect(requestTo("https://api.telegram.org/bot123:ABC-token/sendMessage"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"chat_id\":\"12345\",\"text\":\"Bid closing in 7 days\\n\\nCore banking refresh\"}"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        channel.send(recipientWithChatId("12345"), "Bid closing in 7 days", "<h2>Core banking refresh</h2>");

        server.verify();
    }

    @Test
    void sendWrapsAFailedCallIntoNotificationSendException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramNotificationChannel channel = new TelegramNotificationChannel(builder, "456:bad-token");

        server.expect(requestTo("https://api.telegram.org/bot456:bad-token/sendMessage"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> channel.send(recipientWithChatId("12345"), "Subject", "<p>Body</p>"))
                .isInstanceOf(NotificationSendException.class);
    }

    @Test
    void toPlainTextStripsHtmlAndKeepsListItemsReadable() {
        String html = "<h2>Daily digest</h2><ul><li>Bank A - 10,000,000</li><li>Bank B - 5,000,000</li></ul><p>Done.</p>";

        String text = TelegramNotificationChannel.toPlainText("Daily digest", html);

        assertThat(text).isEqualTo("Daily digest\n\nDaily digest\n\n- Bank A - 10,000,000\n\n- Bank B - 5,000,000\n\nDone.");
    }
}
