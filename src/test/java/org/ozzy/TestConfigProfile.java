package org.ozzy;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class TestConfigProfile implements QuarkusTestProfile {

    public static final String TEST_PROPERTIES_PATH = "target/test-config.properties";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("mcp.properties.path", TEST_PROPERTIES_PATH);
    }
}
