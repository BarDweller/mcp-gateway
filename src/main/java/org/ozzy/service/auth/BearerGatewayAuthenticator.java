package org.ozzy.service.auth;

import jakarta.enterprise.context.ApplicationScoped;

import org.ozzy.model.MCPGateway;

import io.vertx.core.http.HttpServerRequest;

@ApplicationScoped
public class BearerGatewayAuthenticator implements GatewayAuthenticator {

    @Override
    public boolean supports(MCPGateway gateway) {
        return gateway != null && "BEARER".equalsIgnoreCase(gateway.getAuthType());
    }

    @Override
    public boolean authenticate(MCPGateway gateway, HttpServerRequest request) {
        if (gateway == null || request == null) {
            return false;
        }
        String expectedToken = gateway.getAuthToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            return false;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        return expectedToken.equals(token);
    }
}
