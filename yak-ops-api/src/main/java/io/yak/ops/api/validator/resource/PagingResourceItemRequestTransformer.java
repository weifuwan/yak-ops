package io.yak.ops.api.validator.resource;

import static com.google.common.base.Preconditions.checkNotNull;

import io.yak.ops.api.dto.resources.PagingResourceItemRequest;
import io.yak.ops.api.dto.resources.QueryResourceDto;
import io.yak.ops.api.validator.ITransformer;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.Tenant;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;
import io.yak.ops.spi.enums.ResourceType;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class PagingResourceItemRequestTransformer implements ITransformer<PagingResourceItemRequest, QueryResourceDto> {

    private final StorageOperator storageOperator;

    private final TenantDao tenantDao;

    @Override
    public QueryResourceDto transform(PagingResourceItemRequest pagingResourceItemRequest) {
        validatePagingResourceItemRequest(pagingResourceItemRequest);

        if (StringUtils.isNotEmpty(pagingResourceItemRequest.getResourceAbsolutePath())) {
            // query from the given path
            return QueryResourceDto.builder()
                    .resourceAbsolutePaths(Lists.newArrayList(pagingResourceItemRequest.getResourceAbsolutePath()))
                    .build();
        }

        ResourceType resourceType = pagingResourceItemRequest.getResourceType();
        User loginUser = pagingResourceItemRequest.getLoginUser();
        if (loginUser.getUserType() == UserType.ADMIN_USER) {
            // If the current user is admin
            // then will query all tenant resources
            List<String> resourceAbsolutePaths = tenantDao.queryAll()
                    .stream()
                    .map(tenant -> storageOperator.getStorageBaseDirectory(tenant.getTenantCode(), resourceType))
                    .collect(Collectors.toList());
            return QueryResourceDto.builder()
                    .resourceAbsolutePaths(resourceAbsolutePaths)
                    .build();
        } else {
            // todo: inject the tenantCode when login
            Tenant tenant = tenantDao.queryById(loginUser.getTenantId());
            String storageBaseDirectory = storageOperator.getStorageBaseDirectory(tenant.getTenantCode(), resourceType);
            return QueryResourceDto.builder()
                    .resourceAbsolutePaths(Lists.newArrayList(storageBaseDirectory))
                    .build();
        }

    }

    private void validatePagingResourceItemRequest(PagingResourceItemRequest pagingResourceItemRequest) {
        checkNotNull(pagingResourceItemRequest.getLoginUser(), "loginUser is null");
        checkNotNull(pagingResourceItemRequest.getResourceType(), "resourceType is null");
    }

}
