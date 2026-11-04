package io.yak.ops.api.dto.workflowInstance;

import io.yak.ops.plugin.task.api.model.Property;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceVariablesDTO {

    private List<Property> globalParams;

    private Map<String, Map<String, Object>> localParams;
}
