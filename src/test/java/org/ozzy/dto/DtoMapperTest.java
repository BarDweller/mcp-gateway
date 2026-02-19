package org.ozzy.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ozzy.model.GatewayToolRef;
import org.ozzy.model.MCPGateway;

class DtoMapperTest {

    @Test
    void mapsGatewayToolValidationSettings() {
        GatewayToolRef ref = new GatewayToolRef();
        ref.setServerId("server-1");
        ref.setToolName("tool-1");
        ref.setValidationMode("PER_TIME_PERIOD");
        ref.setValidationPeriodSeconds(1800L);

        MCPGateway gateway = new MCPGateway();
        gateway.setId("gateway-1");
        gateway.setName("Gateway");
        gateway.setStatus("STOPPED");
        gateway.setHost("localhost");
        gateway.setPort(9000);
        gateway.setAuthType("BASIC");
        gateway.setAuthUsername("admin");
        gateway.setAuthPassword("secret");
        gateway.setTools(List.of(ref));

        MCPGatewayDto dto = DtoMapper.toGatewayDto(gateway);

        assertNotNull(dto);
        assertEquals(1, dto.getTools().size());
        GatewayToolRefDto toolDto = dto.getTools().get(0);
        assertEquals("server-1", toolDto.getServerId());
        assertEquals("tool-1", toolDto.getToolName());
        assertEquals("PER_TIME_PERIOD", toolDto.getValidationMode());
        assertEquals(1800L, toolDto.getValidationPeriodSeconds());
        assertEquals("BASIC", dto.getAuthType());
        assertEquals("admin", dto.getAuthUsername());
        assertEquals("secret", dto.getAuthPassword());
    }
}
