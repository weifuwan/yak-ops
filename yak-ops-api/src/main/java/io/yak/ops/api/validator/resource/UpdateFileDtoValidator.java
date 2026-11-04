package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.UpdateFileDto;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.io.Files;

@Component
public class UpdateFileDtoValidator extends AbstractResourceValidator<UpdateFileDto> {

    public UpdateFileDtoValidator(StorageOperator storageOperator, TenantDao tenantDao) {
        super(storageOperator, tenantDao);
    }

    @Override
    public void validate(UpdateFileDto updateFileDto) {
        String fileAbsolutePath = updateFileDto.getFileAbsolutePath();
        User loginUser = updateFileDto.getLoginUser();
        MultipartFile file = updateFileDto.getFile();

        if (!Objects.equals(
                Files.getFileExtension(
                        file.getOriginalFilename() == null ? file.getName() : file.getOriginalFilename()),
                Files.getFileExtension(updateFileDto.getFileAbsolutePath()))) {
            throw new ServiceException("file extension cannot not change");
        }

        exceptionResourceAbsolutePathInvalidated(fileAbsolutePath);
        exceptionResourceNotExists(fileAbsolutePath);
        exceptionResourceIsNotFile(fileAbsolutePath);
        exceptionUserNoResourcePermission(loginUser, fileAbsolutePath);
        exceptionFileInvalidated(file);
    }
}
