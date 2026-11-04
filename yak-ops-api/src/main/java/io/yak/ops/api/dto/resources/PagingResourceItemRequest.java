package io.yak.ops.api.dto.resources;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.dao.entity.User;
import io.yak.ops.spi.enums.ResourceType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PagingResourceItemRequest {

    private User loginUser;

    private String resourceAbsolutePath;

    private ResourceType resourceType;

    private String resourceNameKeyWord;

    Integer pageNo;

    Integer pageSize;

    public void checkPageNoAndPageSize() {
        if (pageNo <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, Constants.PAGE_NUMBER);
        }
        if (pageSize <= 0) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, Constants.PAGE_SIZE);
        }
    }

}
