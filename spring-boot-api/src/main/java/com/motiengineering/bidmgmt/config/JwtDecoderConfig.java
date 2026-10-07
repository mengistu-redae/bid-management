package com.motiengineering.bidmgmt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Built explicitly rather than via Spring Boot's single-{@code issuer-uri}
 * autoconfiguration, because Keycloak (with {@code KC_HOSTNAME_STRICT:
 * false}) stamps every token's {@code iss} claim with whatever host the
 * browser used against the authorize endpoint (the public URL), not the
 * host this API uses to reach Keycloak server-to-server (the internal,
 * docker-network URL) - see node-bff/src/auth/oidc.js's comment for the
 * full explanation (same Keycloak behavior, same fix, on both sides of this
 * stack). So one property can't serve both purposes: the JWKS fetch needs a
 * URL this container can actually reach, while the {@code iss} claim check
 * needs to match what Keycloak actually stamped.
 */
@Configuration
public class JwtDecoderConfig {

    @Bean
    @Profile("!test")
    public JwtDecoder jwtDecoder(
            @Value("${bidmgmt.keycloak.issuer-uri}") String internalIssuerUri,
            @Value("${bidmgmt.keycloak.issuer-public-uri}") String publicIssuerUri) {
        String jwkSetUri = internalIssuerUri + "/protocol/openid-connect/certs";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(publicIssuerUri));
        return decoder;
    }
}
