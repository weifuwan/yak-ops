package io.yak.ops.api.validator;

import io.yak.ops.dao.repository.TenantDao;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * This validator is used to validate whether the tenant exists.
 * <p> If the tenant does not exist, an {@link IllegalArgumentException} will be thrown. </p>
 */
@Slf4j
@Component
public class TenantExistValidator implements IValidator<String> {

    private final TenantDao tenantDao;

    public TenantExistValidator(TenantDao tenantDao) {
        this.tenantDao = tenantDao;
    }

    @Override
    public void validate(String tenantCode) {
        if (!tenantDao.queryByCode(tenantCode).isPresent()) {
            throw new IllegalArgumentException(String.format("Tenant: [%s] not exists", tenantCode));
        }
    }
}
