package io.yak.ops.api.validator.resource;

import static io.yak.ops.api.AssertionsHelper.assertDoesNotThrow;
import static io.yak.ops.api.AssertionsHelper.assertThrowServiceException;
import static org.mockito.Mockito.when;

import io.yak.ops.api.dto.resources.RenameDirectoryDto;
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
class RenameDirectoryDtoValidatorTest {

    @Mock
    private StorageOperator storageOperator;

    @Mock
    private TenantDao tenantDao;

    @InjectMocks
    private RenameDirectoryDtoValidator renameDirectoryDtoValidator;

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
        RenameDirectoryDto renameDirectoryDto = RenameDirectoryDto.builder()
                .loginUser(loginUser)
                .originDirectoryAbsolutePath("/tmp")
                .targetDirectoryAbsolutePath("/tmp1")
                .build();
        assertThrowServiceException(
                "Internal Server Error: Invalidated resource path: /tmp",
                () -> renameDirectoryDtoValidator.validate(renameDirectoryDto));

    }

    @Test
    public void testValidate_directoryPathContainsIllegalSymbolic() {
        RenameDirectoryDto renameDirectoryDto = RenameDirectoryDto.builder()
                .loginUser(loginUser)
                .originDirectoryAbsolutePath("/tmp/yak-ops/default/resources/..")
                .targetDirectoryAbsolutePath("/tmp/yak-ops/default/resources/a")
                .build();
        assertThrowServiceException(
                "Internal Server Error: Invalidated resource path: /tmp/yak-ops/default/resources/..",
                () -> renameDirectoryDtoValidator.validate(renameDirectoryDto));
    }

    @Test
    public void testValidate_originDirectoryNotExist() {
        RenameDirectoryDto renameDirectoryDto = RenameDirectoryDto.builder()
                .loginUser(loginUser)
                .originDirectoryAbsolutePath("/tmp/yak-ops/default/resources/a")
                .targetDirectoryAbsolutePath("/tmp/yak-ops/default/resources/b")
                .build();
        assertThrowServiceException(
                "Internal Server Error: Thr resource is not exists: /tmp/yak-ops/default/resources/a",
                () -> renameDirectoryDtoValidator.validate(renameDirectoryDto));
    }

    @Test
    public void testValidate_originDirectoryNoPermission() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("test");
        when(tenantDao.queryOptionalById(loginUser.getTenantId())).thenReturn(Optional.of(tenant));

        RenameDirectoryDto renameDirectoryDto = RenameDirectoryDto.builder()
                .loginUser(loginUser)
                .originDirectoryAbsolutePath("/tmp/yak-ops/default/resources/a")
                .targetDirectoryAbsolutePath("/tmp/yak-ops/default/resources/b")
                .build();
        when(storageOperator.exists(renameDirectoryDto.getOriginDirectoryAbsolutePath())).thenReturn(true);
        when(storageOperator.getResourceMetaData(renameDirectoryDto.getOriginDirectoryAbsolutePath()))
                .thenReturn(ResourceMetadata.builder()
                        .resourceAbsolutePath(renameDirectoryDto.getOriginDirectoryAbsolutePath())
                        .resourceBaseDirectory(BASE_DIRECTORY)
                        .resourceRelativePath("a")
                        .isDirectory(true)
                        .tenant("default")
                        .build());
        assertThrowServiceException(
                "Internal Server Error: The user's tenant is test have no permission to access the resource: /tmp/yak-ops/default/resources/a",
                () -> renameDirectoryDtoValidator.validate(renameDirectoryDto));
    }

    @Test
    public void testValidate_targetDirectoryAlreadyExist() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("default");
        when(tenantDao.queryOptionalById(loginUser.getTenantId())).thenReturn(Optional.of(tenant));

        RenameDirectoryDto renameDirectoryDto = RenameDirectoryDto.builder()
                .loginUser(loginUser)
                .originDirectoryAbsolutePath("/tmp/yak-ops/default/resources/a")
                .targetDirectoryAbsolutePath("/tmp/yak-ops/default/resources/b")
                .build();
        when(storageOperator.exists(renameDirectoryDto.getOriginDirectoryAbsolutePath())).thenReturn(true);
        when(storageOperator.getResourceMetaData(renameDirectoryDto.getOriginDirectoryAbsolutePath()))
                .thenReturn(ResourceMetadata.builder()
                        .resourceAbsolutePath(renameDirectoryDto.getOriginDirectoryAbsolutePath())
                        .resourceBaseDirectory(BASE_DIRECTORY)
                        .resourceRelativePath("a")
                        .isDirectory(true)
                        .tenant("default")
                        .build());

        when(storageOperator.exists(renameDirectoryDto.getTargetDirectoryAbsolutePath())).thenReturn(true);
        assertThrowServiceException(
                "Internal Server Error: The resource is already exist: /tmp/yak-ops/default/resources/b",
                () -> renameDirectoryDtoValidator.validate(renameDirectoryDto));
    }

    @Test
    public void testValidate() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("default");
        when(tenantDao.queryOptionalById(loginUser.getTenantId())).thenReturn(Optional.of(tenant));

        RenameDirectoryDto renameDirectoryDto = RenameDirectoryDto.builder()
                .loginUser(loginUser)
                .originDirectoryAbsolutePath("/tmp/yak-ops/default/resources/a")
                .targetDirectoryAbsolutePath("/tmp/yak-ops/default/resources/b")
                .build();
        when(storageOperator.exists(renameDirectoryDto.getOriginDirectoryAbsolutePath())).thenReturn(true);
        when(storageOperator.getResourceMetaData(renameDirectoryDto.getOriginDirectoryAbsolutePath()))
                .thenReturn(ResourceMetadata.builder()
                        .resourceAbsolutePath(renameDirectoryDto.getOriginDirectoryAbsolutePath())
                        .resourceBaseDirectory(BASE_DIRECTORY)
                        .resourceRelativePath("a")
                        .isDirectory(true)
                        .tenant("default")
                        .build());

        when(storageOperator.exists(renameDirectoryDto.getTargetDirectoryAbsolutePath())).thenReturn(false);
        when(storageOperator.getResourceMetaData(renameDirectoryDto.getTargetDirectoryAbsolutePath()))
                .thenReturn(ResourceMetadata.builder()
                        .resourceAbsolutePath(renameDirectoryDto.getOriginDirectoryAbsolutePath())
                        .resourceBaseDirectory(BASE_DIRECTORY)
                        .resourceRelativePath("b")
                        .isDirectory(true)
                        .tenant("default")
                        .build());

        assertDoesNotThrow(() -> renameDirectoryDtoValidator.validate(renameDirectoryDto));
    }
}
