package org.ozzy.persistence.impl;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.eclipse.microprofile.config.inject.ConfigProperty;

abstract class PropertiesRepositoryBase {

    protected static final String PREFIX_GATEWAY = "gateway";
    protected static final String PREFIX_SERVER = "server";

    protected static final String FIELD_NAME = "name";
    protected static final String FIELD_HOST = "host";
    protected static final String FIELD_PORT = "port";
    protected static final String FIELD_STATUS = "status";
    protected static final String FIELD_TOOLS = "tools";
    protected static final String FIELD_AUTH_TYPE = "authType";
    protected static final String FIELD_AUTH_USERNAME = "authUsername";
    protected static final String FIELD_AUTH_PASSWORD = "authPassword";
    protected static final String FIELD_AUTH_TOKEN = "authToken";
    protected static final String FIELD_AUTHORIZATION_TYPE = "authorizationType";
    protected static final String FIELD_TYPE = "type";
    protected static final String FIELD_PATH = "path";
    protected static final String FIELD_REMOTE_PATH = "remotePath";
    protected static final String FIELD_PROTOCOL = "protocol";
    protected static final String FIELD_ARGUMENT = "argument";
    protected static final String FIELD_CLIENT_CERT = "clientCert";
    protected static final String FIELD_CERTIFICATE = "certificate";
    protected static final String FIELD_HEADERS = "headers";
    protected static final String FIELD_OAUTH_ACCESS_TOKEN = "oauthAccessToken";
    protected static final String FIELD_OAUTH_REFRESH_TOKEN = "oauthRefreshToken";
    protected static final String FIELD_OAUTH_CLIENT_ID = "oauthClientId";

    protected static final String APP_AUTH_TYPE_KEY = "app.auth.type";
    protected static final String APP_AUTH_USERNAME_KEY = "app.auth.username";
    protected static final String APP_AUTH_PASSWORD_KEY = "app.auth.password";

    protected static final String DEFAULT_APP_AUTH_TYPE = "BASIC";
    protected static final String DEFAULT_APP_AUTH_USERNAME = "admin";
    protected static final String DEFAULT_APP_AUTH_PASSWORD = "admin";

    @ConfigProperty(name = "mcp.properties.path", defaultValue = "config.properties")
    String propertiesFile;

    protected Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(propertiesFile)) {
            properties.load(input);
        } catch (IOException e) {
            System.err.println("Could not load properties file: " + e.getMessage());
        }
        return properties;
    }

    protected void saveProperties(Properties properties) {
        try (OutputStream output = new FileOutputStream(propertiesFile)) {
            properties.store(output, null);
        } catch (IOException e) {
            System.err.println("Could not save properties file: " + e.getMessage());
        }
    }

    protected String buildKey(String prefix, String id, String field) {
        return prefix + "." + id + "." + field;
    }

    protected String buildGatewayKey(String id, String field) {
        return buildKey(PREFIX_GATEWAY, id, field);
    }

    protected String buildServerKey(String id, String field) {
        return buildKey(PREFIX_SERVER, id, field);
    }

    protected boolean matchesPrefix(String key, String prefix) {
        return key != null && key.startsWith(prefix + ".");
    }

    protected String extractId(String key, String prefix) {
        if (!matchesPrefix(key, prefix)) {
            return null;
        }
        String[] parts = key.split("\\.");
        if (parts.length < 3) {
            return null;
        }
        return parts[1];
    }

    protected void removeByPrefix(Properties properties, String prefix) {
        properties.entrySet().removeIf(entry -> matchesPrefix(String.valueOf(entry.getKey()), prefix));
    }

    protected void setOptional(Properties properties, String key, String value) {
        if (value == null) {
            return;
        }
        properties.setProperty(key, value);
    }

    protected String encodeSecret(String value) {
        if (value == null) {
            return null;
        }
        return java.util.Base64.getEncoder().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    protected String decodeSecret(String value) {
        if (value == null) {
            return null;
        }
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(value);
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
