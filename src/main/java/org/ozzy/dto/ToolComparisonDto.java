package org.ozzy.dto;

import java.util.ArrayList;
import java.util.List;

public class ToolComparisonDto {
    private boolean match;
    private final List<ToolComparisonItemDto> tools = new ArrayList<>();

    public boolean isMatch() {
        return match;
    }

    public void setMatch(boolean match) {
        this.match = match;
    }

    public List<ToolComparisonItemDto> getTools() {
        return tools;
    }

    public void addTool(ToolComparisonItemDto item) {
        tools.add(item);
    }
}
