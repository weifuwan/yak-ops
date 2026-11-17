package io.yak.ops.agent.tool;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.yak.ops.api.service.DataSyncService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.vo.DataSyncInstanceVO;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Read-only query of data sync execution instances (backed by the common task instance store).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class DataSyncInstanceQueryTool implements AgentTool {

    private final DataSyncService dataSyncService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "query_data_sync_instances";
    }

    @Override
    public String description() {
        return "查询数据同步任务的执行实例（状态、起止时间、关联工作流实例）。"
                + "用于回答：某个同步任务为什么失败、现在有哪些实例在运行、最近一次执行结果、任务执行了多久等问题。"
                + "实例 ID（taskInstanceId）可直接用于 query_task_log 查日志。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        List<String> statusNames = new ArrayList<>();
        for (TaskExecutionStatus status : TaskExecutionStatus.values()) {
            statusNames.add(status.name());
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", property("string", "实例状态过滤，常用 FAILURE（失败）、RUNNING_EXECUTION（运行中）、SUCCESS（成功）",
                "enum", statusNames));
        properties.put("taskCode", property("integer", "同步任务编码，指定后只查该任务的实例", null, null));
        properties.put("pageNo", property("integer", "页码，从 1 开始，默认 1", null, null));
        properties.put("pageSize", property("integer", "每页条数，默认 20，最大 50", null, null));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Collections.emptyList());
        return schema;
    }

    @Override
    public String execute(AgentToolContext context, Map<String, Object> arguments) throws Exception {
        TaskExecutionStatus status = resolveStatus(arguments.get("status"));
        Long taskCode = resolveLong(arguments.get("taskCode"));
        int pageNo = Math.max(1, resolveInt(arguments.get("pageNo"), 1));
        int pageSize = Math.min(50, Math.max(1, resolveInt(arguments.get("pageSize"), 20)));

        PageInfo<DataSyncInstanceVO> page = dataSyncService.listInstances(
                context.getUser(), context.getProjectCode(), pageNo, pageSize, taskCode, status);

        List<Map<String, Object>> instances = new ArrayList<>();
        for (DataSyncInstanceVO instance : page.getTotalList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskInstanceId", instance.getId());
            item.put("taskName", instance.getTaskName());
            item.put("status", instance.getStatus() == null ? null : instance.getStatus().name());
            item.put("startTime", format(instance.getStartTime()));
            item.put("endTime", format(instance.getEndTime()));
            item.put("workflowInstanceId", instance.getWorkflowInstanceId());
            instances.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotal());
        result.put("pageNo", page.getPageNo());
        result.put("pageSize", page.getPageSize());
        result.put("instances", instances);
        return objectMapper.writeValueAsString(result);
    }

    private TaskExecutionStatus resolveStatus(Object raw) {
        if (raw == null) {
            return null;
        }
        String name = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        if (name.isEmpty()) {
            return null;
        }
        try {
            return TaskExecutionStatus.valueOf(name);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown status '" + raw + "'. Valid values: "
                    + Arrays.toString(TaskExecutionStatus.values()));
        }
    }

    private Long resolveLong(Object raw) {
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return null;
        }
        return Long.valueOf(String.valueOf(raw).trim());
    }

    private int resolveInt(Object raw, int defaultValue) {
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(raw).trim());
    }

    private String format(java.util.Date date) {
        return date == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private Map<String, Object> property(String type, String description, String extraKey, Object extraValue) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        if (extraKey != null) {
            property.put(extraKey, extraValue);
        }
        return property;
    }
}
