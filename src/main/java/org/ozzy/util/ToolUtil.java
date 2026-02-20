package org.ozzy.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.ozzy.model.InputSchema;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolExecutor;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.service.tool.ToolExecutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.jboss.logging.Logger;

public class ToolUtil {
    private static final Logger LOG = Logger.getLogger(ToolUtil.class);
    private static final String PROTOCOL_VERSION = "2025-11-25";
    private static final java.util.concurrent.ConcurrentHashMap<String, String> SESSION_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private String getType(JsonSchemaElement element) {
        if (element instanceof JsonAnyOfSchema) {
            return "AnyOf";
        }
        if (element instanceof JsonArraySchema) {
            return "Array";
        }
        if (element instanceof JsonBooleanSchema) {
            return "Boolean";
        }
        if (element instanceof JsonEnumSchema) {
            return "Enum";
        }
        if (element instanceof JsonIntegerSchema) {
            return "Integer";
        }
        if (element instanceof JsonNullSchema) {
            return "Null";
        }
        if (element instanceof JsonNumberSchema) {
            return "Number";
        }
        if (element instanceof JsonObjectSchema) {
            return "Object";
        }
        if (element instanceof JsonRawSchema) {
            return "Raw";
        }
        if (element instanceof JsonReferenceSchema) {
            return "Reference";
        }
        if (element instanceof JsonStringSchema) {
            return "String";
        }
        return null;
    }

    public ArrayList<org.ozzy.model.Tool> getTools(String url){
        return getTools(url, java.util.Collections.emptyMap());
    }

    public ArrayList<org.ozzy.model.Tool> getTools(String url, Map<String, String> headers){
        LOG.infof("Connecting to MCP server at: %s", url);

        if (headers != null && !headers.isEmpty()) {
            return fetchToolsViaHttp(url, headers);
        }

        try(McpTransport transport = new StreamableHttpMcpTransport.Builder()
            .url(url)
            .logRequests(true)
            .logResponses(true)
            .build();){

            try(McpClient mcpClient = new DefaultMcpClient.Builder()
                .key("MCPGateway")
                .autoHealthCheck(Boolean.FALSE)
                .transport(transport)
                .cacheToolList(false)
                .build();){


                LOG.infof("Asking for tools");
                
                Map<ToolSpecification, ToolExecutor> tools = mcpClient.listTools().stream().collect(Collectors.toMap(
                        tool -> tool, 
                        tool -> new McpToolExecutor(mcpClient)
                ));

                LOG.infof(tools.toString());


                LOG.infof("iterating tools");

                List<ToolSpecification> toolspecs = mcpClient.listTools();

                LOG.debugf("We foulnd %d tools", toolspecs.size());


                ArrayList<org.ozzy.model.Tool> toolz = new ArrayList<>();
                for(ToolSpecification tool : toolspecs){
                    LOG.debugf("Discovered tool: %s - %s", tool.name(), tool.description());
                    org.ozzy.model.Tool t = new org.ozzy.model.Tool();
                    t.setName(tool.name());
                    t.setDescription(tool.description());

                    InputSchema inputSchema = new InputSchema();
                    inputSchema.setProperties(new HashMap<>());
                    inputSchema.setDefinitions(new HashMap<>());
                    inputSchema.setRequired(new ArrayList<>());

                    JsonObjectSchema params = tool.parameters();
                    LOG.debugf("Tool params schema: %s", params);
                    params.properties().forEach((propName, propSchema) -> {
                        LOG.debugf("Property: %s(%s) - %s", propName,getType(propSchema), propSchema.description());
                        InputSchema.Property property = new InputSchema.Property();
                        property.setType(getType(propSchema));
                        property.setDescription(propSchema.description());
                        inputSchema.getProperties().put(propName, property);
                    });
                    params.definitions().forEach((defName, defSchema) -> {
                        LOG.debugf("Definition: %s - %s", defName, defSchema);
                        InputSchema.Property property = new InputSchema.Property();
                        property.setType(getType(defSchema));
                        property.setDescription(defSchema.description());
                        inputSchema.getDefinitions().put(defName, property);
                    });
                    params.required().forEach(req -> {
                        LOG.debugf("Required: %s", req);
                        inputSchema.getRequired().add(req);
                    });
                    t.setInputSchema(inputSchema);
                    toolz.add(t);
                }
                return toolz;
            }catch(Exception e){
                LOG.error("Error communicating with MCP server", e);
            }  
        }catch(IOException e){
            LOG.error("Error setting up transport to MCP server", e);
        } 
        return null;
    }

    private ArrayList<org.ozzy.model.Tool> fetchToolsViaHttp(String url, Map<String, String> headers) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode payload = mapper.createObjectNode();
            payload.put("jsonrpc", "2.0");
            payload.put("id", 1);
            payload.put("method", "tools/list");
            payload.set("params", mapper.createObjectNode());

            LOG.debugf("Tool list request -> %s", url);
            LOG.debugf("Tool list request headers: Content-Type=application/json, Accept=application/json, text/event-stream");
            LOG.debugf("Tool list request header MCP-Protocol-Version: %s", PROTOCOL_VERSION);
            LOG.debugf("Tool list request payload: %s", payload.toString());

            String sessionKey = buildSessionKey(url, headers);
            String sessionId = SESSION_CACHE.get(sessionKey);
            HttpResponse<String> response = sendJsonRpc(url, headers, sessionId, payload);

            if (response.statusCode() == 400 && requiresSession(response.body())) {
                LOG.debug("Tool list requires MCP session; initializing.");
                String newSessionId = initializeSession(url, headers);
                if (newSessionId != null && !newSessionId.isBlank()) {
                    SESSION_CACHE.put(sessionKey, newSessionId);
                    response = sendJsonRpc(url, headers, newSessionId, payload);
                }
            }

            LOG.debugf("Tool list response status: %d", response.statusCode());
            response.headers().map().forEach((key, values) ->
                    LOG.debugf("Tool list response header %s: %s", key, values));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warnf("Tool list response body: %s", response.body());
                LOG.warnf("Tool list request failed: %s", response.statusCode());
                return null;
            }

            JsonNode root = parseResponseBody(mapper, response);
            if (root == null) {
                LOG.warn("Tool list response could not be parsed.");
                return null;
            }
            JsonNode toolsNode = root.path("result").path("tools");
            if (!toolsNode.isArray()) {
                return null;
            }

            ArrayList<org.ozzy.model.Tool> toolz = new ArrayList<>();
            for (JsonNode toolNode : (ArrayNode) toolsNode) {
                org.ozzy.model.Tool tool = new org.ozzy.model.Tool();
                tool.setName(toolNode.path("name").asText(null));
                tool.setTitle(toolNode.path("title").asText(null));
                tool.setDescription(toolNode.path("description").asText(null));
                JsonNode inputSchemaNode = toolNode.get("inputSchema");
                if (inputSchemaNode != null && inputSchemaNode.isObject()) {
                    InputSchema schema = mapper.treeToValue(inputSchemaNode, InputSchema.class);
                    tool.setInputSchema(schema);
                }
                toolz.add(tool);
            }
            return toolz;
        } catch (Exception e) {
            LOG.error("Error fetching tool list with headers", e);
            return null;
        }
    }

    private JsonNode parseResponseBody(ObjectMapper mapper, HttpResponse<String> response) throws Exception {
        String contentType = response.headers().firstValue("content-type").orElse("");
        String body = response.body();
        if (contentType.toLowerCase().contains("text/event-stream")) {
            String json = extractFirstEventData(body);
            if (json == null || json.isBlank()) {
                return null;
            }
            return mapper.readTree(json);
        }
        return mapper.readTree(body);
    }

    private String extractFirstEventData(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        StringBuilder data = new StringBuilder();
        String[] lines = body.split("\\r?\\n");
        for (String line : lines) {
            if (line.startsWith("data:")) {
                data.append(line.substring(5).trim());
            } else if (line.isBlank() && data.length() > 0) {
                break;
            }
        }
        return data.toString();
    }

    private HttpResponse<String> sendJsonRpc(String url, Map<String, String> headers, String sessionId, ObjectNode payload)
            throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

        if (sessionId != null && !sessionId.isBlank()) {
            requestBuilder.header("Mcp-Session-Id", sessionId);
            LOG.debugf("Tool list request header Mcp-Session-Id: %s", sessionId);
        }

        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && value != null) {
                    requestBuilder.header(key, value);
                    LOG.debugf("Tool list request header %s: %s", key, maskHeaderValue(key, value));
                }
            });
        }

        HttpClient client = HttpClient.newHttpClient();
        return client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private boolean requiresSession(String body) {
        if (body == null) {
            return false;
        }
        return body.contains("Mcp-Session-Id") && body.toLowerCase().contains("required");
    }

    private String initializeSession(String url, Map<String, String> headers) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode payload = mapper.createObjectNode();
            payload.put("jsonrpc", "2.0");
            payload.put("id", 1);
            payload.put("method", "initialize");

            ObjectNode params = mapper.createObjectNode();
            params.put("protocolVersion", PROTOCOL_VERSION);
            ObjectNode capabilities = mapper.createObjectNode();
            ObjectNode tools = mapper.createObjectNode();
            tools.put("listChanged", false);
            capabilities.set("tools", tools);
            params.set("capabilities", capabilities);

            ObjectNode clientInfo = mapper.createObjectNode();
            clientInfo.put("name", "MCPGateway");
            clientInfo.put("version", "1.0.0");
            params.set("clientInfo", clientInfo);
            payload.set("params", params);

            LOG.debugf("Initialize request payload: %s", payload.toString());
            HttpResponse<String> response = sendJsonRpc(url, headers, null, payload);
            LOG.debugf("Initialize response status: %d", response.statusCode());
            response.headers().map().forEach((key, values) ->
                    LOG.debugf("Initialize response header %s: %s", key, values));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warnf("Initialize response body: %s", response.body());
                return null;
            }
            return response.headers().firstValue("Mcp-Session-Id").orElse(null);
        } catch (Exception e) {
            LOG.warn("Initialize request failed", e);
            return null;
        }
    }

    private String buildSessionKey(String url, Map<String, String> headers) {
        String auth = "";
        if (headers != null) {
            String headerValue = headers.get("Authorization");
            if (headerValue == null) {
                headerValue = headers.get("authorization");
            }
            if (headerValue != null) {
                auth = headerValue;
            }
        }
        return url + "|" + auth;
    }

    private String maskHeaderValue(String key, String value) {
        if (key == null || value == null) {
            return value;
        }
        if (!"authorization".equalsIgnoreCase(key)) {
            return value;
        }
        String trimmed = value.trim();
        int lastSpace = trimmed.indexOf(' ');
        String scheme = lastSpace > 0 ? trimmed.substring(0, lastSpace) : "";
        String token = lastSpace > 0 ? trimmed.substring(lastSpace + 1) : trimmed;
        if (token.length() <= 6) {
            return scheme + " ****";
        }
        String masked = token.substring(0, 2) + "***" + token.substring(token.length() - 4);
        return scheme.isBlank() ? masked : scheme + " " + masked;
    }
}
