/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.api.validator.resource;

import io.yak.ops.api.dto.resources.FetchFileContentDto;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.TenantDao;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.springframework.stereotype.Component;

@Component
public class FetchFileContentDtoValidator extends AbstractResourceValidator<FetchFileContentDto> {

    public FetchFileContentDtoValidator(StorageOperator storageOperator, TenantDao tenantDao) {
        super(storageOperator, tenantDao);
    }

    @Override
    public void validate(FetchFileContentDto fetchFileContentDto) {
        if (fetchFileContentDto.getSkipLineNum() < 0) {
            throw new ServiceException("skipLineNum must be greater than or equal to 0");
        }
        if (fetchFileContentDto.getLimit() != -1 && fetchFileContentDto.getLimit() <= 0) {
            throw new ServiceException("limit must be -1 or greater than 0");
        }
        String resourceFileAbsolutePath = fetchFileContentDto.getResourceFileAbsolutePath();
        User loginUser = fetchFileContentDto.getLoginUser();

        exceptionResourceAbsolutePathInvalidated(resourceFileAbsolutePath);
        exceptionResourceIsNotFile(resourceFileAbsolutePath);
        exceptionUserNoResourcePermission(loginUser, resourceFileAbsolutePath);
        exceptionFileContentCannotFetch(resourceFileAbsolutePath);
    }
}
