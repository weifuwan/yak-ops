package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.CreateFileFromContentDto;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.springframework.stereotype.Component;

@Component
public class CreateFileFromContentDtoValidator extends AbstractResourceValidator<CreateFileFromContentDto> {

    public CreateFileFromContentDtoValidator(StorageOperator storageOperator, TenantDao tenantDao) {
        super(storageOperator, tenantDao);
    }

    @Override
    public void validate(CreateFileFromContentDto createFileFromContentDto) {
        String fileAbsolutePath = createFileFromContentDto.getFileAbsolutePath();
        User loginUser = createFileFromContentDto.getLoginUser();
        String fileContent = createFileFromContentDto.getFileContent();

        exceptionResourceAbsolutePathInvalidated(fileAbsolutePath);
        exceptionResourceIsNotFile(fileAbsolutePath);
        exceptionResourceExists(fileAbsolutePath);
        exceptionUserNoResourcePermission(loginUser, fileAbsolutePath);
        exceptionFileContentInvalidated(fileContent);
    }
}
