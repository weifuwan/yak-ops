package io.yak.ops.agent.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.yak.ops.api.service.LoggerService;
import io.yak.ops.api.utils.Result;
import io.yak.ops.dao.entity.ResponseTaskLog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Read-only task instance log lookup. Permission checks live inside the logger service
 * (project membership is resolved from the instance itself).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
public class TaskLogQueryTool implements AgentTool {

    private static final int DEFAULT_LIMIT = 200;

    private static final int MAX_LOG_CHARS = 8000;

    private final LoggerService loggerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "query_task_log";
    }

    @Override
    public String description() {
        return "查询任务实例的执行日志（日志尾部包含报错信息）。"
                + "用于回答：任务失败的具体报错是什么、日志里的关键异常。"
                + "taskInstanceId 来自 query_data_sync_instances 返回的 taskInstanceId。";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("taskInstanceId", property("integer", "任务实例 ID（即数据同步实例 ID）"));
        properties.put("skipLineNum", property("integer", "跳过行数，默认 0"));
        properties.put("limit", property("integer", "读取行数，默认 200，最大 1000"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Collections.singletonList("taskInstanceId"));
        return schema;
    }

    @Override
    public String execute(AgentToolContext context, Map<String, Object> arguments) throws Exception {
        Object rawId = arguments.get("taskInstanceId");
        if (rawId == null || String.valueOf(rawId).trim().isEmpty()) {
            throw new IllegalArgumentException("taskInstanceId is required");
        }
        int taskInstanceId = Integer.parseInt(String.valueOf(rawId).trim());
        int skipLineNum = Math.max(0, resolveInt(arguments.get("skipLineNum"), 0));
        int limit = Math.min(1000, Math.max(1, resolveInt(arguments.get("limit"), DEFAULT_LIMIT)));

        Result<ResponseTaskLog> result = loggerService.queryLog(context.getUser(), taskInstanceId, skipLineNum, limit);
        ResponseTaskLog taskLog = result == null ? null : result.getData();

        String log = taskLog == null ? null : taskLog.getMessage();
        boolean truncated = false;
        if (log != null && log.length() > MAX_LOG_CHARS) {
            log = log.substring(log.length() - MAX_LOG_CHARS);
            truncated = true;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskInstanceId", taskInstanceId);
        payload.put("totalLines", taskLog == null ? null : taskLog.getLineNum());
        payload.put("truncated", truncated);
        payload.put("log", log);
        return objectMapper.writeValueAsString(payload);
    }

    private int resolveInt(Object raw, int defaultValue) {
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(raw).trim());
    }

    private Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }
}
