package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.DeleteResourceDto;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.springframework.stereotype.Component;

@Component
public class DeleteResourceDtoValidator extends AbstractResourceValidator<DeleteResourceDto> {

    public DeleteResourceDtoValidator(StorageOperator storageOperator, TenantDao tenantDao) {
        super(storageOperator, tenantDao);
    }

    @Override
    public void validate(DeleteResourceDto deleteResourceDto) {
        String resourceAbsolutePath = deleteResourceDto.getResourceAbsolutePath();
        User loginUser = deleteResourceDto.getLoginUser();

        exceptionResourceAbsolutePathInvalidated(resourceAbsolutePath);
        exceptionResourceNotExists(resourceAbsolutePath);
        exceptionUserNoResourcePermission(loginUser, resourceAbsolutePath);
    }
}
