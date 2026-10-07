package com.motiengineering.bidmgmt.keycloakadmin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Logs in as the Keycloak admin (master realm's built-in admin-cli client,
 * Resource Owner Password Credentials grant) - the same flow an admin would
 * use signing into the admin console by hand, just from the app. Reuses the
 * KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD credentials the keycloak compose
 * service itself is seeded with.
 */
@Service
public class KeycloakAdminTokenProvider {

    private final RestClient restClient;
    private final String adminUsername;
    private final String adminPassword;

    public KeycloakAdminTokenProvider(
            @Value("${bidmgmt.keycloak-admin.base-url}") String baseUrl,
            @Value("${bidmgmt.keycloak-admin.admin-username}") String adminUsername,
            @Value("${bidmgmt.keycloak-admin.admin-password}") String adminPassword) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public String fetchToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", "admin-cli");
        form.add("username", adminUsername);
        form.add("password", adminPassword);
        form.add("grant_type", "password");

        Map<String, Object> tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri("/realms/master/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new KeycloakAdminException("Could not authenticate as the Keycloak admin", e);
        }

        Object accessToken = tokenResponse != null ? tokenResponse.get("access_token") : null;
        if (accessToken == null) {
            throw new KeycloakAdminException("Keycloak admin login response had no access_token");
        }
        return accessToken.toString();
    }
}
