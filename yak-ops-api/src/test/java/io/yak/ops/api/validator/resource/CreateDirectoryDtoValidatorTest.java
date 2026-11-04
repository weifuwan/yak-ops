package io.yak.ops.api.validator.resource;

import static io.yak.ops.api.AssertionsHelper.assertThrowServiceException;
import static org.mockito.Mockito.when;

import io.yak.ops.api.dto.resources.CreateDirectoryDto;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.Tenant;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.ResourceMetadata;
import io.yak.ops.plugin.storage.api.StorageOperator;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

@ExtendWith(MockitoExtension.class)
class CreateDirectoryDtoValidatorTest {

    @Mock
    private StorageOperator storageOperator;

    @Mock
    private TenantDao tenantDao;

    @InjectMocks
    private CreateDirectoryDtoValidator createDirectoryDtoValidator;

    private static final String BASE_DIRECTORY = "/tmp/dolphinscheduler";

    private User loginUser;

    @BeforeEach
    public void setup() {
        when(storageOperator.getStorageBaseDirectory()).thenReturn(BASE_DIRECTORY);
        loginUser = new User();
        loginUser.setTenantId(1);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @Test
    void testValidate_notUnderBaseDirectory() {
        CreateDirectoryDto createDirectoryDto = CreateDirectoryDto.builder()
                .loginUser(loginUser)
                .directoryAbsolutePath("/tmp")
                .build();
        assertThrowServiceException(
                "Internal Server Error: Invalidated resource path: /tmp",
                () -> createDirectoryDtoValidator.validate(createDirectoryDto));
    }

    @Test
    public void testValidate_directoryPathContainsIllegalSymbolic() {
        CreateDirectoryDto createDirectoryDto = CreateDirectoryDto.builder()
                .loginUser(loginUser)
                .directoryAbsolutePath("/tmp/dolphinscheduler/default/resources/..")
                .build();
        assertThrowServiceException(
                "Internal Server Error: Invalidated resource path: /tmp/dolphinscheduler/default/resources/..",
                () -> createDirectoryDtoValidator.validate(createDirectoryDto));
    }

    @Test
    public void testValidate_directoryExist() {
        CreateDirectoryDto createDirectoryDto = CreateDirectoryDto.builder()
                .loginUser(loginUser)
                .directoryAbsolutePath("/tmp/dolphinscheduler/default/resources/demo")
                .build();
        when(storageOperator.exists(createDirectoryDto.getDirectoryAbsolutePath())).thenReturn(true);
        assertThrowServiceException(
                "Internal Server Error: The resource is already exist: /tmp/dolphinscheduler/default/resources/demo",
                () -> createDirectoryDtoValidator.validate(createDirectoryDto));
    }

    @Test
    public void testValidate_NoPermission() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("test");
        when(tenantDao.queryOptionalById(loginUser.getTenantId())).thenReturn(Optional.of(tenant));

        CreateDirectoryDto createDirectoryDto = CreateDirectoryDto.builder()
                .loginUser(loginUser)
                .directoryAbsolutePath("/tmp/dolphinscheduler/default/resources/demo")
                .build();
        when(storageOperator.getResourceMetaData(createDirectoryDto.getDirectoryAbsolutePath()))
                .thenReturn(ResourceMetadata.builder()
                        .resourceAbsolutePath(createDirectoryDto.getDirectoryAbsolutePath())
                        .resourceBaseDirectory(BASE_DIRECTORY)
                        .resourceRelativePath("demo")
                        .isDirectory(true)
                        .tenant("default")
                        .build());
        when(storageOperator.exists(createDirectoryDto.getDirectoryAbsolutePath())).thenReturn(false);
        assertThrowServiceException(
                "Internal Server Error: The user's tenant is test have no permission to access the resource: /tmp/dolphinscheduler/default/resources/demo",
                () -> createDirectoryDtoValidator.validate(createDirectoryDto));
    }

    @Test
    public void testValidate_pathNotDirectory() {
        CreateDirectoryDto createDirectoryDto = CreateDirectoryDto.builder()
                .loginUser(loginUser)
                .directoryAbsolutePath("/tmp/dolphinscheduler/default/resources/demo.sql")
                .build();
        loginUser.setUserType(UserType.ADMIN_USER);
        assertThrowServiceException(
                "Internal Server Error: The path is not a directory: /tmp/dolphinscheduler/default/resources/demo.sql",
                () -> createDirectoryDtoValidator.validate(createDirectoryDto));
    }

}
