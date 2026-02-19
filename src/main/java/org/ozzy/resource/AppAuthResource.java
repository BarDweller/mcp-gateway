package org.ozzy.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.ozzy.dto.AppAuthSettingsDto;
import org.ozzy.dto.AppAuthUpdateDto;
import org.ozzy.model.AppAuthConfig;
import org.ozzy.service.auth.AppAuthService;

@Path("/app-auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppAuthResource {

    @Inject
    AppAuthService authService;

    @GET
    @Path("/check")
    public Response check() {
        AppAuthConfig config = authService.getConfig();
        AppAuthSettingsDto dto = new AppAuthSettingsDto();
        dto.setAuthType(config.getAuthType());
        dto.setUsername(config.getUsername());
        return Response.ok(dto).build();
    }

    @GET
    public Response getSettings() {
        AppAuthConfig config = authService.getConfig();
        AppAuthSettingsDto dto = new AppAuthSettingsDto();
        dto.setAuthType(config.getAuthType());
        dto.setUsername(config.getUsername());
        return Response.ok(dto).build();
    }

    @PUT
    public Response updateSettings(@Valid AppAuthUpdateDto update) {
        AppAuthConfig config = new AppAuthConfig();
        config.setAuthType("BASIC");
        config.setUsername(update.getUsername());
        config.setPassword(update.getPassword());
        AppAuthConfig saved = authService.updateConfig(config);

        AppAuthSettingsDto dto = new AppAuthSettingsDto();
        dto.setAuthType(saved.getAuthType());
        dto.setUsername(saved.getUsername());
        return Response.ok(dto).build();
    }
}
