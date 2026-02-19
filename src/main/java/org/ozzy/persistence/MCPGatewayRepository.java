package org.ozzy.persistence;

import java.util.Map;

import org.ozzy.model.MCPGateway;

public interface MCPGatewayRepository {
    Map<String, MCPGateway> loadAll();

    void saveAll(Map<String, MCPGateway> gateways);
}
