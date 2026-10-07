package com.motiengineering.bidmgmt.security;

import com.motiengineering.bidmgmt.domain.AppUser;
import com.motiengineering.bidmgmt.domain.UserDivision;
import com.motiengineering.bidmgmt.domain.enums.Role;
import com.motiengineering.bidmgmt.repository.AppUserRepository;
import com.motiengineering.bidmgmt.repository.UserDivisionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves (or lazily provisions) the {@link AppUser} row behind the current
 * JWT and stashes it, plus their division memberships, in
 * {@link CurrentUserContext} for the rest of the request. Runs after bearer
 * -token authentication, so the JWT's claims are already available on the
 * SecurityContext - see SecurityConfig for the filter-chain ordering.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserFilter extends OncePerRequestFilter {

    private final AppUserRepository appUserRepository;
    private final UserDivisionRepository userDivisionRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                AppUser user = resolveOrProvision(jwtAuth.getToken());
                List<UserDivision> memberships = userDivisionRepository.findById_UserId(user.getId());
                Set<java.util.UUID> divisionIds = new HashSet<>();
                Map<java.util.UUID, Boolean> managerByDivision = new HashMap<>();
                for (UserDivision membership : memberships) {
                    divisionIds.add(membership.getId().getDivisionId());
                    managerByDivision.put(membership.getId().getDivisionId(), membership.isManager());
                }
                CurrentUserContext.set(user, divisionIds, managerByDivision);
            }
            filterChain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }

    private AppUser resolveOrProvision(Jwt jwt) {
        String keycloakUserId = jwt.getSubject();

        return appUserRepository.findByKeycloakUserId(keycloakUserId)
                .orElseGet(() -> linkByEmailOrCreate(jwt));
    }

    private AppUser linkByEmailOrCreate(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email != null) {
            var existing = appUserRepository.findByEmailIgnoreCase(email);
            if (existing.isPresent() && existing.get().getKeycloakUserId() == null) {
                AppUser user = existing.get();
                user.setKeycloakUserId(jwt.getSubject());
                return appUserRepository.save(user);
            }
        }

        AppUser user = new AppUser();
        user.setKeycloakUserId(jwt.getSubject());
        user.setEmail(email);
        String name = jwt.getClaimAsString("name");
        user.setFullName(name != null ? name : (email != null ? email : jwt.getSubject()));
        user.setRole(highestRoleFrom(jwt));
        user.setActive(true);
        return appUserRepository.save(user);
    }

    @SuppressWarnings("unchecked")
    private Role highestRoleFrom(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        List<String> roles = realmAccess == null ? List.of() : (List<String>) realmAccess.getOrDefault("roles", List.of());
        if (roles.contains("director")) {
            return Role.DIRECTOR;
        }
        if (roles.contains("division_manager")) {
            return Role.DIVISION_MANAGER;
        }
        if (roles.contains("account_officer")) {
            return Role.ACCOUNT_OFFICER;
        }
        return Role.SCOUT;
    }
}
