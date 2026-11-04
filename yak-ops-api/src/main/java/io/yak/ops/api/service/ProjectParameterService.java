package io.yak.ops.api.service;

import io.yak.ops.api.utils.Result;
import io.yak.ops.dao.entity.User;

public interface ProjectParameterService {

    Result createProjectParameter(User loginUser, long projectCode, String projectParameterName,
                                  String projectParameterValue, String projectParameterDataType);

    Result updateProjectParameter(User loginUser, long projectCode, long code, String projectParameterName,
                                  String projectParameterValue, String projectParameterDataType);

    Result deleteProjectParametersByCode(User loginUser, long projectCode, long code);

    Result batchDeleteProjectParametersByCodes(User loginUser, long projectCode, String codes);

    Result queryProjectParameterListPaging(User loginUser, long projectCode, Integer pageSize, Integer pageNo,
                                           String searchVal, String projectParameterDataType);

    Result queryProjectParameterByCode(User loginUser, long projectCode, long code);
}
