package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.CreateDirectoryDto;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.apache.commons.lang3.StringUtils;

import org.springframework.stereotype.Component;

import com.google.common.io.Files;

@Component
public class CreateDirectoryDtoValidator extends AbstractResourceValidator<CreateDirectoryDto> {

    public CreateDirectoryDtoValidator(StorageOperator storageOperator, TenantDao tenantDao) {
        super(storageOperator, tenantDao);
    }

    @Override
    public void validate(CreateDirectoryDto createDirectoryDto) {
        String directoryAbsolutePath = createDirectoryDto.getDirectoryAbsolutePath();

        exceptionResourceAbsolutePathInvalidated(directoryAbsolutePath);
        exceptionResourceExists(directoryAbsolutePath);
        exceptionUserNoResourcePermission(createDirectoryDto.getLoginUser(), directoryAbsolutePath);
        exceptionResourceIsNotDirectory(directoryAbsolutePath);
        if (StringUtils.isNotEmpty(Files.getFileExtension(directoryAbsolutePath))) {
            throw new ServiceException("The path is not a directory: " + directoryAbsolutePath);
        }
    }
}
