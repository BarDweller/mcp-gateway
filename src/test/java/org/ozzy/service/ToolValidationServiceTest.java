package org.ozzy.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ozzy.model.GatewayToolRef;
import org.ozzy.model.InputSchema;
import org.ozzy.model.MCPServer;
import org.ozzy.model.Tool;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ToolValidationServiceTest {

    private TestToolValidationService service;

    @BeforeEach
    void setUp() {
        service = new TestToolValidationService();
        service.setMeterRegistry(new SimpleMeterRegistry());
    }

    @Test
    void validatesMatchingToolFingerprint() {
        Tool stored = buildTool("weather", "returns weather", "string");
        Tool current = buildTool("weather", "returns weather", "string");

        MCPServer server = new MCPServer();
        server.setTools(List.of(stored));

        GatewayToolRef ref = new GatewayToolRef("server-1", "weather");
        service.setRemoteTools(List.of(current));

        boolean result = service.validateToolFingerprint("gateway-1", ref, server, "weather");
        assertTrue(result);
    }

    @Test
    void rejectsModifiedToolFingerprint() {
        Tool stored = buildTool("weather", "returns weather", "string");
        Tool current = buildTool("weather", "returns weather", "integer");

        MCPServer server = new MCPServer();
        server.setTools(List.of(stored));

        GatewayToolRef ref = new GatewayToolRef("server-1", "weather");
        service.setRemoteTools(List.of(current));

        boolean result = service.validateToolFingerprint("gateway-1", ref, server, "weather");
        assertFalse(result);
    }

    @Test
    void usesCachedValidationWithinPeriod() {
        Tool stored = buildTool("weather", "returns weather", "string");
        Tool current = buildTool("weather", "returns weather", "string");
        Tool modified = buildTool("weather", "returns weather", "integer");

        MCPServer server = new MCPServer();
        server.setTools(List.of(stored));

        GatewayToolRef ref = new GatewayToolRef("server-1", "weather");
        ref.setValidationMode("PER_TIME_PERIOD");
        ref.setValidationPeriodSeconds(3600L);

        service.setRemoteTools(List.of(current));
        assertTrue(service.validateToolFingerprint("gateway-1", ref, server, "weather"));

        service.setRemoteTools(List.of(modified));
        assertTrue(service.validateToolFingerprint("gateway-1", ref, server, "weather"));
    }

    private Tool buildTool(String name, String description, String type) {
        Tool tool = new Tool();
        tool.setName(name);
        tool.setDescription(description);

        InputSchema schema = new InputSchema();
        schema.setProperties(new HashMap<>());
        schema.setDefinitions(new HashMap<>());
        schema.setRequired(List.of("input"));

        InputSchema.Property property = new InputSchema.Property();
        property.setType(type);
        property.setDescription("input");
        schema.getProperties().put("input", property);

        tool.setInputSchema(schema);
        return tool;
    }

    private static final class TestToolValidationService extends ToolValidationService {
        private List<Tool> remoteTools;

        void setRemoteTools(List<Tool> remoteTools) {
            this.remoteTools = remoteTools;
        }

        @Override
        protected List<Tool> fetchRemoteTools(MCPServer server) {
            return remoteTools;
        }
    }
}
