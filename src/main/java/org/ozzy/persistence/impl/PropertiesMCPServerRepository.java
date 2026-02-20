package org.ozzy.persistence.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.ozzy.model.MCPServer;
import org.ozzy.model.Tool;
import org.ozzy.persistence.MCPServerRepository;

@ApplicationScoped
public class PropertiesMCPServerRepository extends PropertiesRepositoryBase implements MCPServerRepository {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public List<MCPServer> loadAll() {
        Properties properties = loadProperties();
        List<MCPServer> servers = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!matchesPrefix(key, PREFIX_SERVER)) {
                continue;
            }
            String id = extractId(key, PREFIX_SERVER);
            if (id != null) {
                ids.add(id);
            }
        }

        for (String id : ids) {
            MCPServer server = new MCPServer();
            server.setId(id);
            server.setName(properties.getProperty(buildServerKey(id, FIELD_NAME)));
            server.setHost(properties.getProperty(buildServerKey(id, FIELD_HOST)));
            String portValue = properties.getProperty(buildServerKey(id, FIELD_PORT));
            if (portValue != null && !portValue.isBlank()) {
                server.setPort(Integer.parseInt(portValue));
            }
            server.setAuthorizationType(properties.getProperty(buildServerKey(id, FIELD_AUTHORIZATION_TYPE)));
            server.setType(properties.getProperty(buildServerKey(id, FIELD_TYPE)));
            server.setPath(properties.getProperty(buildServerKey(id, FIELD_PATH)));
            server.setRemotePath(properties.getProperty(buildServerKey(id, FIELD_REMOTE_PATH)));
            server.setProtocol(properties.getProperty(buildServerKey(id, FIELD_PROTOCOL)));
            server.setArgument(properties.getProperty(buildServerKey(id, FIELD_ARGUMENT)));
            server.setClientCert(properties.getProperty(buildServerKey(id, FIELD_CLIENT_CERT)));
            server.setCertificate(decodeSecret(properties.getProperty(buildServerKey(id, FIELD_CERTIFICATE))));
            server.setAuthUsername(properties.getProperty(buildServerKey(id, FIELD_AUTH_USERNAME)));
            server.setAuthPassword(decodeSecret(properties.getProperty(buildServerKey(id, FIELD_AUTH_PASSWORD))));
            server.setAuthToken(decodeSecret(properties.getProperty(buildServerKey(id, FIELD_AUTH_TOKEN))));
            server.setHeaders(readHeaders(properties.getProperty(buildServerKey(id, FIELD_HEADERS))));
            server.setOauthClientId(properties.getProperty(buildServerKey(id, FIELD_OAUTH_CLIENT_ID)));
            server.setOauthAccessToken(decodeSecret(properties.getProperty(buildServerKey(id, FIELD_OAUTH_ACCESS_TOKEN))));
            server.setOauthRefreshToken(decodeSecret(properties.getProperty(buildServerKey(id, FIELD_OAUTH_REFRESH_TOKEN))));
            server.setTools(readTools(properties.getProperty(buildServerKey(id, FIELD_TOOLS))));
            servers.add(server);
        }

        return servers;
    }

    @Override
    public void saveAll(List<MCPServer> servers) {
        Properties properties = loadProperties();
        removeByPrefix(properties, PREFIX_SERVER);

        for (MCPServer server : servers) {
            String id = server.getId();
            if (id == null || id.isBlank()) {
                id = java.util.UUID.randomUUID().toString();
                server.setId(id);
            }
            properties.setProperty(buildServerKey(id, FIELD_NAME), server.getName());
            properties.setProperty(buildServerKey(id, FIELD_HOST), server.getHost());
            properties.setProperty(buildServerKey(id, FIELD_PORT), String.valueOf(server.getPort()));
            setOptional(properties, buildServerKey(id, FIELD_AUTHORIZATION_TYPE), server.getAuthorizationType());
            properties.setProperty(buildServerKey(id, FIELD_TYPE), server.getType());
            setOptional(properties, buildServerKey(id, FIELD_PATH), server.getPath());
            properties.setProperty(buildServerKey(id, FIELD_REMOTE_PATH), server.getRemotePath());
            properties.setProperty(buildServerKey(id, FIELD_PROTOCOL), server.getProtocol());
            setOptional(properties, buildServerKey(id, FIELD_ARGUMENT), server.getArgument());
            setOptional(properties, buildServerKey(id, FIELD_CLIENT_CERT), server.getClientCert());
            setOptional(properties, buildServerKey(id, FIELD_CERTIFICATE), encodeSecret(server.getCertificate()));
            setOptional(properties, buildServerKey(id, FIELD_AUTH_USERNAME), server.getAuthUsername());
            setOptional(properties, buildServerKey(id, FIELD_AUTH_PASSWORD), encodeSecret(server.getAuthPassword()));
            setOptional(properties, buildServerKey(id, FIELD_AUTH_TOKEN), encodeSecret(server.getAuthToken()));
            setOptional(properties, buildServerKey(id, FIELD_HEADERS), writeHeaders(server.getHeaders()));
            setOptional(properties, buildServerKey(id, FIELD_OAUTH_CLIENT_ID), server.getOauthClientId());
            setOptional(properties, buildServerKey(id, FIELD_OAUTH_ACCESS_TOKEN), encodeSecret(server.getOauthAccessToken()));
            setOptional(properties, buildServerKey(id, FIELD_OAUTH_REFRESH_TOKEN), encodeSecret(server.getOauthRefreshToken()));
            properties.setProperty(buildServerKey(id, FIELD_TOOLS), writeTools(server.getTools()));
        }

        saveProperties(properties);
    }

    private List<Tool> readTools(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<List<Tool>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String writeTools(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(tools);
        } catch (Exception e) {
            return "";
        }
    }

    private java.util.Map<String, String> readHeaders(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return java.util.Collections.emptyMap();
        }
        try {
            java.util.Map<String, String> raw = objectMapper.readValue(rawJson, new TypeReference<java.util.Map<String, String>>() {});
            java.util.Map<String, String> decoded = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, String> entry : raw.entrySet()) {
                decoded.put(entry.getKey(), decodeSecret(entry.getValue()));
            }
            return decoded;
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    private String writeHeaders(java.util.Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        try {
            java.util.Map<String, String> encoded = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                encoded.put(entry.getKey(), encodeSecret(entry.getValue()));
            }
            return objectMapper.writeValueAsString(encoded);
        } catch (Exception e) {
            return "";
        }
    }
}
