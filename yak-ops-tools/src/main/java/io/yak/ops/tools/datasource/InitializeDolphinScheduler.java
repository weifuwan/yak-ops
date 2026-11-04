package io.yak.ops.tools.datasource;

import io.yak.ops.dao.DaoConfiguration;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Import(DaoConfiguration.class)
@SpringBootApplication
public class InitializeDolphinScheduler {

    public static void main(String[] args) {
        SpringApplication.run(InitializeDolphinScheduler.class, args);
    }

    @Component
    @Profile("init")
    static class InitializeRunner implements CommandLineRunner {

        private final DolphinSchedulerManager dolphinSchedulerManager;

        InitializeRunner(DolphinSchedulerManager dolphinSchedulerManager) {
            this.dolphinSchedulerManager = dolphinSchedulerManager;
        }

        @Override
        public void run(String... args) throws Exception {
            dolphinSchedulerManager.initializeOrValidate();
        }
    }
}
