package io.yak.ops.tools.datasource.jupiter;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DolphinSchedulerDatabaseContainerExtension implements BeforeAllCallback, AfterAllCallback {

    private static GenericContainer<?> databaseContainer;

    @Override
    public void beforeAll(ExtensionContext context) {
        databaseContainer = getDataSourceContainer(context);

    }

    private GenericContainer<?> getDataSourceContainer(ExtensionContext context) {
        Class<?> requiredTestClass = context.getRequiredTestClass();
        DolphinSchedulerDatabaseContainer annotation =
                requiredTestClass.getAnnotation(DolphinSchedulerDatabaseContainer.class);
        if (annotation == null) {
            throw new IllegalArgumentException("@DolphinSchedulerDataSourceContainer annotation not found");
        }
        Map<String, DatabaseContainerProvider> dataSourceContainerProviderMap = new HashMap<>();
        ServiceLoader.load(DatabaseContainerProvider.class)
                .forEach(databaseContainerProvider -> dataSourceContainerProviderMap
                        .put(databaseContainerProvider.getType(), databaseContainerProvider));

        DockerImageName dockerImageName = DockerImageName.parse(annotation.imageName());

        if (!dataSourceContainerProviderMap.containsKey(dockerImageName.getRepository())) {
            throw new IllegalArgumentException(
                    "DataSourceContainerProvider not found for type: " + annotation.imageName());
        }
        DatabaseContainerProvider databaseContainerProvider =
                dataSourceContainerProviderMap.get(dockerImageName.getRepository());
        return databaseContainerProvider.getContainer(annotation);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (databaseContainer != null) {
            databaseContainer.stop();
        }
    }
}
