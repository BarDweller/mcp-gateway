package org.ozzy.persistence.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ozzy.TestConfigProfile;
import org.ozzy.model.MCPGateway;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TestConfigProfile.class)
class PropertiesMCPGatewayRepositoryTest {

    @Inject
    PropertiesMCPGatewayRepository repository;

    @BeforeEach
    void cleanProperties() throws Exception {
        Files.deleteIfExists(Path.of(TestConfigProfile.TEST_PROPERTIES_PATH));
    }

    @Test
    void saveAndLoadGateways() {
        MCPGateway gatewayOne = new MCPGateway();
        gatewayOne.setId("g-1");
        gatewayOne.setName("Gateway One");
        gatewayOne.setHost("localhost");
        gatewayOne.setPort(8888);
        gatewayOne.setStatus("STOPPED");

        MCPGateway gatewayTwo = new MCPGateway();
        gatewayTwo.setId("g-2");
        gatewayTwo.setName("Gateway Two");
        gatewayTwo.setHost("127.0.0.1");
        gatewayTwo.setPort(9999);
        gatewayTwo.setStatus("STARTED");

        Map<String, MCPGateway> gateways = new HashMap<>();
        gateways.put(gatewayOne.getId(), gatewayOne);
        gateways.put(gatewayTwo.getId(), gatewayTwo);

        repository.saveAll(gateways);

        Map<String, MCPGateway> loaded = repository.loadAll();
        assertThat(loaded, hasKey("g-1"));
        assertThat(loaded, hasKey("g-2"));
        assertThat(loaded.get("g-1").getName(), equalTo("Gateway One"));
        assertThat(loaded.get("g-2").getStatus(), equalTo("STARTED"));
        assertThat(loaded.keySet(), containsInAnyOrder("g-1", "g-2"));
    }
}
