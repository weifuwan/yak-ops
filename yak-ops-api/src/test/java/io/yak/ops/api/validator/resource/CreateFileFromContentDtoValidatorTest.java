package io.yak.ops.api.validator.resource;

import static io.yak.ops.api.AssertionsHelper.assertDoesNotThrow;
import static io.yak.ops.api.AssertionsHelper.assertThrowServiceException;
import static org.mockito.Mockito.when;

import io.yak.ops.api.dto.resources.CreateFileFromContentDto;
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
class CreateFileFromContentDtoValidatorTest {

    @Mock
    private StorageOperator storageOperator;

    @Mock
    private TenantDao tenantDao;

    @InjectMocks
    private CreateFileFromContentDtoValidator createFileFromContentDtoValidator;

    private static final String BASE_DIRECTORY = "/tmp/yak-ops";

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
        CreateFileFromContentDto createFileFromContentDto = CreateFileFromContentDto.builder()
                .loginUser(loginUser)
                .fileAbsolutePath("/tmp")
                .fileContent("select * from t")
                .build();
        assertThrowServiceException(
                "Internal Server Error: Invalidated resource path: /tmp",
                () -> createFileFromContentDtoValidator.validate(createFileFromContentDto));

    }

    @Test
    public void testValidate_filePathContainsIllegalSymbolic() {
        CreateFileFromContentDto renameDirectoryDto = CreateFileFromContentDto.builder()
                .loginUser(loginUser)
                .fileAbsolutePath("/tmp/yak-ops/default/resources/..")
                .fileContent("select * from t")
                .build();
        assertThrowServiceException(
                "Internal Server Error: Invalidated resource path: /tmp/yak-ops/default/resources/..",
                () -> createFileFromContentDtoValidator.validate(renameDirectoryDto));
    }

    @Test
    public void testValidate_IsNotFile() {
        CreateFileFromContentDto createFileFromContentDto = CreateFileFromContentDto.builder()
                .loginUser(loginUser)
                .fileAbsolutePath("/tmp/yak-ops/default/resources/a")
                .fileContent("select * from t")
                .build();
        assertThrowServiceException(
                "Internal Server Error: The path is not a file: /tmp/yak-ops/default/resources/a",
                () -> createFileFromContentDtoValidator.validate(createFileFromContentDto));
    }

    @Test
    public void testValidate_fileAlreadyExist() {
        CreateFileFromContentDto createFileFromContentDto = CreateFileFromContentDto.builder()
                .loginUser(loginUser)
                .fileAbsolutePath("/tmp/yak-ops/default/resources/a.sql")
                .fileContent("select * from t")
                .build();
        when(storageOperator.exists(createFileFromContentDto.getFileAbsolutePath())).thenReturn(true);
        assertThrowServiceException(
                "Internal Server Error: The resource is already exist: /tmp/yak-ops/default/resources/a.sql",
                () -> createFileFromContentDtoValidator.validate(createFileFromContentDto));
    }

    @Test
    public void testValidate_fileNoPermission() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("test");
        when(tenantDao.queryOptionalById(loginUser.getTenantId())).thenReturn(Optional.of(tenant));

        CreateFileFromContentDto createFileFromContentDto = CreateFileFromContentDto.builder()
                .loginUser(loginUser)
                .fileAbsolutePath("/tmp/yak-ops/default/resources/a.sql")
                .fileContent("select * from t")
                .build();
        when(storageOperator.exists(createFileFromContentDto.getFileAbsolutePath())).thenReturn(false);
        when(storageOperator.getResourceMetaData(createFileFromContentDto.getFileAbsolutePath()))
                .thenReturn(ResourceMetadata.builder()
                        .resourceAbsolutePath(createFileFromContentDto.getFileAbsolutePath())
                        .resourceBaseDirectory(BASE_DIRECTORY)
                        .resourceRelativePath("a.sql")
                        .isDirectory(false)
                        .tenant("default")
                        .build());
        assertThrowServiceException(
                "Internal Server Error: The user's tenant is test have no permission to access the resource: /tmp/yak-ops/default/resources/a.sql",
                () -> createFileFromContentDtoValidator.validate(createFileFromContentDto));
    }

    @Test
    public void testValidate_contentIsInvalidated() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("default");
        when(tenantDao.queryOptionalById(loginUser.getTenantId())).thenReturn(Optional.of(tenant));

        CreateFileFromContentDto createFileFromContentDto = CreateFileFromContentDto.builder()
                .loginUser(loginUser)
                .fileAbsolutePath("/tmp/yak-ops/default/resources/a.sql")
                .fileContent("")
                .build();
        when(storageOperator.exists(createFileFromContentDto.getFileAbsolutePath())).thenReturn(false);
        when(storageOperator.getResourceMetaData(createFileFromContentDto.getFileAbsolutePath()))
                .thenReturn(ResourceMetadata.builder()
                        .resourceAbsolutePath(createFileFromContentDto.getFileAbsolutePath())
                        .resourceBaseDirectory(BASE_DIRECTORY)
                        .resourceRelativePath("a.sql")
                        .isDirectory(false)
                        .tenant("default")
                        .build());
        assertThrowServiceException(
                "Internal Server Error: The file content is null",
                () -> createFileFromContentDtoValidator.validate(createFileFromContentDto));
    }

    @Test
    public void testValidate() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("default");
        when(tenantDao.queryOptionalById(loginUser.getTenantId())).thenReturn(Optional.of(tenant));

        CreateFileFromContentDto createFileFromContentDto = CreateFileFromContentDto.builder()
                .loginUser(loginUser)
                .fileAbsolutePath("/tmp/yak-ops/default/resources/a.sql")
                .fileContent("select * from t")
                .build();
        when(storageOperator.exists(createFileFromContentDto.getFileAbsolutePath())).thenReturn(false);
        when(storageOperator.getResourceMetaData(createFileFromContentDto.getFileAbsolutePath()))
                .thenReturn(ResourceMetadata.builder()
                        .resourceAbsolutePath(createFileFromContentDto.getFileAbsolutePath())
                        .resourceBaseDirectory(BASE_DIRECTORY)
                        .resourceRelativePath("a.sql")
                        .isDirectory(false)
                        .tenant("default")
                        .build());
        assertDoesNotThrow(() -> createFileFromContentDtoValidator.validate(createFileFromContentDto));
    }
}
