package org.ozzy.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ozzy.model.GatewayToolRef;
import org.ozzy.model.InputSchema;
import org.ozzy.model.MCPGateway;
import org.ozzy.model.MCPServer;
import org.ozzy.model.Tool;

public final class DtoMapper {

    private DtoMapper() {
    }

    public static MCPGatewayDto toGatewayDto(MCPGateway gateway) {
        if (gateway == null) {
            return null;
        }
        MCPGatewayDto dto = new MCPGatewayDto();
        dto.setId(gateway.getId());
        dto.setName(gateway.getName());
        dto.setStatus(gateway.getStatus());
        dto.setPort(gateway.getPort());
        dto.setHost(gateway.getHost());
        dto.setAuthType(gateway.getAuthType());
        dto.setAuthUsername(gateway.getAuthUsername());
        dto.setAuthPassword(gateway.getAuthPassword());
        dto.setAuthToken(gateway.getAuthToken());
        dto.setTools(toGatewayToolRefs(gateway.getTools()));
        return dto;
    }

    public static List<MCPGatewayDto> toGatewayDtos(Collection<MCPGateway> gateways) {
        List<MCPGatewayDto> results = new ArrayList<>();
        if (gateways == null) {
            return results;
        }
        for (MCPGateway gateway : gateways) {
            results.add(toGatewayDto(gateway));
        }
        return results;
    }

    private static List<GatewayToolRefDto> toGatewayToolRefs(Collection<GatewayToolRef> refs) {
        List<GatewayToolRefDto> results = new ArrayList<>();
        if (refs == null) {
            return results;
        }
        for (GatewayToolRef ref : refs) {
            GatewayToolRefDto dto = new GatewayToolRefDto();
            dto.setServerId(ref.getServerId());
            dto.setToolName(ref.getToolName());
            dto.setValidationMode(ref.getValidationMode());
            dto.setValidationPeriodSeconds(ref.getValidationPeriodSeconds());
            results.add(dto);
        }
        return results;
    }

    public static MCPServerDto toServerDto(MCPServer server) {
        if (server == null) {
            return null;
        }
        MCPServerDto dto = new MCPServerDto();
        dto.setId(server.getId());
        dto.setName(server.getName());
        dto.setHost(server.getHost());
        dto.setPort(server.getPort());
        dto.setAuthorizationType(server.getAuthorizationType());
        dto.setClientCert(server.getClientCert());
        dto.setAuthUsername(server.getAuthUsername());
        dto.setAuthPassword(server.getAuthPassword());
        dto.setAuthToken(server.getAuthToken());
        dto.setType(server.getType());
        dto.setPath(server.getPath());
        dto.setRemotePath(server.getRemotePath());
        dto.setProtocol(server.getProtocol());
        dto.setArgument(server.getArgument());
        dto.setCertificate(server.getCertificate());
        dto.setHeaders(server.getHeaders());
        dto.setOauthAccessToken(server.getOauthAccessToken());
        dto.setOauthRefreshToken(server.getOauthRefreshToken());
        dto.setTools(toToolDtos(server.getTools()));
        return dto;
    }

    public static List<MCPServerDto> toServerDtos(Collection<MCPServer> servers) {
        List<MCPServerDto> results = new ArrayList<>();
        if (servers == null) {
            return results;
        }
        for (MCPServer server : servers) {
            results.add(toServerDto(server));
        }
        return results;
    }

    public static ToolDto toToolDto(Tool tool) {
        if (tool == null) {
            return null;
        }
        ToolDto dto = new ToolDto();
        dto.setName(tool.getName());
        dto.setTitle(tool.getTitle());
        dto.setDescription(tool.getDescription());
        dto.setInputSchema(toInputSchemaDto(tool.getInputSchema()));
        dto.setValidationStatus(tool.getValidationStatus());
        dto.setLastValidatedAt(tool.getLastValidatedAt());
        dto.setFirstFailedAt(tool.getFirstFailedAt());
        return dto;
    }

    public static List<ToolDto> toToolDtos(Collection<Tool> tools) {
        List<ToolDto> results = new ArrayList<>();
        if (tools == null) {
            return results;
        }
        for (Tool tool : tools) {
            results.add(toToolDto(tool));
        }
        return results;
    }

    private static InputSchemaDto toInputSchemaDto(InputSchema schema) {
        if (schema == null) {
            return null;
        }
        InputSchemaDto dto = new InputSchemaDto();
        dto.setType(schema.getType());
        dto.setRequired(schema.getRequired());
        dto.setProperties(toPropertyMap(schema.getProperties()));
        dto.setDefinitions(toPropertyMap(schema.getDefinitions()));
        return dto;
    }

    private static Map<String, InputSchemaPropertyDto> toPropertyMap(Map<String, InputSchema.Property> source) {
        if (source == null) {
            return null;
        }
        Map<String, InputSchemaPropertyDto> mapped = new HashMap<>();
        for (Map.Entry<String, InputSchema.Property> entry : source.entrySet()) {
            InputSchema.Property property = entry.getValue();
            if (property == null) {
                mapped.put(entry.getKey(), null);
                continue;
            }
            InputSchemaPropertyDto dto = new InputSchemaPropertyDto();
            dto.setType(property.getType());
            dto.setDescription(property.getDescription());
            mapped.put(entry.getKey(), dto);
        }
        return mapped;
    }
}
