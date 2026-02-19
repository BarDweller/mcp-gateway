package org.ozzy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.ozzy.model.MCPGateway;
import org.ozzy.model.GatewayToolRef;
import org.ozzy.model.MCPServer;
import org.ozzy.model.Tool;
import org.ozzy.persistence.MCPGatewayRepository;
import org.ozzy.persistence.MCPServerRepository;

import io.micrometer.core.instrument.MeterRegistry;

@ApplicationScoped
public class MCPGatewayService {

    private static final Logger LOG = Logger.getLogger(MCPGatewayService.class);

    private final Map<String, MCPGateway> gateways = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Inject
    MCPGatewayRepository gatewayRepository;

    @Inject
    MCPServerRepository serverRepository;

    @Inject
    MCPServerProxy serverProxy;

    @Inject
    MeterRegistry meterRegistry;

    @PostConstruct
    void init() {
        Map<String, MCPGateway> loaded = gatewayRepository.loadAll();
        lock.writeLock().lock();
        try {
            gateways.clear();
            gateways.putAll(loaded);
            removeInvalidToolRefs();
            for (MCPGateway gateway : gateways.values()) {
                if (gateway != null && "STARTED".equalsIgnoreCase(gateway.getStatus())) {
                    boolean started = serverProxy.startGateway(gateway);
                    if (!started) {
                        gateway.setStatus("STOPPED");
                    }
                }
            }
            gatewayRepository.saveAll(gateways);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<MCPGateway> listGateways() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(gateways.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public MCPGateway addGateway(MCPGateway gateway) {
        lock.writeLock().lock();
        try {
            gateways.put(gateway.getId(), gateway);
            gatewayRepository.saveAll(gateways);
            LOG.debugf("Added gateway %s", gateway.getId());
            meterRegistry.counter("mcp.gateway.create.count").increment();
            return gateway;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public MCPGateway updateGateway(String id, MCPGateway updatedGateway) {
        lock.writeLock().lock();
        try {
            MCPGateway existing = gateways.get(id);
            if (existing == null) {
                return null;
            }
            if (updatedGateway.getName() != null) {
                existing.setName(updatedGateway.getName());
            }
            if (updatedGateway.getHost() != null) {
                existing.setHost(updatedGateway.getHost());
            }
            if (updatedGateway.getPort() > 0) {
                existing.setPort(updatedGateway.getPort());
            }
            if (updatedGateway.getStatus() != null) {
                existing.setStatus(updatedGateway.getStatus());
            }
            if (updatedGateway.getTools() != null) {
                existing.setTools(updatedGateway.getTools());
            }
            gatewayRepository.saveAll(gateways);
            LOG.debugf("Updated gateway %s", id);
            meterRegistry.counter("mcp.gateway.update.count").increment();
            return existing;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public MCPGateway deleteGateway(String id) {
        lock.writeLock().lock();
        try {
            MCPGateway removed = gateways.remove(id);
            if (removed != null) {
                if ("STARTED".equalsIgnoreCase(removed.getStatus())) {
                    serverProxy.stopGateway(removed.getId());
                }
                gatewayRepository.saveAll(gateways);
                LOG.debugf("Deleted gateway %s", id);
                meterRegistry.counter("mcp.gateway.delete.count").increment();
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public MCPGateway startGateway(String id) {
        lock.writeLock().lock();
        try {
            MCPGateway gateway = gateways.get(id);
            if (gateway == null) {
                return null;
            }
            if ("STARTED".equalsIgnoreCase(gateway.getStatus())) {
                return gateway;
            }
            boolean started = serverProxy.startGateway(gateway);
            if (!started) {
                LOG.warnf("Failed to start MCP gateway server for %s", id);
                meterRegistry.counter("mcp.gateway.start.failure.count").increment();
                return null;
            }
            gateway.setStatus("STARTED");
            gatewayRepository.saveAll(gateways);
            LOG.debugf("Updated gateway %s status to STARTED", id);
            meterRegistry.counter("mcp.gateway.start.count").increment();
            return gateway;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public MCPGateway stopGateway(String id) {
        lock.writeLock().lock();
        try {
            MCPGateway gateway = gateways.get(id);
            if (gateway == null) {
                return null;
            }
            serverProxy.stopGateway(gateway.getId());
            gateway.setStatus("STOPPED");
            gatewayRepository.saveAll(gateways);
            LOG.debugf("Updated gateway %s status to STOPPED", id);
            meterRegistry.counter("mcp.gateway.stop.count").increment();
            return gateway;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int removeToolsForServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return 0;
        }
        lock.writeLock().lock();
        try {
            int removedCount = 0;
            boolean changed = false;
            for (MCPGateway gateway : gateways.values()) {
                List<GatewayToolRef> tools = gateway.getTools();
                if (tools == null || tools.isEmpty()) {
                    continue;
                }
                int before = tools.size();
                List<GatewayToolRef> filtered = tools.stream()
                    .filter(ref -> ref != null && !serverId.equals(ref.getServerId()))
                    .collect(Collectors.toList());
                if (filtered.size() != before) {
                    gateway.setTools(filtered);
                    removedCount += before - filtered.size();
                    changed = true;
                }
            }
            if (changed) {
                gatewayRepository.saveAll(gateways);
                LOG.debugf("Removed %d gateway tool references for server %s", removedCount, serverId);
                meterRegistry.counter("mcp.gateway.toolrefs.removed.count").increment(removedCount);
            }
            return removedCount;
        } finally {
            lock.writeLock().unlock();
        }
    }



    private void removeInvalidToolRefs() {
        List<MCPServer> servers = serverRepository.loadAll();
        Map<String, Set<String>> toolsByServerId = new HashMap<>();
        for (MCPServer server : servers) {
            if (server == null || server.getId() == null || server.getId().isBlank()) {
                continue;
            }
            List<Tool> tools = server.getTools();
            Set<String> toolNames = new HashSet<>();
            if (tools != null) {
                for (Tool tool : tools) {
                    if (tool != null && tool.getName() != null && !tool.getName().isBlank()) {
                        toolNames.add(tool.getName());
                    }
                }
            }
            toolsByServerId.put(server.getId(), toolNames);
        }

        boolean changed = false;
        int removed = 0;
        for (MCPGateway gateway : gateways.values()) {
            List<GatewayToolRef> tools = gateway.getTools();
            if (tools == null || tools.isEmpty()) {
                continue;
            }
            int before = tools.size();
            List<GatewayToolRef> filtered = tools.stream()
                .filter(ref -> ref != null && isValidToolRef(ref, toolsByServerId))
                .collect(Collectors.toList());
            if (filtered.size() != before) {
                gateway.setTools(filtered);
                removed += before - filtered.size();
                changed = true;
            }
        }

        if (changed) {
            gatewayRepository.saveAll(gateways);
            LOG.warnf("Removed %d invalid gateway tool references during startup", removed);
            meterRegistry.counter("mcp.gateway.toolrefs.invalid.removed.count").increment(removed);
        }
    }

    private boolean isValidToolRef(GatewayToolRef ref, Map<String, Set<String>> toolsByServerId) {
        String serverId = ref.getServerId();
        String toolName = ref.getToolName();
        if (serverId == null || serverId.isBlank() || toolName == null || toolName.isBlank()) {
            return false;
        }
        Set<String> tools = toolsByServerId.get(serverId);
        return tools != null && tools.contains(toolName);
    }
}
