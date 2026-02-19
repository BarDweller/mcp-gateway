package org.ozzy.dto;

public class ToolDto {

    private String name;
    private String title;
    private String description;
    private InputSchemaDto inputSchema;
    private String validationStatus;
    private Long lastValidatedAt;
    private Long firstFailedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public InputSchemaDto getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(InputSchemaDto inputSchema) {
        this.inputSchema = inputSchema;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public Long getLastValidatedAt() {
        return lastValidatedAt;
    }

    public void setLastValidatedAt(Long lastValidatedAt) {
        this.lastValidatedAt = lastValidatedAt;
    }

    public Long getFirstFailedAt() {
        return firstFailedAt;
    }

    public void setFirstFailedAt(Long firstFailedAt) {
        this.firstFailedAt = firstFailedAt;
    }
}
