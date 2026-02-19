package org.ozzy.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.ozzy.model.MCPGateway;

import io.vertx.core.http.HttpServerRequest;

@ApplicationScoped
public class GatewayAuthService {

    private static final Logger LOG = Logger.getLogger(GatewayAuthService.class);

    @Inject
    jakarta.enterprise.inject.Instance<GatewayAuthenticator> authenticators;

    public boolean isAuthorized(MCPGateway gateway, HttpServerRequest request) {
        if (gateway == null) {
            return false;
        }
        String authType = gateway.getAuthType();
        if (authType == null || authType.isBlank() || "NONE".equalsIgnoreCase(authType)) {
            return true;
        }

        for (GatewayAuthenticator authenticator : authenticators) {
            if (authenticator.supports(gateway)) {
                return authenticator.authenticate(gateway, request);
            }
        }

        LOG.warnf("No authenticator available for gateway auth type %s", authType);
        return false;
    }
}
