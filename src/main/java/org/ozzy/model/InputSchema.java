package org.ozzy.model;

import java.util.List;
import java.util.Map;

public class InputSchema {

    private String type;
    private Map<String, Property> properties;
    private Map<String, Property> definitions;
    private List<String> required;

    public List<String> getRequired() {
        return required;
    }

    public void setRequired(List<String> required) {
        this.required = required;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Property> getDefinitions() {
        return definitions;
    }

    public Map<String, Property> getProperties() {
        return properties;
    }

    public void setDefinitions(Map<String, Property> definitions) {
        this.definitions = definitions;
    }

    public void setProperties(Map<String, Property> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return "{" +
                "\"type\": \"" + type + "\"," +
                "\"definitions\": " + definitions + "," +
                "\"properties\": " + properties + "," +
                "\"required\": " + required +
               '}';
    }

    public static class Property {

        private String type;
        private String description;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return "{" +
                    //"\"type\": \"" + type + "\"," +
                    "\"description\": \"" + description + "\"" +
                    '}';
        }
    }
}