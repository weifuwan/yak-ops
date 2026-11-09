package io.yak.ops.api.utils;

import static io.yak.ops.common.constants.Constants.LOCAL_PARAMS;
import static io.yak.ops.plugin.task.api.TaskConstants.LOCAL_PARAMS_LIST;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.vo.TaskDefinitionVO;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.DagData;
import io.yak.ops.dao.entity.TaskDefinition;
import io.yak.ops.dao.entity.TaskDefinitionLog;
import io.yak.ops.dao.entity.AbstractTaskInstanceContext;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.TaskInstanceDependentDetails;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.entity.WorkflowDefinitionLog;
import io.yak.ops.dao.entity.WorkflowInstance;
import io.yak.ops.plugin.task.api.model.Property;
import io.yak.ops.plugin.task.api.utils.GlobalParameterUtils;
import io.yak.ops.plugin.task.api.utils.PropertySensitiveUtils;
import io.yak.ops.plugin.task.api.utils.VarPoolUtils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;

import org.springframework.beans.BeanUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@UtilityClass
public class SensitivePropertyUtils {

    public void requireNoPlaceholder(List<Property> properties) {
        String invalidProp = PropertySensitiveUtils.findPlaceholderProp(properties);
        if (invalidProp != null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR,
                    "parameter '" + invalidProp + "' cannot use ****** when creating; please re-enter the value");
        }
    }

    public List<Property> merge(List<Property> submittedProperties, List<Property> existingProperties) {
        if (CollectionUtils.isEmpty(submittedProperties)) {
            return submittedProperties;
        }
        String invalidProp = PropertySensitiveUtils.findInvalidSensitivePlaceholderProp(
                submittedProperties, existingProperties);
        if (invalidProp != null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR,
                    "parameter '" + invalidProp
                            + "' cannot use ****** without an existing sensitive value; please re-enter the value");
        }
        return PropertySensitiveUtils.mergeSensitiveValuePlaceholders(submittedProperties, existingProperties);
    }

    public String mergeLocalParams(String submittedTaskParams, String existingTaskParams) {
        return rewriteLocalParams(submittedTaskParams, submitted -> {
            if (StringUtils.isEmpty(existingTaskParams)) {
                requireNoPlaceholder(submitted);
                return submitted;
            }
            return merge(submitted, getLocalParams(existingTaskParams));
        });
    }

    public DagData mask(DagData source) {
        if (source == null) {
            return null;
        }
        DagData copy = new DagData();
        BeanUtils.copyProperties(source, copy);
        copy.setWorkflowDefinition(mask(source.getWorkflowDefinition()));
        if (source.getTaskDefinitionList() != null) {
            copy.setTaskDefinitionList(source.getTaskDefinitionList().stream()
                    .map(SensitivePropertyUtils::mask)
                    .collect(Collectors.toList()));
        }
        return copy;
    }

    public WorkflowDefinition mask(WorkflowDefinition source) {
        if (source == null) {
            return null;
        }
        WorkflowDefinition copy = source instanceof WorkflowDefinitionLog
                ? new WorkflowDefinitionLog()
                : new WorkflowDefinition();
        BeanUtils.copyProperties(source, copy);
        copy.setGlobalParams(maskGlobalParams(copy.getGlobalParams()));
        copy.setGlobalParamMap(null);
        return copy;
    }

    public TaskDefinition mask(TaskDefinition source) {
        if (source == null) {
            return null;
        }
        TaskDefinition copy;
        if (source instanceof TaskDefinitionVO) {
            copy = new TaskDefinitionVO();
        } else if (source instanceof TaskDefinitionLog) {
            copy = new TaskDefinitionLog();
        } else {
            copy = new TaskDefinition();
        }
        BeanUtils.copyProperties(source, copy);
        copy.setTaskParams(rewriteLocalParams(copy.getTaskParams(),
                PropertySensitiveUtils::maskSensitiveValues));
        copy.setTaskParamMap(null);
        return copy;
    }

    public TaskInstance mask(TaskInstance source) {
        if (source == null) {
            return null;
        }
        TaskInstance copy = source instanceof TaskInstanceDependentDetails
                ? new TaskInstanceDependentDetails<AbstractTaskInstanceContext>()
                : new TaskInstance();
        BeanUtils.copyProperties(source, copy);
        copy.setTaskParams(rewriteLocalParams(copy.getTaskParams(),
                PropertySensitiveUtils::maskSensitiveValues));
        copy.setVarPool(maskVarPool(copy.getVarPool()));
        return copy;
    }

    public WorkflowInstance mask(WorkflowInstance source) {
        if (source == null) {
            return null;
        }
        WorkflowInstance copy = new WorkflowInstance();
        BeanUtils.copyProperties(source, copy);
        copy.setGlobalParams(maskGlobalParams(copy.getGlobalParams()));
        copy.setVarPool(maskVarPool(copy.getVarPool()));
        copy.setDagData(mask(source.getDagData()));
        return copy;
    }

    public Map<String, Map<String, Object>> mask(Map<String, Map<String, Object>> source) {
        if (source == null) {
            return null;
        }
        Map<String, Map<String, Object>> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : source.entrySet()) {
            Map<String, Object> inner = entry.getValue();
            if (inner == null) {
                masked.put(entry.getKey(), null);
                continue;
            }
            Map<String, Object> copied = new LinkedHashMap<>(inner);
            Object localParamsList = copied.get(LOCAL_PARAMS_LIST);
            if (localParamsList instanceof List<?>) {
                List<Property> properties = new ArrayList<>();
                for (Object item : (List<?>) localParamsList) {
                    if (item instanceof Property) {
                        properties.add((Property) item);
                    }
                }
                copied.put(LOCAL_PARAMS_LIST, PropertySensitiveUtils.maskSensitiveValues(properties));
            }
            masked.put(entry.getKey(), copied);
        }
        return masked;
    }

    private String maskGlobalParams(String globalParams) {
        List<Property> properties = GlobalParameterUtils.deserializeGlobalParameter(globalParams);
        if (CollectionUtils.isEmpty(properties)) {
            return globalParams;
        }
        return GlobalParameterUtils.serializeGlobalParameter(
                PropertySensitiveUtils.maskSensitiveValues(properties));
    }

    private String maskVarPool(String varPool) {
        if (StringUtils.isEmpty(varPool)) {
            return varPool;
        }
        List<Property> properties = VarPoolUtils.deserializeVarPool(varPool);
        if (CollectionUtils.isEmpty(properties)) {
            return varPool;
        }
        return VarPoolUtils.serializeVarPool(PropertySensitiveUtils.maskSensitiveValues(properties));
    }

    private List<Property> getLocalParams(String taskParams) {
        if (StringUtils.isEmpty(taskParams)) {
            return Collections.emptyList();
        }
        String localParams = JSONUtils.getNodeString(taskParams, LOCAL_PARAMS);
        if (StringUtils.isEmpty(localParams)) {
            return Collections.emptyList();
        }
        return JSONUtils.toList(localParams, Property.class);
    }

    private String rewriteLocalParams(String taskParams, Function<List<Property>, List<Property>> transform) {
        if (StringUtils.isEmpty(taskParams)) {
            return taskParams;
        }
        ObjectNode taskParamsNode = JSONUtils.parseObject(taskParams);
        if (taskParamsNode == null) {
            return taskParams;
        }
        JsonNode localParamsNode = taskParamsNode.findValue(LOCAL_PARAMS);
        if (localParamsNode == null || localParamsNode.isNull()) {
            return taskParams;
        }
        List<Property> localParams = JSONUtils.toList(localParamsNode.toString(), Property.class);
        taskParamsNode.set(LOCAL_PARAMS, JSONUtils.toJsonNode(transform.apply(localParams)));
        return JSONUtils.toJsonString(taskParamsNode);
    }
}
