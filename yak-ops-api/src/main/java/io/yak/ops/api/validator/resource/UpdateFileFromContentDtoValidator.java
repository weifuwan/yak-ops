package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.UpdateFileFromContentDto;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.springframework.stereotype.Component;

@Component
public class UpdateFileFromContentDtoValidator extends AbstractResourceValidator<UpdateFileFromContentDto> {

    public UpdateFileFromContentDtoValidator(StorageOperator storageOperator, TenantDao tenantDao) {
        super(storageOperator, tenantDao);
    }

    @Override
    public void validate(UpdateFileFromContentDto updateFileFromContentDto) {
        String fileAbsolutePath = updateFileFromContentDto.getFileAbsolutePath();
        User loginUser = updateFileFromContentDto.getLoginUser();
        String fileContent = updateFileFromContentDto.getFileContent();

        exceptionResourceAbsolutePathInvalidated(fileAbsolutePath);
        exceptionResourceNotExists(fileAbsolutePath);
        exceptionResourceIsNotFile(fileAbsolutePath);
        exceptionUserNoResourcePermission(loginUser, fileAbsolutePath);
        exceptionFileContentCannotFetch(fileAbsolutePath);
        exceptionFileContentInvalidated(fileContent);
    }
}
