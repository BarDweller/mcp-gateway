package org.ozzy.service.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.enterprise.context.ApplicationScoped;

import org.ozzy.model.AppAuthConfig;

@ApplicationScoped
public class BasicManagementAuthProvider implements ManagementAuthProvider {

    @Override
    public boolean supports(AppAuthConfig config) {
        return config != null && "BASIC".equalsIgnoreCase(config.getAuthType());
    }

    @Override
    public boolean authenticate(AppAuthConfig config, String authorizationHeader) {
        if (config == null || authorizationHeader == null || !authorizationHeader.startsWith("Basic ")) {
            return false;
        }
        String token = authorizationHeader.substring("Basic ".length()).trim();
        if (token.isBlank()) {
            return false;
        }
        String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
        int delimiter = decoded.indexOf(':');
        if (delimiter < 0) {
            return false;
        }
        String user = decoded.substring(0, delimiter);
        String password = decoded.substring(delimiter + 1);
        return safeEquals(config.getUsername(), user) && safeEquals(config.getPassword(), password);
    }

    private boolean safeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return expected.equals(actual);
    }
}
