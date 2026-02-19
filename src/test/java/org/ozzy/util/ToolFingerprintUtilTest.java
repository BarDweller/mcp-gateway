package org.ozzy.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ozzy.model.InputSchema;
import org.ozzy.model.Tool;

class ToolFingerprintUtilTest {

    @Test
    void fingerprintMatchesForIdenticalTools() {
        Tool toolA = buildTool("tool-a", "desc", "string", List.of("input"));
        Tool toolB = buildTool("tool-a", "desc", "string", List.of("input"));

        String fingerprintA = ToolFingerprintUtil.fingerprint(toolA);
        String fingerprintB = ToolFingerprintUtil.fingerprint(toolB);

        assertEquals(fingerprintA, fingerprintB);
    }

    @Test
    void fingerprintChangesWhenDescriptionDiffers() {
        Tool toolA = buildTool("tool-a", "desc", "string", List.of("input"));
        Tool toolB = buildTool("tool-a", "desc-changed", "string", List.of("input"));

        String fingerprintA = ToolFingerprintUtil.fingerprint(toolA);
        String fingerprintB = ToolFingerprintUtil.fingerprint(toolB);

        assertNotEquals(fingerprintA, fingerprintB);
    }

    @Test
    void fingerprintChangesWhenArgsDiffers() {
        Tool toolA = buildTool("tool-a", "desc", "string", List.of("input"));
        Tool toolB = buildTool("tool-a", "desc", "integer", List.of("input"));

        String fingerprintA = ToolFingerprintUtil.fingerprint(toolA);
        String fingerprintB = ToolFingerprintUtil.fingerprint(toolB);

        assertNotEquals(fingerprintA, fingerprintB);
    }

    @Test
    void fingerprintChangesWhenRequiredDiffers() {
        Tool toolA = buildTool("tool-a", "desc", "string", List.of("input"));
        Tool toolB = buildTool("tool-a", "desc", "string", List.of());

        String fingerprintA = ToolFingerprintUtil.fingerprint(toolA);
        String fingerprintB = ToolFingerprintUtil.fingerprint(toolB);

        assertNotEquals(fingerprintA, fingerprintB);
    }

    @Test
    void fingerprintHandlesNullTool() {
        assertEquals("", ToolFingerprintUtil.fingerprint(null));
    }

    private Tool buildTool(String name, String description, String type, List<String> required) {
        Tool tool = new Tool();
        tool.setName(name);
        tool.setDescription(description);

        InputSchema schema = new InputSchema();
        schema.setProperties(new HashMap<>());
        schema.setDefinitions(new HashMap<>());
        schema.setRequired(required);

        InputSchema.Property property = new InputSchema.Property();
        property.setType(type);
        property.setDescription("input description");
        schema.getProperties().put("input", property);

        tool.setInputSchema(schema);
        return tool;
    }
}
