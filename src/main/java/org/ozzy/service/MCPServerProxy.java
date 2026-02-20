package org.ozzy.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.ozzy.model.GatewayToolRef;
import org.ozzy.model.InputSchema;
import org.ozzy.model.MCPGateway;
import org.ozzy.model.MCPServer;
import org.ozzy.model.Tool;
import org.ozzy.persistence.MCPGatewayRepository;
import org.ozzy.persistence.MCPServerRepository;
import org.ozzy.service.auth.GatewayAuthService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.RequestOptions;

@ApplicationScoped
public class MCPServerProxy {

    private static final Logger LOG = Logger.getLogger(MCPServerProxy.class);
    private static final String MCP_PATH = "/mcp";
    private static final String JSONRPC_VERSION = "2.0";
    private static final String PROTOCOL_VERSION = "2025-11-25";
    private static final String TOOL_DISABLED_MESSAGE = "The tool is currently disabled for policy reasons. Please try again later once the issue has been resolved.";

    private final Map<String, HttpServer> runningServers = new ConcurrentHashMap<>();
    private HttpClient httpClient;

    @Inject
    Vertx vertx;

    @Inject
    MCPGatewayRepository gatewayRepository;

    @Inject
    MCPServerRepository serverRepository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    GatewayAuthService authService;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    ToolValidationService validationService;

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = vertx.createHttpClient();
        }
        return httpClient;
    }

    public boolean startGateway(MCPGateway gateway) {
        if (gateway == null || gateway.getId() == null || gateway.getId().isBlank()) {
            return false;
        }
        LOG.infof("Gateway MCP server being started for %s", gateway.getId());
        if (runningServers.containsKey(gateway.getId())) {
            return true;
        }
        LOG.infof("Gateway MCP server was not already started.. launching..");

        HttpServerOptions options = new HttpServerOptions()
                .setHost(gateway.getHost())
                .setPort(gateway.getPort());

        HttpServer server = vertx.createHttpServer(options);
        server.requestHandler(request -> {
            if (!MCP_PATH.equals(request.path())) {
                request.response().setStatusCode(404).end();
                return;
            }
            if (!HttpMethod.POST.equals(request.method())) {
                request.response().setStatusCode(405).end();
                return;
            }
            request.bodyHandler(buffer -> handleRequest(gateway.getId(), buffer.toString(), request));
        });

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        server.listen(asyncResult -> {
            if (asyncResult.succeeded()) {
                runningServers.put(gateway.getId(), server);
                LOG.infof("Gateway MCP server started for %s on %s:%d", gateway.getId(), gateway.getHost(), gateway.getPort());
                result.complete(true);
            } else {
                LOG.errorf(asyncResult.cause(), "Failed to start gateway MCP server for %s", gateway.getId());
                result.complete(false);
            }
        });

        try {
            return result.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            LOG.errorf(e, "Error starting gateway MCP server for %s", gateway.getId());
            return false;
        }
    }

    public boolean stopGateway(String gatewayId) {
        HttpServer server = runningServers.remove(gatewayId);
        if (server == null) {
            return true;
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        server.close(asyncResult -> {
            if (asyncResult.succeeded()) {
                LOG.infof("Gateway MCP server stopped for %s", gatewayId);
                result.complete(true);
            } else {
                LOG.errorf(asyncResult.cause(), "Failed to stop gateway MCP server for %s", gatewayId);
                result.complete(false);
            }
        });

        try {
            return result.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            LOG.errorf(e, "Error stopping gateway MCP server for %s", gatewayId);
            return false;
        }
    }

    private void handleRequest(String gatewayId, String body, io.vertx.core.http.HttpServerRequest request) {
        MCPGateway gateway = getGateway(gatewayId);
        if (gateway == null) {
            request.response().setStatusCode(404).end();
            return;
        }
        if (!authService.isAuthorized(gateway, request)) {
            meterRegistry.counter("mcp.gateway.auth.failure.count", "gatewayId", gatewayId).increment();
            sendUnauthorized(request);
            return;
        }
        meterRegistry.counter("mcp.gateway.auth.success.count", "gatewayId", gatewayId).increment();

        JsonNode json;
        try {
            json = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            sendError(request, null, -32700, "Parse error");
            return;
        }

        if (!json.isObject()) {
            sendError(request, null, -32600, "Invalid Request");
            return;
        }

        JsonNode methodNode = json.get("method");
        String method = methodNode != null ? methodNode.asText() : null;
        JsonNode idNode = json.get("id");

        if (method == null || method.isBlank()) {
            sendError(request, idNode, -32600, "Invalid Request");
            return;
        }

        if (idNode == null || idNode.isNull()) {
            request.response().setStatusCode(204).end();
            return;
        }

        switch (method) {
            case "initialize":
                meterRegistry.counter("mcp.gateway.request.count", "method", "initialize", "gatewayId", gatewayId).increment();
                sendJson(request, buildInitializeResponse(idNode));
                return;
            case "tools/list":
                meterRegistry.counter("mcp.gateway.request.count", "method", "tools/list", "gatewayId", gatewayId).increment();
                sendJson(request, buildToolsListResponse(gatewayId, idNode));
                return;
            case "tools/call":
                meterRegistry.counter("mcp.gateway.request.count", "method", "tools/call", "gatewayId", gatewayId).increment();
                forwardToolCall(gateway, gatewayId, json, idNode, request);
                return;
            case "ping":
                meterRegistry.counter("mcp.gateway.request.count", "method", "ping", "gatewayId", gatewayId).increment();
                sendJson(request, buildEmptyResult(idNode));
                return;
            default:
                meterRegistry.counter("mcp.gateway.request.count", "method", "unknown", "gatewayId", gatewayId).increment();
                sendError(request, idNode, -32601, "Method not found");
        }
    }

    private void forwardToolCall(MCPGateway gateway, String gatewayId, JsonNode requestJson, JsonNode idNode, io.vertx.core.http.HttpServerRequest request) {
        String toolName = requestJson.path("params").path("name").asText(null);
        if (toolName == null || toolName.isBlank()) {
            sendError(request, idNode, -32602, "Invalid params");
            return;
        }

        Optional<GatewayToolRef> refOpt = gateway.getTools().stream()
                .filter(ref -> ref != null && toolName.equals(ref.getToolName()))
                .findFirst();

        if (refOpt.isEmpty()) {
            sendError(request, idNode, -32601, "Tool not found");
            return;
        }

        GatewayToolRef ref = refOpt.get();
        MCPServer server = getServer(ref.getServerId());
        if (server == null) {
            sendError(request, idNode, -32601, "Tool server not found");
            return;
        }

        meterRegistry.counter("mcp.tool.invocation.count",
            "gatewayId", gatewayId,
            "serverId", String.valueOf(ref.getServerId()),
            "tool", toolName).increment();

        String url = buildServerUrl(server);
        if (url == null) {
            sendError(request, idNode, -32601, "Tool server endpoint not configured");
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(requestJson);
        } catch (JsonProcessingException e) {
            sendError(request, idNode, -32603, "Internal error");
            return;
        }

        String acceptHeader = normalizeAcceptHeader(request.getHeader("Accept"));

        //LOG.infof("Proxy sending request - %s  :: ACCEPT %s", payload, acceptHeader);

        BackendTarget target = buildBackendTarget(server);
        if (target == null) {
            sendError(request, idNode, -32601, "Tool server endpoint not configured");
            return;
        }

        validateToolFingerprintAsync(gatewayId, ref, server, toolName, isValid -> {
            if (!isValid) {
                sendToolDisabledResult(request, idNode);
                return;
            }
            forwardToBackend(target, payload, acceptHeader, toolName, idNode, request);
        });
    }

    private void forwardToBackend(BackendTarget target, String payload, String acceptHeader, String toolName,
                                  JsonNode idNode, io.vertx.core.http.HttpServerRequest request) {
        RequestOptions options = new RequestOptions()
            .setHost(target.host)
            .setPort(target.port)
            .setSsl(target.ssl)
            .setMethod(HttpMethod.POST)
            .setTimeout(Duration.ofSeconds(30).toMillis())
            .setURI(target.path);

        getHttpClient().request(options).onComplete(backendResult -> {
            if (backendResult.failed()) {
                LOG.errorf(backendResult.cause(), "Tool call proxy failed for %s", toolName);
                sendError(request, idNode, -32603, "Tool call failed");
                return;
            }

            HttpClientRequest backendRequest = backendResult.result();
            backendRequest.putHeader("Content-Type", "application/json");
            backendRequest.putHeader("Accept", acceptHeader);
            applyServerHeaders(target, backendRequest);

            backendRequest.send(payload).onComplete(responseResult -> {
                if (responseResult.failed()) {
                    LOG.errorf(responseResult.cause(), "Tool call proxy failed for %s", toolName);
                    sendError(request, idNode, -32603, "Tool call failed");
                    return;
                }

                HttpClientResponse backendResponse = responseResult.result();
                String contentType = backendResponse.getHeader("content-type");
                request.response().setChunked(true);
                if (contentType != null && !contentType.isBlank()) {
                    request.response().putHeader("Content-Type", contentType);
                }
                request.response().setStatusCode(backendResponse.statusCode());

                backendResponse.handler(buffer -> request.response().write(buffer));
                backendResponse.endHandler(done -> request.response().end());
                backendResponse.exceptionHandler(error -> {
                    LOG.errorf(error, "Tool call proxy stream failed for %s", toolName);
                    if (!request.response().ended()) {
                        request.response().end();
                    }
                });
            });
        });
    }

    private void applyServerHeaders(BackendTarget target, HttpClientRequest backendRequest) {
        MCPServer server = getServer(target.serverId);
        if (server == null) {
            return;
        }

        String authType = server.getAuthorizationType();
        if (authType != null && authType.equalsIgnoreCase("BASIC")) {
            String username = server.getAuthUsername();
            String password = server.getAuthPassword();
            if (username != null && password != null) {
                String token = java.util.Base64.getEncoder().encodeToString((username + ":" + password)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                backendRequest.putHeader("Authorization", "Basic " + token);
            }
        } else if (authType != null && authType.equalsIgnoreCase("BEARER")) {
            String token = server.getAuthToken();
            if (token != null && !token.isBlank()) {
                backendRequest.putHeader("Authorization", "Bearer " + token);
            }
        } else if (authType != null && authType.equalsIgnoreCase("OAUTH")) {
            String token = server.getOauthAccessToken();
            if (token != null && !token.isBlank()) {
                backendRequest.putHeader("Authorization", "Bearer " + token);
            }
        }

        if (server.getHeaders() != null) {
            server.getHeaders().forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    return;
                }
                if ("authorization".equalsIgnoreCase(key)) {
                    LOG.warnf("Ignoring custom Authorization header for server %s; use auth type instead.", server.getId());
                    return;
                }
                backendRequest.putHeader(key, value);
            });
        }
    }

    private void validateToolFingerprintAsync(String gatewayId, GatewayToolRef ref, MCPServer server, String toolName,
                                              java.util.function.Consumer<Boolean> callback) {
        CompletableFuture.supplyAsync(() -> validationService.validateToolFingerprint(gatewayId, ref, server, toolName))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        LOG.errorf(error, "Tool fingerprint validation failed for %s", toolName);
                        callback.accept(false);
                        return;
                    }
                    callback.accept(Boolean.TRUE.equals(result));
                });
    }


    private BackendTarget buildBackendTarget(MCPServer server) {
        if (server.getHost() == null || server.getPort() <= 0) {
            return null;
        }
        boolean ssl = server.getProtocol() != null && server.getProtocol().equalsIgnoreCase("HTTPS");
        String path = server.getRemotePath();
        if (path == null || path.isBlank()) {
            path = MCP_PATH;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return new BackendTarget(server.getId(), server.getHost(), server.getPort(), ssl, path);
    }

    private static final class BackendTarget {
        private final String serverId;
        private final String host;
        private final int port;
        private final boolean ssl;
        private final String path;

        private BackendTarget(String serverId, String host, int port, boolean ssl, String path) {
            this.serverId = serverId;
            this.host = host;
            this.port = port;
            this.ssl = ssl;
            this.path = path;
        }
    }

    private String normalizeAcceptHeader(String acceptHeader) {
        String requiredJson = "application/json";
        String requiredStream = "text/event-stream";
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return requiredJson + ", " + requiredStream;
        }
        String lower = acceptHeader.toLowerCase();
        if (lower.contains("*/*")) {
            return requiredJson + ", " + requiredStream;
        }

        boolean acceptsJson = lower.contains(requiredJson);
        boolean acceptsStream = lower.contains(requiredStream);
        if (!acceptsJson && !acceptsStream) {
            return requiredJson + ", " + requiredStream;
        }

        StringBuilder builder = new StringBuilder();
        if (acceptsJson) {
            builder.append(requiredJson);
        }
        if (acceptsStream) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(requiredStream);
        }
        return builder.toString();
    }

    private ObjectNode buildInitializeResponse(JsonNode idNode) {
        ObjectNode result = objectMapper.createObjectNode();
        ObjectNode capabilities = objectMapper.createObjectNode();
        ObjectNode tools = objectMapper.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);

        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "MCPGateway");
        serverInfo.put("version", "1.0.0");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("capabilities", capabilities);
        payload.put("protocolVersion", PROTOCOL_VERSION);
        payload.set("serverInfo", serverInfo);

        result.put("jsonrpc", JSONRPC_VERSION);
        result.set("id", idNode);
        result.set("result", payload);
        return result;
    }

    private ObjectNode buildToolsListResponse(String gatewayId, JsonNode idNode) {
        List<Tool> tools = resolveGatewayTools(gatewayId);
        ArrayNode toolArray = objectMapper.createArrayNode();
        for (Tool tool : tools) {
            if (tool == null) {
                continue;
            }
            toolArray.add(toolToJson(tool));
        }

        ObjectNode resultPayload = objectMapper.createObjectNode();
        resultPayload.set("tools", toolArray);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("jsonrpc", JSONRPC_VERSION);
        result.set("id", idNode);
        result.set("result", resultPayload);
        return result;
    }

    private ObjectNode buildEmptyResult(JsonNode idNode) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("jsonrpc", JSONRPC_VERSION);
        result.set("id", idNode);
        result.set("result", objectMapper.createObjectNode());
        return result;
    }

    private ObjectNode toolToJson(Tool tool) {
        ObjectNode toolNode = objectMapper.createObjectNode();
        toolNode.put("name", tool.getName());
        if (tool.getDescription() != null) {
            toolNode.put("description", tool.getDescription());
        }

        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        InputSchema schema = tool.getInputSchema();
        if (schema != null && schema.getProperties() != null) {
            schema.getProperties().forEach((propName, propSchema) -> {
                ObjectNode propNode = objectMapper.createObjectNode();
                if (propSchema != null) {
                    if (propSchema.getType() != null) {
                        propNode.put("type", propSchema.getType());
                    }
                    if (propSchema.getDescription() != null) {
                        propNode.put("description", propSchema.getDescription());
                    }
                }
                properties.set(propName, propNode);
            });
        }
        inputSchema.set("properties", properties);

        ArrayNode required = objectMapper.createArrayNode();
        if (schema != null && schema.getRequired() != null) {
            schema.getRequired().forEach(required::add);
        }
        if (required.size() > 0) {
            inputSchema.set("required", required);
        }

        toolNode.set("inputSchema", inputSchema);
        return toolNode;
    }

    private List<Tool> resolveGatewayTools(String gatewayId) {
        MCPGateway gateway = getGateway(gatewayId);
        if (gateway == null || gateway.getTools() == null) {
            return List.of();
        }

        List<MCPServer> servers = serverRepository.loadAll();
        List<Tool> results = new ArrayList<>();
        for (GatewayToolRef ref : gateway.getTools()) {
            if (ref == null) {
                continue;
            }
            MCPServer server = servers.stream()
                    .filter(item -> item != null && ref.getServerId() != null && ref.getServerId().equals(item.getId()))
                    .findFirst()
                    .orElse(null);
            if (server == null || server.getTools() == null) {
                continue;
            }
            server.getTools().stream()
                    .filter(tool -> tool != null && ref.getToolName() != null && ref.getToolName().equals(tool.getName()))
                    .findFirst()
                    .ifPresent(results::add);
        }

        return results;
    }

    private MCPGateway getGateway(String gatewayId) {
        Map<String, MCPGateway> gateways = gatewayRepository.loadAll();
        return gateways.get(gatewayId);
    }

    private MCPServer getServer(String serverId) {
        return serverRepository.loadAll().stream()
                .filter(server -> server != null && serverId != null && serverId.equals(server.getId()))
                .findFirst()
                .orElse(null);
    }

    private String buildServerUrl(MCPServer server) {
        if (server.getHost() == null || server.getPort() <= 0) {
            return null;
        }
        String protocol = server.getProtocol() != null && server.getProtocol().equalsIgnoreCase("HTTPS") ? "https" : "http";
        String path = server.getRemotePath();
        if (path == null || path.isBlank()) {
            path = MCP_PATH;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return protocol + "://" + server.getHost() + ":" + server.getPort() + path;
    }

    private void sendJson(io.vertx.core.http.HttpServerRequest request, ObjectNode response) {
        request.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(200)
                .end(response.toString());
    }

    private void sendError(io.vertx.core.http.HttpServerRequest request, JsonNode idNode, int code, String message) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jsonrpc", JSONRPC_VERSION);
        if (idNode != null && !idNode.isNull()) {
            payload.set("id", idNode);
        }
        payload.set("error", error);

        request.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(200)
                .end(payload.toString());
    }

    private void sendUnauthorized(io.vertx.core.http.HttpServerRequest request) {
        request.response()
                .setStatusCode(401)
                .putHeader("WWW-Authenticate", "Basic realm=\"MCPGateway\"")
                .end();
    }

    private void sendToolDisabledResult(io.vertx.core.http.HttpServerRequest request, JsonNode idNode) {
        meterRegistry.counter("mcp.tool.disabled.count").increment();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("jsonrpc", JSONRPC_VERSION);
        if (idNode != null && !idNode.isNull()) {
            payload.set("id", idNode);
        }
        payload.put("result", TOOL_DISABLED_MESSAGE);

        request.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(200)
                .end(payload.toString());
    }

}
