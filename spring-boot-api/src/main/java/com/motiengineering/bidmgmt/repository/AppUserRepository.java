package com.motiengineering.bidmgmt.repository;

import com.motiengineering.bidmgmt.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByKeycloakUserId(String keycloakUserId);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    List<AppUser> findByFullNameIgnoreCase(String fullName);

    List<AppUser> findByActiveTrue();
}
