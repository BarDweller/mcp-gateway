package org.ozzy.service;

import java.util.ArrayList;
import java.util.List;

import org.ozzy.model.Tool;

public class ToolComparisonResult {
    private boolean match;
    private final List<ToolComparisonItem> tools = new ArrayList<>();

    public boolean isMatch() {
        return match;
    }

    public void setMatch(boolean match) {
        this.match = match;
    }

    public List<ToolComparisonItem> getTools() {
        return tools;
    }

    public void addTool(ToolComparisonItem item) {
        tools.add(item);
    }

    public static class ToolComparisonItem {
        private String name;
        private boolean match;
        private Tool stored;
        private Tool current;
        private final List<ToolFieldDiff> diffs = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isMatch() {
            return match;
        }

        public void setMatch(boolean match) {
            this.match = match;
        }

        public Tool getStored() {
            return stored;
        }

        public void setStored(Tool stored) {
            this.stored = stored;
        }

        public Tool getCurrent() {
            return current;
        }

        public void setCurrent(Tool current) {
            this.current = current;
        }

        public List<ToolFieldDiff> getDiffs() {
            return diffs;
        }

        public void addDiff(ToolFieldDiff diff) {
            diffs.add(diff);
        }
    }

    public static class ToolFieldDiff {
        private String field;
        private String oldValue;
        private String newValue;

        public ToolFieldDiff() {
        }

        public ToolFieldDiff(String field, String oldValue, String newValue) {
            this.field = field;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getOldValue() {
            return oldValue;
        }

        public void setOldValue(String oldValue) {
            this.oldValue = oldValue;
        }

        public String getNewValue() {
            return newValue;
        }

        public void setNewValue(String newValue) {
            this.newValue = newValue;
        }
    }
}
