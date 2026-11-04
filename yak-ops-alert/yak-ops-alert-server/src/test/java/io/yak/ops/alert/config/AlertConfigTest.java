package io.yak.ops.alert.config;

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@AutoConfigureMockMvc
@SpringBootTest(classes = AlertConfig.class)
class AlertConfigTest {

    @Autowired
    private AlertConfig alertConfig;

    @Test
    void testValidate() {
        assertThat(alertConfig.getWaitTimeout()).isEqualTo(10);
        assertThat(alertConfig.getMaxHeartbeatInterval()).isEqualTo(Duration.ofSeconds(59));
        assertThat(alertConfig.getSenderParallelism()).isEqualTo(101);
    }

}
