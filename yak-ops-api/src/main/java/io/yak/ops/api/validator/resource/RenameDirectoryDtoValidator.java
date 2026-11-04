package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.RenameDirectoryDto;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.springframework.stereotype.Component;

@Component
public class RenameDirectoryDtoValidator extends AbstractResourceValidator<RenameDirectoryDto> {

    public RenameDirectoryDtoValidator(StorageOperator storageOperator, TenantDao tenantDao) {
        super(storageOperator, tenantDao);
    }

    @Override
    public void validate(RenameDirectoryDto renameDirectoryDto) {
        String originDirectoryAbsolutePath = renameDirectoryDto.getOriginDirectoryAbsolutePath();
        User loginUser = renameDirectoryDto.getLoginUser();
        String targetDirectoryAbsolutePath = renameDirectoryDto.getTargetDirectoryAbsolutePath();

        exceptionResourceAbsolutePathInvalidated(originDirectoryAbsolutePath);
        exceptionResourceIsNotDirectory(originDirectoryAbsolutePath);
        exceptionResourceNotExists(originDirectoryAbsolutePath);
        exceptionUserNoResourcePermission(loginUser, originDirectoryAbsolutePath);

        exceptionResourceAbsolutePathInvalidated(targetDirectoryAbsolutePath);
        exceptionResourceIsNotDirectory(targetDirectoryAbsolutePath);
        exceptionResourceExists(targetDirectoryAbsolutePath);
        exceptionUserNoResourcePermission(loginUser, targetDirectoryAbsolutePath);
    }
}
