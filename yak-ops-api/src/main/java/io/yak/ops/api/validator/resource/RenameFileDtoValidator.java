package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.RenameFileDto;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.springframework.stereotype.Component;

@Component
public class RenameFileDtoValidator extends AbstractResourceValidator<RenameFileDto> {

    public RenameFileDtoValidator(StorageOperator storageOperator, TenantDao tenantDao) {
        super(storageOperator, tenantDao);
    }

    @Override
    public void validate(RenameFileDto renameFileDto) {
        String originFileAbsolutePath = renameFileDto.getOriginFileAbsolutePath();
        User loginUser = renameFileDto.getLoginUser();
        String targetFileAbsolutePath = renameFileDto.getTargetFileAbsolutePath();

        exceptionResourceAbsolutePathInvalidated(originFileAbsolutePath);
        exceptionResourceNotExists(originFileAbsolutePath);
        exceptionResourceIsNotFile(originFileAbsolutePath);
        exceptionUserNoResourcePermission(loginUser, originFileAbsolutePath);

        exceptionResourceAbsolutePathInvalidated(targetFileAbsolutePath);
        exceptionResourceExists(targetFileAbsolutePath);
        exceptionResourceIsNotFile(targetFileAbsolutePath);
        exceptionUserNoResourcePermission(loginUser, targetFileAbsolutePath);
    }
}
