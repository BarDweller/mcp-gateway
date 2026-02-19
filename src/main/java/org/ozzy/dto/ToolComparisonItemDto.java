package org.ozzy.dto;

import java.util.ArrayList;
import java.util.List;

public class ToolComparisonItemDto {
    private String name;
    private boolean match;
    private ToolDto stored;
    private ToolDto current;
    private final List<ToolFieldDiffDto> diffs = new ArrayList<>();

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

    public ToolDto getStored() {
        return stored;
    }

    public void setStored(ToolDto stored) {
        this.stored = stored;
    }

    public ToolDto getCurrent() {
        return current;
    }

    public void setCurrent(ToolDto current) {
        this.current = current;
    }

    public List<ToolFieldDiffDto> getDiffs() {
        return diffs;
    }

    public void addDiff(ToolFieldDiffDto diff) {
        diffs.add(diff);
    }
}
