package org.ozzy.service.auth;

import org.ozzy.model.MCPGateway;

import io.vertx.core.http.HttpServerRequest;

public interface GatewayAuthenticator {

    boolean supports(MCPGateway gateway);

    boolean authenticate(MCPGateway gateway, HttpServerRequest request);
}
