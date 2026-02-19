package org.ozzy.persistence;

import java.util.List;

import org.ozzy.model.MCPServer;

public interface MCPServerRepository {
    List<MCPServer> loadAll();

    void saveAll(List<MCPServer> servers);
}
