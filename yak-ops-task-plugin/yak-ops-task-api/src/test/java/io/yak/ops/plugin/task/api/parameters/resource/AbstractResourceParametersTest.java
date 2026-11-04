package io.yak.ops.plugin.task.api.parameters.resource;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.parameters.SqlParameters;
import io.yak.ops.spi.enums.DbType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AbstractResourceParametersTest {

    @Test
    public void testDataSource() {
        TaskExecutionContext taskExecutionContext = new TaskExecutionContext();
        String taskParam =
                "{\"localParams\":[],\"resourceList\":[],\"type\":\"MYSQL\",\"datasource\":\"1\",\"sql\":\"select now();\",\"sqlType\":\"0\",\"preStatements\":[],\"postStatements\":[],\"conditionResult\":\"null\",\"dependence\":\"null\",\"switchResult\":\"null\",\"waitStartTimeout\":null}";

        ResourceParametersHelper resourceParametersHelper =
                JSONUtils.parseObject(taskParam, SqlParameters.class).getResources();

        resourceParametersHelper.getResourceMap().forEach((type, map) -> {
            map.forEach((code, parameters) -> {
                DataSourceParameters dataSourceParameters = new DataSourceParameters();
                dataSourceParameters.setType(DbType.MYSQL);
                dataSourceParameters.setConnectionParams("127.0.0.1:3306");
                map.put(code, dataSourceParameters);
            });
        });

        taskExecutionContext.setResourceParametersHelper(resourceParametersHelper);

        String json = JSONUtils.toJsonString(taskExecutionContext);

        taskExecutionContext = JSONUtils.parseObject(json, TaskExecutionContext.class);

        Assertions.assertNotNull(taskExecutionContext);
    }
}
