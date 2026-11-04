package io.yak.ops.plugin.registry.jdbc;

import io.yak.ops.plugin.registry.RegistryTestCase;
import io.yak.ops.plugin.registry.jdbc.server.IJdbcRegistryServer;
import io.yak.ops.registry.api.ConnectionState;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import com.google.common.truth.Truth;

import lombok.SneakyThrows;

@SpringBootTest(classes = {JdbcRegistryProperties.class})
@SpringBootApplication(scanBasePackageClasses = JdbcRegistryProperties.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public abstract class JdbcRegistryTestCase extends RegistryTestCase<JdbcRegistry> {

    @Autowired
    private JdbcRegistryProperties jdbcRegistryProperties;

    @Autowired
    private IJdbcRegistryServer jdbcRegistryServer;

    @SneakyThrows
    @Test
    public void testAddConnectionStateListener() {

        AtomicReference<ConnectionState> connectionState = new AtomicReference<>();
        registry.addConnectionStateListener(connectionState::set);

        // todo: since the jdbc registry is started at the auto configuration, the stateListener is added after the
        // registry is started.
        Truth.assertThat(connectionState.get()).isNull();
    }

    @Override
    public JdbcRegistry createRegistry() {
        return new JdbcRegistry(jdbcRegistryProperties, jdbcRegistryServer);
    }

}
