package org.ozzy.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.ozzy.model.MCPServer;
import org.ozzy.model.InputSchema;
import org.ozzy.model.Tool;
import org.ozzy.persistence.MCPServerRepository;
import org.ozzy.util.ToolUtil;

import io.micrometer.core.instrument.MeterRegistry;

@ApplicationScoped
public class MCPServerService {

    private static final Logger LOG = Logger.getLogger(MCPServerService.class);

    private final List<MCPServer> servers = new ArrayList<>();
    private final Object lock = new Object();

    @Inject
    MCPServerRepository serverRepository;

    @Inject
    MCPGatewayService gatewayService;

    @Inject
    MeterRegistry meterRegistry;

    @PostConstruct
    void init() {
        List<MCPServer> loaded = serverRepository.loadAll();
        synchronized (lock) {
            servers.clear();
            servers.addAll(loaded);
        }
    }

    public List<MCPServer> listServers() {
        synchronized (lock) {
            return new ArrayList<>(servers);
        }
    }

    public MCPServer addServer(MCPServer server) {
        synchronized (lock) {
            if (server.getId() == null || server.getId().isBlank()) {
                server.setId(java.util.UUID.randomUUID().toString());
            }
            servers.add(server);
            serverRepository.saveAll(servers);
            LOG.debugf("Added server %s", server.getName());
            meterRegistry.counter("mcp.server.create.count").increment();
            return server;
        }
    }

    public MCPServer deleteServer(String id) {
        synchronized (lock) {
            int index = findIndexById(id);
            if (index < 0) {
                return null;
            }
            MCPServer removed = servers.remove(index);
            serverRepository.saveAll(servers);
            if (gatewayService != null) {
                gatewayService.removeToolsForServer(id);
            }
            LOG.debugf("Deleted server %s", id);
            meterRegistry.counter("mcp.server.delete.count").increment();
            return removed;
        }
    }

    public MCPServer updateServer(String id, MCPServer updatedServer) {
        synchronized (lock) {
            int index = findIndexById(id);
            if (index < 0) {
                return null;
            }
            if (updatedServer.getId() == null || !updatedServer.getId().equals(id)) {
                updatedServer.setId(id);
            }
            servers.set(index, updatedServer);
            serverRepository.saveAll(servers);
            LOG.debugf("Updated server %s", id);
            meterRegistry.counter("mcp.server.update.count").increment();
            return updatedServer;
        }
    }

    public List<Tool> listTools(String id) {
        synchronized (lock) {
            MCPServer server = getServer(id);
            if (server == null) {
                return null;
            }
            if (server.getTools() == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(server.getTools());
        }
    }

    public List<Tool> readTools(String id) {
        synchronized (lock) {
            MCPServer server = getServer(id);
            if (server == null) {
                return null;
            }
            List<Tool> tools = fetchRemoteTools(server);
            if (tools != null) {
                markNeverValidated(tools);
                server.setTools(tools);
                serverRepository.saveAll(servers);
                meterRegistry.counter("mcp.server.tools.read.count").increment();
                return new ArrayList<>(tools);
            }
            return Collections.emptyList();
        }
    }

    public ToolComparisonResult compareTools(String id) {
        synchronized (lock) {
            MCPServer server = getServer(id);
            if (server == null) {
                return null;
            }
            List<Tool> stored = server.getTools() == null ? Collections.emptyList() : server.getTools();
            List<Tool> current = fetchRemoteTools(server);
            if (current == null) {
                current = Collections.emptyList();
            }

            ToolComparisonResult result = new ToolComparisonResult();
            Map<String, Tool> storedByName = toToolMap(stored);
            Map<String, Tool> currentByName = toToolMap(current);
            Set<String> allNames = new TreeSet<>();
            allNames.addAll(storedByName.keySet());
            allNames.addAll(currentByName.keySet());

            boolean allMatch = true;
            long now = System.currentTimeMillis();
            for (String name : allNames) {
                ToolComparisonResult.ToolComparisonItem item = new ToolComparisonResult.ToolComparisonItem();
                item.setName(name);
                Tool storedTool = storedByName.get(name);
                Tool currentTool = currentByName.get(name);
                item.setStored(storedTool);
                item.setCurrent(currentTool);

                boolean match = true;
                if (storedTool == null || currentTool == null) {
                    match = false;
                    item.addDiff(new ToolComparisonResult.ToolFieldDiff(
                            "tool",
                            storedTool == null ? "(missing)" : "present",
                            currentTool == null ? "(missing)" : "present"));
                } else {
                    String storedDesc = safe(storedTool.getDescription());
                    String currentDesc = safe(currentTool.getDescription());
                    if (!storedDesc.equals(currentDesc)) {
                        match = false;
                        item.addDiff(new ToolComparisonResult.ToolFieldDiff("description", storedDesc, currentDesc));
                    }

                    String storedArgs = buildArgsSignature(storedTool.getInputSchema());
                    String currentArgs = buildArgsSignature(currentTool.getInputSchema());
                    if (!storedArgs.equals(currentArgs)) {
                        match = false;
                        item.addDiff(new ToolComparisonResult.ToolFieldDiff("args", storedArgs, currentArgs));
                    }
                }

                item.setMatch(match);
                result.addTool(item);
                if (!match) {
                    allMatch = false;
                }

                applyValidationStatus(storedTool, match, now);
                applyValidationStatus(currentTool, match, now);
            }

            result.setMatch(allMatch);

            server.setTools(allMatch ? current : stored);
            serverRepository.saveAll(servers);

            meterRegistry.counter("mcp.server.tools.compare.count", "match", Boolean.toString(allMatch)).increment();

            return result;
        }
    }

    public List<Tool> approveTools(String id) {
        synchronized (lock) {
            MCPServer server = getServer(id);
            if (server == null) {
                return null;
            }
            List<Tool> tools = fetchRemoteTools(server);
            if (tools != null) {
                long now = System.currentTimeMillis();
                tools.forEach(tool -> applyValidationStatus(tool, true, now));
                server.setTools(tools);
                serverRepository.saveAll(servers);
                meterRegistry.counter("mcp.server.tools.approve.count").increment();
                return new ArrayList<>(tools);
            }
            return Collections.emptyList();
        }
    }

    public MCPServer getServer(String id) {
        synchronized (lock) {
            int index = findIndexById(id);
            return index < 0 ? null : servers.get(index);
        }
    }

    private int findIndexById(String id) {
        if (id == null || id.isBlank()) {
            return -1;
        }
        for (int i = 0; i < servers.size(); i++) {
            MCPServer server = servers.get(i);
            if (server != null && id.equals(server.getId())) {
                return i;
            }
        }
        return -1;
    }

    private List<Tool> fetchRemoteTools(MCPServer server) {
        if (server.getProtocol() == null) {
            return Collections.emptyList();
        }
        String scheme = "HTTP".equalsIgnoreCase(server.getProtocol()) ? "http" : "https";
        String path = server.getRemotePath();
        if (path == null || path.isBlank()) {
            path = "/mcp";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String url = scheme + "://" + server.getHost() + ":" + server.getPort() + path;
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

    private Map<String, Tool> toToolMap(List<Tool> tools) {
        Map<String, Tool> map = new HashMap<>();
        if (tools == null) {
            return map;
        }
        for (Tool tool : tools) {
            if (tool == null || tool.getName() == null) {
                continue;
            }
            map.put(tool.getName(), tool);
        }
        return map;
    }

    private String buildArgsSignature(InputSchema schema) {
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void applyValidationStatus(Tool tool, boolean match, long now) {
        if (tool == null) {
            return;
        }
        tool.setValidationStatus(match ? "match" : "mismatch");
        tool.setLastValidatedAt(now);
        if (match) {
            tool.setFirstFailedAt(null);
        } else if (tool.getFirstFailedAt() == null) {
            tool.setFirstFailedAt(now);
        }
    }

    private void markNeverValidated(List<Tool> tools) {
        if (tools == null) {
            return;
        }
        for (Tool tool : tools) {
            if (tool == null) {
                continue;
            }
            tool.setValidationStatus("unknown");
            tool.setLastValidatedAt(null);
            tool.setFirstFailedAt(null);
        }
    }
}
