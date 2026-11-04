package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.CreateFileDto;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class CreateFileDtoValidator extends AbstractResourceValidator<CreateFileDto> {

    public CreateFileDtoValidator(StorageOperator storageOperator, TenantDao tenantDao) {
        super(storageOperator, tenantDao);
    }

    @Override
    public void validate(CreateFileDto createFileDto) {
        String fileAbsolutePath = createFileDto.getFileAbsolutePath();
        User loginUser = createFileDto.getLoginUser();
        MultipartFile file = createFileDto.getFile();

        exceptionResourceAbsolutePathInvalidated(fileAbsolutePath);
        exceptionResourceExists(fileAbsolutePath);
        exceptionFileInvalidated(file);
        exceptionUserNoResourcePermission(loginUser, fileAbsolutePath);
        exceptionResourceIsNotFile(fileAbsolutePath);
    }
}
