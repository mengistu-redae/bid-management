package com.motiengineering.bidmgmt.notification;

import com.motiengineering.bidmgmt.domain.AppUser;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    @Value("${bidmgmt.notifications.from-address}")
    private String fromAddress;

    @Override
    public boolean supports(AppUser recipient) {
        return recipient.getEmail() != null && !recipient.getEmail().isBlank();
    }

    @Override
    public void send(AppUser recipient, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipient.getEmail());
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            // Wrapped (not swallowed) so NotificationService can catch it
            // per-recipient and keep going for everyone else in the batch -
            // and since NotificationService only records a sent-log row
            // after this call returns normally, a failed send here is
            // automatically retried on the next scheduler run instead of
            // silently being marked "sent".
            throw new NotificationSendException("Failed to send email to " + recipient.getEmail(), e);
        }
    }
}
