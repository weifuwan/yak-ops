package io.yak.ops.plugin.task.api.model;

import io.yak.ops.plugin.task.api.enums.DependResult;
import io.yak.ops.plugin.task.api.enums.DependentType;

import lombok.Data;

@Data
public class DependentItem {

    private DependentType dependentType;
    private long projectCode;
    private long definitionCode;
    private long depTaskCode;
    private String cycle;
    private String dateValue;
    private DependResult dependResult;
    private Boolean parameterPassing = false;

    public String getKey() {
        return String.format("%d-%d-%s-%s",
                getDefinitionCode(),
                getDepTaskCode(),
                getCycle(),
                getDateValue());
    }

    public DependentItem fromKey(String key) {
        String[] parts = key.split("-");
        boolean isNegativeDepTaskCode = parts.length == 5 && parts[1].isEmpty();
        if (parts.length != 4 && !isNegativeDepTaskCode) {
            throw new IllegalArgumentException("Invalid key format");
        }
        int offset = isNegativeDepTaskCode ? 1 : 0;
        setDefinitionCode(Long.parseLong(parts[0]));
        setDepTaskCode(Long.parseLong(isNegativeDepTaskCode ? "-" + parts[2] : parts[1]));
        setCycle(parts[2 + offset]);
        setDateValue(parts[3 + offset]);
        return this;
    }

}
