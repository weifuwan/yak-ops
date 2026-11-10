package io.yak.ops.api.configuration;

import io.yak.ops.api.dto.FavTaskDto;
import io.yak.ops.common.config.YamlPropertySourceFactory;
import io.yak.ops.common.constants.Constants;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableConfigurationProperties
@PropertySource(value = {"classpath:task-type-config.yaml"}, factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "task")
@Data
@Slf4j
public class TaskTypeConfiguration {

    private List<String> universal;
    private List<String> cloud;
    private List<String> logic;
    private List<String> dataIntegration;
    private List<String> other;
    private List<String> machineLearning;

    private static final List<FavTaskDto> defaultTaskTypes = new ArrayList<>();

    public List<FavTaskDto> getDefaultTaskTypes() {
        if (CollectionUtils.isNotEmpty(defaultTaskTypes)) {
            return defaultTaskTypes;
        }
        printDefaultTypes();
        universal.forEach(task -> defaultTaskTypes.add(new FavTaskDto(task, false, Constants.TYPE_UNIVERSAL)));
        cloud.forEach(task -> defaultTaskTypes.add(new FavTaskDto(task, false, Constants.TYPE_CLOUD)));
        logic.forEach(task -> defaultTaskTypes.add(new FavTaskDto(task, false, Constants.TYPE_LOGIC)));
        dataIntegration
                .forEach(task -> defaultTaskTypes.add(new FavTaskDto(task, false, Constants.TYPE_DATA_INTEGRATION)));
        machineLearning
                .forEach(task -> defaultTaskTypes.add(new FavTaskDto(task, false, Constants.TYPE_MACHINE_LEARNING)));
        other.forEach(task -> defaultTaskTypes.add(new FavTaskDto(task, false, Constants.TYPE_OTHER)));
        return defaultTaskTypes;
    }

    public String getTaskCategory(String taskType) {
        if (containsTaskType(universal, taskType)) {
            return Constants.TYPE_UNIVERSAL;
        }
        if (containsTaskType(cloud, taskType)) {
            return Constants.TYPE_CLOUD;
        }
        if (containsTaskType(logic, taskType)) {
            return Constants.TYPE_LOGIC;
        }
        if (containsTaskType(dataIntegration, taskType)) {
            return Constants.TYPE_DATA_INTEGRATION;
        }
        if (containsTaskType(machineLearning, taskType)) {
            return Constants.TYPE_MACHINE_LEARNING;
        }
        if (containsTaskType(other, taskType)) {
            return Constants.TYPE_OTHER;
        }
        return Constants.TYPE_OTHER;
    }

    private boolean containsTaskType(List<String> taskTypes, String taskType) {
        return CollectionUtils.isNotEmpty(taskTypes) && taskTypes.contains(taskType);
    }

    public void printDefaultTypes() {
        log.info("support default universal task types: {}", universal);
        log.info("support default cloud task types: {}", cloud);
        log.info("support default logic task types: {}", logic);
        log.info("support default dataIntegration task types: {}", dataIntegration);
        log.info("support default machineLearning task types: {}", machineLearning);
        log.info("support default other task types: {}", other);
    }
}
