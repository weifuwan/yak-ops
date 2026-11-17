package io.yak.ops.agent.config;

import org.mybatis.spring.annotation.MapperScan;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConditionalOnProperty(prefix = "yak.agent", name = "enabled", havingValue = "true")
@ConfigurationProperties(prefix = "yak.agent")
@MapperScan(basePackages = "io.yak.ops.agent.dao.mapper", sqlSessionFactoryRef = "sqlSessionFactory")
public class AgentConfiguration {

    private String llmBaseUrl;

    private String llmApiKey;

    private String llmModel;

    private int llmTimeoutSeconds = 120;

    private int maxToolRounds = 5;

    private int turnPoolSize = 4;
}
