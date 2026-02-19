package org.ozzy.resource;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.ozzy.dto.DtoMapper;
import org.ozzy.dto.MCPGatewayDto;
import org.ozzy.model.MCPGateway;
import org.ozzy.service.MCPGatewayService;

@Path("/mcp-gateways")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MCPGatewayResource {

    private static final Logger LOG = Logger.getLogger(MCPGatewayResource.class);

    @Inject
    MCPGatewayService gatewayService;

    @GET
    public List<MCPGatewayDto> listGateways() {
        return DtoMapper.toGatewayDtos(gatewayService.listGateways());
    }

    @POST
    public Response addGateway(@Valid MCPGateway gateway) {
        LOG.debugf("Adding gateway: %s", gateway);
        MCPGateway created = gatewayService.addGateway(gateway);
        return Response.ok(DtoMapper.toGatewayDto(created)).build();
    }

    @PUT
    @Path("/{id}")
    public Response updateGateway(@PathParam("id") String id, @Valid MCPGateway updatedGateway) {
        MCPGateway updated = gatewayService.updateGateway(id, updatedGateway);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(DtoMapper.toGatewayDto(updated)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteGateway(@PathParam("id") String id) {
        MCPGateway removed = gatewayService.deleteGateway(id);
        if (removed == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/start")
    public Response startGateway(@PathParam("id") String id) {
        MCPGateway started = gatewayService.startGateway(id);
        if (started == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(DtoMapper.toGatewayDto(started)).build();
    }

    @POST
    @Path("/{id}/stop")
    public Response stopGateway(@PathParam("id") String id) {
        MCPGateway stopped = gatewayService.stopGateway(id);
        if (stopped == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(DtoMapper.toGatewayDto(stopped)).build();
    }
}
