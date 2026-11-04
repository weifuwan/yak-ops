package io.yak.ops.plugin.storage.api;

import static io.yak.ops.plugin.storage.api.constants.StorageConstants.RESOURCE_STORAGE_TYPE;

import io.yak.ops.common.utils.PropertyUtils;

import java.util.Optional;
import java.util.ServiceLoader;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// todo: If we move the config to yaml
@Configuration
public class StorageConfiguration {

    @Bean
    public StorageOperator storageOperate() {
        Optional<StorageType> storageTypeOptional =
                StorageType.getStorageType(PropertyUtils.getUpperCaseString(RESOURCE_STORAGE_TYPE));
        Optional<StorageOperator> storageOperate = storageTypeOptional.map(storageType -> {
            ServiceLoader<StorageOperatorFactory> storageOperateFactories =
                    ServiceLoader.load(StorageOperatorFactory.class);
            for (StorageOperatorFactory storageOperateFactory : storageOperateFactories) {
                if (storageOperateFactory.getStorageOperate() == storageType) {
                    return storageOperateFactory.createStorageOperate();
                }
            }
            return null;
        });
        return storageOperate.orElse(null);
    }
}
