package io.yak.ops.business.datasync.impl;

import java.lang.reflect.Field;

/** Contract Test 所用的 Service 注入助手；自动组合单表校验组件。 */
final class DataSyncTestServices {

    private DataSyncTestServices() {}

    static void inject(Object target, String fieldName, Object value) throws Exception {
        if ("dataSourceService".equals(fieldName) || "tableRouteRepository".equals(fieldName)) {
            if (!(target instanceof DataSyncTaskDefinitionValidator)) {
                Field validatorField = target.getClass().getDeclaredField("definitionValidator");
                validatorField.setAccessible(true);
                DataSyncTaskDefinitionValidator validator =
                        (DataSyncTaskDefinitionValidator) validatorField.get(target);
                if (validator == null) {
                    validator = new DataSyncTaskDefinitionValidator();
                    validatorField.set(target, validator);
                }
                target = validator;
            }
        }
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
