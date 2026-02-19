package org.ozzy.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class MCPGateway {
    @NotBlank
    private String id;
    @NotBlank
    private String name;
    @NotBlank
    private String status;
    @Min(1)
    private int port;
    @NotBlank
    private String host;
    private String authType;
    private String authUsername;
    private String authPassword;
    private String authToken;
    private List<GatewayToolRef> tools = new ArrayList<>();

    public MCPGateway() {
        this.id = UUID.randomUUID().toString();
        this.status = "STOPPED";
    }

    public MCPGateway(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.status = "STOPPED";
    }

    public MCPGateway(String name, String status, int port, String host) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.status = status;
        this.port = port;
        this.host = host;
    }

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

    public List<GatewayToolRef> getTools() {
        return tools;
    }

    public void setTools(List<GatewayToolRef> tools) {
        this.tools = tools;
    }

    @Override
    public String toString() {
        return "MCPGateway{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", port=" + port +
                ", host='" + host + '\'' +
                ", authType='" + authType + '\'' +
            ", tools=" + tools +
                '}';
    }
}