package com.motiengineering.bidmgmt.service;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.Division;
import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.UserDivisionId;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.dto.CreateUserRequest;
import com.motiengineering.bidmgmt.dto.DivisionDto;
import com.motiengineering.bidmgmt.dto.GrantLoginResult;
import com.motiengineering.bidmgmt.dto.UpdateUserRequest;
import com.motiengineering.bidmgmt.dto.UserAdminRowDto;
import com.motiengineering.bidmgmt.keycloakadmin.KeycloakUserProvisioningClient;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.DivisionRepository;
import com.motiengineering.bidmgmt.repository.UserDivisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Director-only user administration: create a person as an app_users row
 * (most account officers/scouts only ever need this - a reference row other
 * entities link to, no login), separately grant them a real Keycloak login
 * when they actually need to sign in, and edit role/divisions/Telegram chat
 * id. See BidAccessService's javadoc for how role/division drive access.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final int TEMP_PASSWORD_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final UserDivisionRepository userDivisionRepository;
    private final DivisionRepository divisionRepository;
    private final BidMapper bidMapper;
    private final KeycloakUserProvisioningClient keycloakUserProvisioningClient;

    @Transactional(readOnly = true)
    public List<UserAdminRowDto> list() {
        return appUserRepository.findAll().stream().map(this::toAdminRow).toList();
    }

    public UserAdminRowDto create(CreateUserRequest request) {
        if (appUserRepository.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new IllegalArgumentException("A user with this email already exists: " + request.email());
        }
        AppUser user = new AppUser();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setRole(parseRole(request.role()));
        user.setActive(true);
        user = appUserRepository.save(user);
        replaceDivisions(user, request.role(), request.divisionIds());
        return toAdminRow(user);
    }

    /**
     * Email is only editable before a login exists - once Keycloak has a
     * real account for this person, its own email is the source of truth,
     * and changing app_users.email alone would desync the two (this call
     * never talks to Keycloak). The edit page disables the field to match;
     * this is the defense-in-depth backstop.
     */
    public UserAdminRowDto update(UUID userId, UpdateUserRequest request) {
        AppUser user = requireUser(userId);
        if (user.getKeycloakUserId() == null) {
            String newEmail = blankToNull(request.email());
            if (newEmail != null && !newEmail.equalsIgnoreCase(user.getEmail())) {
                appUserRepository.findByEmailIgnoreCase(newEmail).filter(existing -> !existing.getId().equals(userId))
                        .ifPresent(existing -> {
                            throw new IllegalArgumentException("A user with this email already exists: " + newEmail);
                        });
            }
            user.setEmail(newEmail);
        }
        user.setFullName(request.fullName());
        user.setRole(parseRole(request.role()));
        user.setTelegramChatId(blankToNull(request.telegramChatId()));
        user = appUserRepository.save(user);
        replaceDivisions(user, request.role(), request.divisionIds());
        return toAdminRow(user);
    }

    /**
     * Creates the real Keycloak account for an existing app_users row that
     * doesn't have one yet (createUser alone never does this - most
     * reference rows never need a login at all). Returns a one-time
     * temporary password; nothing stores it after this call returns.
     */
    public GrantLoginResult grantLogin(UUID userId) {
        AppUser user = requireUser(userId);
        if (user.getKeycloakUserId() != null) {
            throw new IllegalStateException("This user already has a login");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("This person has no email on file - edit them first to add one before granting a login");
        }
        String[] names = splitFullName(user.getFullName());
        String keycloakUserId = keycloakUserProvisioningClient.createUser(user.getEmail(), names[0], names[1]);
        keycloakUserProvisioningClient.assignRealmRole(keycloakUserId, user.getRole().name().toLowerCase());
        String temporaryPassword = generateTemporaryPassword();
        keycloakUserProvisioningClient.setTemporaryPassword(keycloakUserId, temporaryPassword);

        user.setKeycloakUserId(keycloakUserId);
        user = appUserRepository.save(user);
        return new GrantLoginResult(toAdminRow(user), temporaryPassword);
    }

    public UserAdminRowDto setActive(UUID userId, boolean active) {
        AppUser user = requireUser(userId);
        user.setActive(active);
        return toAdminRow(appUserRepository.save(user));
    }

    private void replaceDivisions(AppUser user, String role, List<UUID> divisionIds) {
        userDivisionRepository.deleteById_UserId(user.getId());
        if (parseRole(role) != Role.DIVISION_MANAGER || divisionIds == null) {
            return;
        }
        for (UUID divisionId : divisionIds) {
            Division division = divisionRepository.findById(divisionId)
                    .orElseThrow(() -> new NoSuchElementException("Division not found: " + divisionId));
            userDivisionRepository.save(new UserDivision(new UserDivisionId(user.getId(), division.getId()), true));
        }
    }

    private UserAdminRowDto toAdminRow(AppUser user) {
        List<DivisionDto> divisions = userDivisionRepository.findById_UserId(user.getId()).stream()
                .map(ud -> divisionRepository.findById(ud.getId().getDivisionId()).orElse(null))
                .filter(d -> d != null)
                .map(bidMapper::toDto)
                .toList();
        return new UserAdminRowDto(
                user.getId(), user.getFullName(), user.getEmail(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getKeycloakUserId() != null, user.isActive(), divisions, user.getTelegramChatId());
    }

    private AppUser requireUser(UUID userId) {
        return appUserRepository.findById(userId).orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }

    private Role parseRole(String raw) {
        try {
            return Role.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new NoSuchElementException("Unknown role: " + raw);
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String[] splitFullName(String fullName) {
        String source = fullName == null ? "" : fullName.trim();
        int space = source.indexOf(' ');
        return space < 0
                ? new String[] {source, ""}
                : new String[] {source.substring(0, space), source.substring(space + 1).trim()};
    }

    private static String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
