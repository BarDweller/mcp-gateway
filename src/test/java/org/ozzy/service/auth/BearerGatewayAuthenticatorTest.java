package org.ozzy.service.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.ozzy.model.MCPGateway;
import org.mockito.Mockito;

import io.vertx.core.http.HttpServerRequest;

class BearerGatewayAuthenticatorTest {

    @Test
    void authenticatesWithValidToken() {
        MCPGateway gateway = new MCPGateway();
        gateway.setAuthType("BEARER");
        gateway.setAuthToken("token-123");

        HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-123");

        BearerGatewayAuthenticator authenticator = new BearerGatewayAuthenticator();
        assertTrue(authenticator.authenticate(gateway, request));
    }

    @Test
    void rejectsInvalidToken() {
        MCPGateway gateway = new MCPGateway();
        gateway.setAuthType("BEARER");
        gateway.setAuthToken("token-123");

        HttpServerRequest request = Mockito.mock(HttpServerRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer wrong");

        BearerGatewayAuthenticator authenticator = new BearerGatewayAuthenticator();
        assertFalse(authenticator.authenticate(gateway, request));
    }
}
