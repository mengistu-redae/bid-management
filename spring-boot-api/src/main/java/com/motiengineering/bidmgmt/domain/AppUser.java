package com.motiengineering.bidmgmt.domain;

import com.motiengineering.bidmgmt.domain.enums.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @UuidGenerator
    private UUID id;

    private String keycloakUserId;

    private String fullName;

    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean active = true;

    /** Set by the user themselves (or an admin) via the user management page - see TelegramNotificationChannel. */
    private String telegramChatId;

    @CreationTimestamp
    private Instant createdAt;
}
