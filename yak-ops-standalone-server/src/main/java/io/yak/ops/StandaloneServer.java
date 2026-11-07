package io.yak.ops;

import io.yak.ops.tools.datasource.InitializeYakOps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
public class StandaloneServer {

    public static void main(String[] args) throws Exception {
        try {
            SpringApplication.run(StandaloneServer.class, args);
        } catch (Exception ex) {
            log.error("StandaloneServer start failed", ex);
            System.exit(1);
        }
    }
}
