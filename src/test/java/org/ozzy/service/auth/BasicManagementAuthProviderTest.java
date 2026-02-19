package org.ozzy.service.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.ozzy.model.AppAuthConfig;

class BasicManagementAuthProviderTest {

    @Test
    void authenticatesValidCredentials() {
        AppAuthConfig config = new AppAuthConfig();
        config.setAuthType("BASIC");
        config.setUsername("admin");
        config.setPassword("admin");

        String token = Base64.getEncoder().encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));
        String header = "Basic " + token;

        BasicManagementAuthProvider provider = new BasicManagementAuthProvider();
        assertTrue(provider.authenticate(config, header));
    }

    @Test
    void rejectsInvalidCredentials() {
        AppAuthConfig config = new AppAuthConfig();
        config.setAuthType("BASIC");
        config.setUsername("admin");
        config.setPassword("admin");

        String token = Base64.getEncoder().encodeToString("admin:wrong".getBytes(StandardCharsets.UTF_8));
        String header = "Basic " + token;

        BasicManagementAuthProvider provider = new BasicManagementAuthProvider();
        assertFalse(provider.authenticate(config, header));
    }
}
