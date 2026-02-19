package org.ozzy.resource;

import java.io.IOException;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.ozzy.service.auth.AppAuthService;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class ManagementAuthFilter implements ContainerRequestFilter {

    @Inject
    AppAuthService authService;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (!requiresAuth(path)) {
            return;
        }

        String authHeader = requestContext.getHeaderString("Authorization");
        if (authHeader == null || !authService.authenticate(authHeader)) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity("Unauthorized")
                    .build());
        }
    }

    private boolean requiresAuth(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("mcp-gateways")
                || path.startsWith("mcp-servers")
                || path.startsWith("certificate")
                || path.startsWith("app-auth");
    }
}
