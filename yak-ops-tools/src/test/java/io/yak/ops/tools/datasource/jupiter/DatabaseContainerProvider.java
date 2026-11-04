package io.yak.ops.tools.datasource.jupiter;

import org.testcontainers.containers.GenericContainer;

public interface DatabaseContainerProvider {

    GenericContainer<?> getContainer(YakOpsDatabaseContainer dataSourceContainer);

    String getType();

}
