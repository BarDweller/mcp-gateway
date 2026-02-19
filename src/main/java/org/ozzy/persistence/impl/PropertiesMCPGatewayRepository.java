package org.ozzy.persistence.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.ozzy.model.GatewayToolRef;
import org.ozzy.model.MCPGateway;
import org.ozzy.persistence.MCPGatewayRepository;

@ApplicationScoped
public class PropertiesMCPGatewayRepository extends PropertiesRepositoryBase implements MCPGatewayRepository {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public Map<String, MCPGateway> loadAll() {
        Properties properties = loadProperties();
        Map<String, MCPGateway> gateways = new HashMap<>();

        for (String key : properties.stringPropertyNames()) {
            if (!matchesPrefix(key, PREFIX_GATEWAY)) {
                continue;
            }
            String id = extractId(key, PREFIX_GATEWAY);
            if (id == null || gateways.containsKey(id)) {
                continue;
            }
            MCPGateway gateway = new MCPGateway();
            gateway.setId(id);
            gateway.setName(properties.getProperty(buildGatewayKey(id, FIELD_NAME)));
            gateway.setHost(properties.getProperty(buildGatewayKey(id, FIELD_HOST)));
            String portValue = properties.getProperty(buildGatewayKey(id, FIELD_PORT));
            if (portValue != null && !portValue.isBlank()) {
                gateway.setPort(Integer.parseInt(portValue));
            }
            gateway.setStatus(properties.getProperty(buildGatewayKey(id, FIELD_STATUS)));
            gateway.setAuthType(properties.getProperty(buildGatewayKey(id, FIELD_AUTH_TYPE)));
            gateway.setAuthUsername(properties.getProperty(buildGatewayKey(id, FIELD_AUTH_USERNAME)));
            gateway.setAuthPassword(decodeSecret(properties.getProperty(buildGatewayKey(id, FIELD_AUTH_PASSWORD))));
            gateway.setAuthToken(decodeSecret(properties.getProperty(buildGatewayKey(id, FIELD_AUTH_TOKEN))));
            gateway.setTools(readTools(properties.getProperty(buildGatewayKey(id, FIELD_TOOLS))));
            gateways.put(id, gateway);
        }

        return gateways;
    }

    @Override
    public void saveAll(Map<String, MCPGateway> gateways) {
        Properties properties = loadProperties();
        removeByPrefix(properties, PREFIX_GATEWAY);

        for (MCPGateway gateway : gateways.values()) {
            String id = gateway.getId();
            properties.setProperty(buildGatewayKey(id, FIELD_NAME), gateway.getName());
            properties.setProperty(buildGatewayKey(id, FIELD_HOST), gateway.getHost());
            properties.setProperty(buildGatewayKey(id, FIELD_PORT), String.valueOf(gateway.getPort()));
            properties.setProperty(buildGatewayKey(id, FIELD_STATUS), gateway.getStatus());
            setOptional(properties, buildGatewayKey(id, FIELD_AUTH_TYPE), gateway.getAuthType());
            setOptional(properties, buildGatewayKey(id, FIELD_AUTH_USERNAME), gateway.getAuthUsername());
            setOptional(properties, buildGatewayKey(id, FIELD_AUTH_PASSWORD), encodeSecret(gateway.getAuthPassword()));
            setOptional(properties, buildGatewayKey(id, FIELD_AUTH_TOKEN), encodeSecret(gateway.getAuthToken()));
            properties.setProperty(buildGatewayKey(id, FIELD_TOOLS), writeTools(gateway.getTools()));
        }

        saveProperties(properties);
    }

    private List<GatewayToolRef> readTools(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<GatewayToolRef>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String writeTools(List<GatewayToolRef> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(tools);
        } catch (Exception e) {
            return "";
        }
    }

}
