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
import org.ozzy.dto.MCPServerDto;
import org.ozzy.dto.ToolComparisonDto;
import org.ozzy.dto.ToolComparisonItemDto;
import org.ozzy.dto.ToolFieldDiffDto;
import org.ozzy.dto.ToolDto;
import org.ozzy.model.MCPServer;
import org.ozzy.model.Tool;
import org.ozzy.service.MCPServerService;
import org.ozzy.service.ToolComparisonResult;

@Path("/mcp-servers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MCPServerResource {

    private static final Logger LOG = Logger.getLogger(MCPServerResource.class);

    @Inject
    MCPServerService serverService;

    @GET
    public List<MCPServerDto> listServers() {
        return DtoMapper.toServerDtos(serverService.listServers());
    }

    @POST
    public Response addServer(@Valid MCPServer server) {
        LOG.debugf("POST Received server: %s", server);

        if ("remote".equalsIgnoreCase(server.getType())) {
            if (server.getName() == null || server.getName().isEmpty() ||
                server.getHost() == null || server.getHost().isEmpty() ||
                server.getPort() <= 0) {
                LOG.warnf("Invalid server data: %s", server);
                return Response.status(Response.Status.BAD_REQUEST).entity("Invalid server data").build();
            }

            if (server.getCertificate() != null) {
                if (!server.getCertificate().contains("-----BEGIN CERTIFICATE-----") ||
                    !server.getCertificate().contains("-----END CERTIFICATE-----")) {
                    LOG.warnf("Invalid certificate data for server %s", server.getName());
                    return Response.status(Response.Status.BAD_REQUEST).entity("Invalid certificate data").build();
                }
            }
        }

        MCPServer created = serverService.addServer(server);
        return Response.status(Response.Status.CREATED).entity(DtoMapper.toServerDto(created)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteServer(@PathParam("id") String id) {
        MCPServer removed = serverService.deleteServer(id);
        if (removed == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/tools")
    public Response listTools(@PathParam("id") String id) {
        List<Tool> tools = serverService.listTools(id);
        if (tools == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        List<ToolDto> toolDtos = DtoMapper.toToolDtos(tools);
        return Response.ok(toolDtos).build();
    }

    @POST
    @Path("/{id}/tools/read")
    public Response readTools(@PathParam("id") String id) {
        List<Tool> tools = serverService.readTools(id);
        if (tools == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(DtoMapper.toToolDtos(tools)).build();
    }

    @POST
    @Path("/{id}/tools/compare")
    public Response compareTools(@PathParam("id") String id) {
        ToolComparisonResult comparison = serverService.compareTools(id);
        if (comparison == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        ToolComparisonDto dto = new ToolComparisonDto();
        dto.setMatch(comparison.isMatch());
        for (ToolComparisonResult.ToolComparisonItem item : comparison.getTools()) {
            ToolComparisonItemDto itemDto = new ToolComparisonItemDto();
            itemDto.setName(item.getName());
            itemDto.setMatch(item.isMatch());
            itemDto.setStored(DtoMapper.toToolDto(item.getStored()));
            itemDto.setCurrent(DtoMapper.toToolDto(item.getCurrent()));
            for (ToolComparisonResult.ToolFieldDiff diff : item.getDiffs()) {
                itemDto.addDiff(new ToolFieldDiffDto(diff.getField(), diff.getOldValue(), diff.getNewValue()));
            }
            dto.addTool(itemDto);
        }

        return Response.ok(dto).build();
    }

    @POST
    @Path("/{id}/tools/approve")
    public Response approveTools(@PathParam("id") String id) {
        List<Tool> tools = serverService.approveTools(id);
        if (tools == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(DtoMapper.toToolDtos(tools)).build();
    }

    @PUT
    @Path("/{id}")
    public Response updateServer(@PathParam("id") String id, @Valid MCPServer updatedServer) {
        MCPServer updated = serverService.updateServer(id, updatedServer);
        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(DtoMapper.toServerDto(updated)).build();
    }
}
