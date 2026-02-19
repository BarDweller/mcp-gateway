package org.ozzy.dto;

public class GatewayToolRefDto {
    private String serverId;
    private String toolName;
    private String validationMode;
    private Long validationPeriodSeconds;

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getValidationMode() {
        return validationMode;
    }

    public void setValidationMode(String validationMode) {
        this.validationMode = validationMode;
    }

    public Long getValidationPeriodSeconds() {
        return validationPeriodSeconds;
    }

    public void setValidationPeriodSeconds(Long validationPeriodSeconds) {
        this.validationPeriodSeconds = validationPeriodSeconds;
    }
}
