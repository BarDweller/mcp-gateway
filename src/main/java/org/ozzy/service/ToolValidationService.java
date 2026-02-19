package org.ozzy.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.ozzy.model.GatewayToolRef;
import org.ozzy.model.MCPServer;
import org.ozzy.model.Tool;
import org.ozzy.util.ToolFingerprintUtil;
import org.ozzy.util.ToolUtil;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@ApplicationScoped
public class ToolValidationService {

    private static final Logger LOG = Logger.getLogger(ToolValidationService.class);
    private static final String VALIDATION_PER_INVOCATION = "PER_INVOCATION";
    private static final String VALIDATION_PER_TIME_PERIOD = "PER_TIME_PERIOD";
    private static final long DEFAULT_VALIDATION_PERIOD_SECONDS = 300;

    private final Map<String, ValidationState> validationStates = new ConcurrentHashMap<>();

    @Inject
    MeterRegistry meterRegistry;

    public boolean validateToolFingerprint(String gatewayId, GatewayToolRef ref, MCPServer server, String toolName) {
        ValidationPolicy policy = resolveValidationPolicy(ref);
        String key = buildValidationKey(gatewayId, ref);
        ValidationState state = validationStates.get(key);
        long now = System.currentTimeMillis();

        if (!VALIDATION_PER_INVOCATION.equals(policy.mode)) {
            if (state != null && (now - state.lastValidatedAt) < policy.periodMillis) {
                meterRegistry.counter("mcp.tool.validation.cached.count",
                        "gatewayId", gatewayId,
                        "serverId", String.valueOf(ref.getServerId()),
                        "tool", toolName,
                        "result", Boolean.toString(state.lastSuccess)).increment();
                return state.lastSuccess;
            }
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        boolean valid = validateToolFingerprintNow(server, toolName);
        sample.stop(meterRegistry.timer("mcp.tool.validation.duration",
                "gatewayId", gatewayId,
                "serverId", String.valueOf(ref.getServerId()),
                "tool", toolName,
                "result", Boolean.toString(valid)));

        meterRegistry.counter("mcp.tool.validation.count",
                "gatewayId", gatewayId,
                "serverId", String.valueOf(ref.getServerId()),
                "tool", toolName,
                "result", Boolean.toString(valid)).increment();

        validationStates.put(key, new ValidationState(now, valid));
        return valid;
    }

    ValidationPolicy resolveValidationPolicy(GatewayToolRef ref) {
        String mode = normalizeValidationMode(ref == null ? null : ref.getValidationMode());
        long periodSeconds = ref != null && ref.getValidationPeriodSeconds() != null
                ? ref.getValidationPeriodSeconds()
                : DEFAULT_VALIDATION_PERIOD_SECONDS;
        if (periodSeconds <= 0) {
            periodSeconds = DEFAULT_VALIDATION_PERIOD_SECONDS;
        }
        return new ValidationPolicy(mode, Duration.ofSeconds(periodSeconds).toMillis());
    }

    String buildValidationKey(String gatewayId, GatewayToolRef ref) {
        String serverId = ref == null ? "" : String.valueOf(ref.getServerId());
        String toolName = ref == null ? "" : String.valueOf(ref.getToolName());
        return gatewayId + "|" + serverId + "|" + toolName;
    }

    boolean validateToolFingerprintNow(MCPServer server, String toolName) {
        Tool stored = resolveStoredTool(server, toolName);
        if (stored == null) {
            LOG.warnf("No stored tool definition found for %s", toolName);
            return false;
        }

        List<Tool> currentTools = fetchRemoteTools(server);
        if (currentTools == null || currentTools.isEmpty()) {
            LOG.warnf("No tools returned during validation for %s", toolName);
            return false;
        }

        Tool current = currentTools.stream()
                .filter(tool -> tool != null && toolName.equals(tool.getName()))
                .findFirst()
                .orElse(null);
        if (current == null) {
            LOG.warnf("Current tool definition missing for %s", toolName);
            return false;
        }

        String storedFingerprint = ToolFingerprintUtil.fingerprint(stored);
        String currentFingerprint = ToolFingerprintUtil.fingerprint(current);
        boolean match = storedFingerprint.equals(currentFingerprint);
        if (!match) {
            LOG.warnf("Fingerprint validation failed for tool %s", toolName);
        }
        return match;
    }

    Tool resolveStoredTool(MCPServer server, String toolName) {
        if (server == null || server.getTools() == null || toolName == null) {
            return null;
        }
        return server.getTools().stream()
                .filter(tool -> tool != null && toolName.equals(tool.getName()))
                .findFirst()
                .orElse(null);
    }

    protected List<Tool> fetchRemoteTools(MCPServer server) {
        String url = buildServerUrl(server);
        if (url == null) {
            return null;
        }
        ToolUtil toolUtil = new ToolUtil();
        return toolUtil.getTools(url, buildServerHeaders(server));
    }

    private java.util.Map<String, String> buildServerHeaders(MCPServer server) {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        if (server == null) {
            return headers;
        }
        String authType = server.getAuthorizationType();
        if (authType != null && authType.equalsIgnoreCase("BASIC")) {
            String username = server.getAuthUsername();
            String password = server.getAuthPassword();
            if (username != null && password != null) {
                String token = java.util.Base64.getEncoder().encodeToString((username + ":" + password)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + token);
            }
        } else if (authType != null && authType.equalsIgnoreCase("BEARER")) {
            String token = server.getAuthToken();
            if (token != null && !token.isBlank()) {
                headers.put("Authorization", "Bearer " + token);
            }
        }

        if (server.getHeaders() != null) {
            server.getHeaders().forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    return;
                }
                if ("authorization".equalsIgnoreCase(key)) {
                    return;
                }
                headers.put(key, value);
            });
        }
        return headers;
    }

    String buildServerUrl(MCPServer server) {
        if (server.getHost() == null || server.getPort() <= 0) {
            return null;
        }
        String protocol = server.getProtocol() != null && server.getProtocol().equalsIgnoreCase("HTTPS") ? "https" : "http";
        String path = server.getRemotePath();
        if (path == null || path.isBlank()) {
            path = "/mcp";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return protocol + "://" + server.getHost() + ":" + server.getPort() + path;
    }

    void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private String normalizeValidationMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return VALIDATION_PER_INVOCATION;
        }
        if (VALIDATION_PER_TIME_PERIOD.equalsIgnoreCase(mode)) {
            return VALIDATION_PER_TIME_PERIOD;
        }
        if (VALIDATION_PER_INVOCATION.equalsIgnoreCase(mode)) {
            return VALIDATION_PER_INVOCATION;
        }
        return VALIDATION_PER_INVOCATION;
    }

    static final class ValidationState {
        private final long lastValidatedAt;
        private final boolean lastSuccess;

        private ValidationState(long lastValidatedAt, boolean lastSuccess) {
            this.lastValidatedAt = lastValidatedAt;
            this.lastSuccess = lastSuccess;
        }
    }

    static final class ValidationPolicy {
        private final String mode;
        private final long periodMillis;

        private ValidationPolicy(String mode, long periodMillis) {
            this.mode = mode;
            this.periodMillis = periodMillis;
        }
    }
}
