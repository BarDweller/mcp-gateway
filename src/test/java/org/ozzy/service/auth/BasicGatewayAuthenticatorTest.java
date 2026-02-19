package org.ozzy.service.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.ozzy.model.MCPGateway;
import org.mockito.Mockito;

import io.vertx.core.http.HttpServerRequest;

class BasicGatewayAuthenticatorTest {

    @Test
    void authenticatesWithValidCredentials() {
        MCPGateway gateway = new MCPGateway();
        gateway.setAuthType("BASIC");
        gateway.setAuthUsername("user");
        gateway.setAuthPassword("pass");

        HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
        String token = Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        when(request.getHeader("Authorization")).thenReturn("Basic " + token);

        BasicGatewayAuthenticator authenticator = new BasicGatewayAuthenticator();
        assertTrue(authenticator.authenticate(gateway, request));
    }

    @Test
    void rejectsInvalidCredentials() {
        MCPGateway gateway = new MCPGateway();
        gateway.setAuthType("BASIC");
        gateway.setAuthUsername("user");
        gateway.setAuthPassword("pass");

        HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
        String token = Base64.getEncoder().encodeToString("user:wrong".getBytes(StandardCharsets.UTF_8));
        when(request.getHeader("Authorization")).thenReturn("Basic " + token);

        BasicGatewayAuthenticator authenticator = new BasicGatewayAuthenticator();
        assertFalse(authenticator.authenticate(gateway, request));
    }
}
