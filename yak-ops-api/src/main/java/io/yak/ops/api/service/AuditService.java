package io.yak.ops.api.service;

import io.yak.ops.api.dto.AuditDto;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.dao.entity.AuditLog;
import io.yak.ops.dao.entity.User;

/**
 * audit information service
 */
public interface AuditService {

    /**
     * add audit object
     *
     * @param auditLog         auditLog
     */
    void addAudit(AuditLog auditLog);

    /**
     * query audit log list
     *
     * @param loginUser           login user
     * @param modelTypes          model types
     * @param modelName           model name
     * @param operationTypes      operation types
     * @param startTime           start time
     * @param endTime             end time
     * @param userName            query user name
     * @param pageNo              page number
     * @param pageSize            page size
     * @return                    audit log string
     */
    PageInfo<AuditDto> queryLogListPaging(User loginUser,
                                          String modelTypes,
                                          String operationTypes, String startTime,
                                          String endTime, String userName, String modelName,
                                          Integer pageNo, Integer pageSize);

}
