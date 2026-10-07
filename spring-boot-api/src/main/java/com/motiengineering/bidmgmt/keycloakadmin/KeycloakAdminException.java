package com.motiengineering.bidmgmt.keycloakadmin;

public class KeycloakAdminException extends RuntimeException {
    public KeycloakAdminException(String message) {
        super(message);
    }

    public KeycloakAdminException(String message, Throwable cause) {
        super(message, cause);
    }
}
