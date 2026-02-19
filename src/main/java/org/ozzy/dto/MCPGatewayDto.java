package org.ozzy.dto;

public class MCPGatewayDto {
    private String id;
    private String name;
    private String status;
    private int port;
    private String host;
    private String authType;
    private String authUsername;
    private String authPassword;
    private String authToken;
    private java.util.List<GatewayToolRefDto> tools;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
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

    public java.util.List<GatewayToolRefDto> getTools() {
        return tools;
    }

    public void setTools(java.util.List<GatewayToolRefDto> tools) {
        this.tools = tools;
    }
}
