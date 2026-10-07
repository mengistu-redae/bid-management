package com.motiengineering.bidmgmt.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.enums.EntityType;
import com.motiengineering.bidmgmt.domain.enums.ReminderType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Sends a real reminder email through JavaMailSender over SMTP to the local
 * Mailpit catcher (see docker-compose.yml's mailpit service) and reads it
 * back out via Mailpit's REST API - the one part of the notification
 * pipeline a mocked JavaMailSender can't verify: that the message actually
 * leaves EmailNotificationChannel with a deliverable envelope.
 */
@SpringBootTest
@Transactional
class NotificationServiceLiveTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5434/bidmgmt_test");
        registry.add("spring.datasource.username", () -> "bidmgmt");
        registry.add("spring.datasource.password", () -> "bidmgmt");
        registry.add("bidmgmt.seed.enabled", () -> "false");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "1026");
    }

    @Autowired
    private NotificationService notificationService;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sentEmailArrivesInMailpit() throws Exception {
        String uniqueSubject = "Live notification test " + UUID.randomUUID();
        AppUser recipient = new AppUser();
        recipient.setId(UUID.randomUUID());
        recipient.setEmail("live-test-recipient@motiengineering.com");
        recipient.setFullName("Live Test Recipient");

        notificationService.sendIfNotAlreadySent(
                ReminderType.BID_CLOSING_7D, EntityType.BID, UUID.randomUUID(), recipient,
                new EmailContent(uniqueSubject, "<p>Live test body</p>"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode message = findMessageBySubject(uniqueSubject);
            assertThat(message).isNotNull();
            assertThat(message.at("/To/0/Address").asText()).isEqualTo("live-test-recipient@motiengineering.com");
        });
    }

    private JsonNode findMessageBySubject(String subject) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8026/api/v1/messages?limit=50")).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        for (JsonNode message : root.get("messages")) {
            if (subject.equals(message.get("Subject").asText())) {
                return message;
            }
        }
        return null;
    }
}
