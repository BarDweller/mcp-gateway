package org.ozzy.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.ozzy.model.InputSchema;
import org.ozzy.model.Tool;

public final class ToolFingerprintUtil {

    private ToolFingerprintUtil() {
    }

    public static String fingerprint(Tool tool) {
        if (tool == null) {
            return "";
        }
        String signature = buildSignature(tool);
        return sha256(signature);
    }

    private static String buildSignature(Tool tool) {
        String name = safe(tool.getName());
        String description = safe(tool.getDescription());
        String argsSignature = buildArgsSignature(tool.getInputSchema());
        return name + "|" + description + "|" + argsSignature;
    }

    private static String buildArgsSignature(InputSchema schema) {
        if (schema == null || schema.getProperties() == null || schema.getProperties().isEmpty()) {
            return "";
        }
        Set<String> required = schema.getRequired() == null ? Set.of() : new TreeSet<>(schema.getRequired());
        Map<String, InputSchema.Property> sorted = new TreeMap<>(schema.getProperties());
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, InputSchema.Property> entry : sorted.entrySet()) {
            String name = entry.getKey();
            InputSchema.Property property = entry.getValue();
            String type = property == null ? "" : safe(property.getType());
            String requiredFlag = required.contains(name) ? "required" : "optional";
            parts.add(name + ":" + type + ":" + requiredFlag);
        }
        parts.sort(Comparator.naturalOrder());
        return String.join(",", parts);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
