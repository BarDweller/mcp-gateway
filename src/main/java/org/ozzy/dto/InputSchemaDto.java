package org.ozzy.dto;

import java.util.List;
import java.util.Map;

public class InputSchemaDto {

    private String type;
    private Map<String, InputSchemaPropertyDto> properties;
    private Map<String, InputSchemaPropertyDto> definitions;
    private List<String> required;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, InputSchemaPropertyDto> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, InputSchemaPropertyDto> properties) {
        this.properties = properties;
    }

    public Map<String, InputSchemaPropertyDto> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(Map<String, InputSchemaPropertyDto> definitions) {
        this.definitions = definitions;
    }

    public List<String> getRequired() {
        return required;
    }

    public void setRequired(List<String> required) {
        this.required = required;
    }
}
