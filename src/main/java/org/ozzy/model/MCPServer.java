package org.ozzy.model;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class MCPServer {

    private String id;
    private String host;
    @Min(0)
    private int port;
    private String authorizationType;
    private String clientCert;
    private String authUsername;
    private String authPassword;
    private String authToken;
    @NotBlank
    private String name;
    @NotBlank
    private String type; // 'local' or 'remote'
    private String path;
    private String remotePath;
    private String protocol; // 'HTTP' or 'HTTPS'
    private String argument;
    private String certificate; // Stores the actual certificate in PEM format
    private List<Tool> tools;
    private java.util.Map<String, String> headers;
    private String oauthAccessToken;
    private String oauthRefreshToken;

    public MCPServer() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isValid() {
        if ("local".equalsIgnoreCase(type)) {
            return path != null && !path.isEmpty() && argument != null && !argument.isEmpty()
                    && host == null && port == 0 && authorizationType == null && clientCert == null;
        } else if ("remote".equalsIgnoreCase(type)) {
            return host != null && !host.isEmpty() && port > 0 && authorizationType != null && !authorizationType.isEmpty()
                    && path == null && argument == null;
        }
        return false;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAuthorizationType() {
        return authorizationType;
    }

    public void setAuthorizationType(String authorizationType) {
        this.authorizationType = authorizationType;
    }

    public String getAuthUsername() {
        return authUsername;
    }

    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    public String getAuthPassword() {
        return authPassword;
    }

    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getClientCert() {
        return clientCert;
    }

    public void setClientCert(String clientCert) {
        this.clientCert = clientCert;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getArgument() {
        return argument;
    }

    public void setArgument(String argument) {
        this.argument = argument;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public List<Tool> getTools() {
        return tools;
    }

    public void setTools(List<Tool> tools) {
        this.tools = tools;
    }

    public java.util.Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(java.util.Map<String, String> headers) {
        this.headers = headers;
    }

    public String getOauthAccessToken() {
        return oauthAccessToken;
    }

    public void setOauthAccessToken(String oauthAccessToken) {
        this.oauthAccessToken = oauthAccessToken;
    }

    public String getOauthRefreshToken() {
        return oauthRefreshToken;
    }

    public void setOauthRefreshToken(String oauthRefreshToken) {
        this.oauthRefreshToken = oauthRefreshToken;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public String toString() {
        return "{" +
                "\"id\": \"" + id + "\"," +
                "\"host\": \"" + host + "\"," +
                "\"port\": " + port + "," +
                "\"authorizationType\": \"" + authorizationType + "\"," +
                "\"clientCert\": \"" + clientCert + "\"," +
                "\"authUsername\": \"" + authUsername + "\"," +
                "\"authPassword\": \"" + authPassword + "\"," +
                "\"authToken\": \"" + authToken + "\"," +
                "\"name\": \"" + name + "\"," +
                "\"type\": \"" + type + "\"," +
                "\"path\": \"" + path + "\"," +
                "\"remotePath\": \"" + remotePath + "\"," +
                "\"protocol\": \"" + protocol + "\"," +
                "\"argument\": \"" + argument + "\"," +
                "\"certificate\": \"" + certificate + "\"," +
                "\"headers\": " + headers + "," +
                "\"oauthAccessToken\": \"" + oauthAccessToken + "\"," +
                "\"oauthRefreshToken\": \"" + oauthRefreshToken + "\"," +
                "\"tools\": " + tools +
                '}';
    }
}