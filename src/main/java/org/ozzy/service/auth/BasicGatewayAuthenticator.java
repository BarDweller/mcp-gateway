package org.ozzy.service.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.enterprise.context.ApplicationScoped;

import org.ozzy.model.MCPGateway;

import io.vertx.core.http.HttpServerRequest;

@ApplicationScoped
public class BasicGatewayAuthenticator implements GatewayAuthenticator {

    @Override
    public boolean supports(MCPGateway gateway) {
        return gateway != null && "BASIC".equalsIgnoreCase(gateway.getAuthType());
    }

    @Override
    public boolean authenticate(MCPGateway gateway, HttpServerRequest request) {
        if (gateway == null || request == null) {
            return false;
        }
        String expectedUser = gateway.getAuthUsername();
        String expectedPassword = gateway.getAuthPassword();
        if (expectedUser == null || expectedPassword == null) {
            return false;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }

        String token = authHeader.substring("Basic ".length()).trim();
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
        return expectedUser.equals(user) && expectedPassword.equals(password);
    }
}
