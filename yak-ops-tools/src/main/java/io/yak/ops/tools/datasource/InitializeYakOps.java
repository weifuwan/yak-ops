package io.yak.ops.tools.datasource;

import io.yak.ops.dao.DaoConfiguration;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Import(DaoConfiguration.class)
@Profile("init")
@SpringBootApplication(exclude = QuartzAutoConfiguration.class)
public class InitializeYakOps {

    public static void main(String[] args) {
        SpringApplication.run(InitializeYakOps.class, args);
    }

    @Component
    @Profile("init")
    static class InitializeRunner implements CommandLineRunner {

        private final YakOpsManager yakOpsManager;

        InitializeRunner(YakOpsManager yakOpsManager) {
            this.yakOpsManager = yakOpsManager;
        }

        @Override
        public void run(String... args) throws Exception {
            yakOpsManager.initializeOrValidate();
        }
    }
}
