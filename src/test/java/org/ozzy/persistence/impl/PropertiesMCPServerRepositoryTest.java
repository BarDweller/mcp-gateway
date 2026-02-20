package org.ozzy.persistence.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ozzy.TestConfigProfile;
import org.ozzy.model.MCPServer;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TestConfigProfile.class)
class PropertiesMCPServerRepositoryTest {

    @Inject
    PropertiesMCPServerRepository repository;

    @BeforeEach
    void cleanProperties() throws Exception {
        Files.deleteIfExists(Path.of(TestConfigProfile.TEST_PROPERTIES_PATH));
    }

    @Test
    void saveAndLoadServers() {
        MCPServer server = new MCPServer();
        server.setName("Server One");
        server.setHost("localhost");
        server.setPort(8080);
        server.setType("remote");
        server.setProtocol("HTTP");
        server.setRemotePath("/mcp");
        server.setAuthorizationType("None");
        server.setCertificate("-----BEGIN CERTIFICATE-----\nTEST\n-----END CERTIFICATE-----");
        server.setOauthClientId("client-123");

        repository.saveAll(List.of(server));

        List<MCPServer> loaded = repository.loadAll();
        assertThat(loaded, hasSize(1));
        assertThat(loaded.get(0).getName(), equalTo("Server One"));
        assertThat(loaded.get(0).getPort(), equalTo(8080));
        assertThat(loaded.get(0).getAuthorizationType(), equalTo("None"));
        assertThat(loaded.get(0).getCertificate(), equalTo("-----BEGIN CERTIFICATE-----\nTEST\n-----END CERTIFICATE-----"));
        assertThat(loaded.get(0).getOauthClientId(), equalTo("client-123"));
    }
}
