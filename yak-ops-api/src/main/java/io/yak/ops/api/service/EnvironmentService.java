package io.yak.ops.api.service;

import io.yak.ops.api.dto.EnvironmentDto;
import io.yak.ops.api.utils.Result;
import io.yak.ops.dao.entity.Environment;
import io.yak.ops.dao.entity.User;

import java.util.List;

public interface EnvironmentService {

    /**
     * create environment
     *
     * @param loginUser login user
     * @param name environment name
     * @param config environment config
     * @param desc environment desc
     * @param workerGroups worker groups
     */
    Long createEnvironment(User loginUser, String name, String config, String desc, String workerGroups);

    /**
     * query environment
     *
     * @param name environment name
     */
    EnvironmentDto queryEnvironmentByName(String name);

    /**
     * query environment
     *
     * @param code environment code
     */
    EnvironmentDto queryEnvironmentByCode(Long code);

    /**
     * delete environment
     *
     * @param loginUser login user
     * @param code environment code
     */
    void deleteEnvironmentByCode(User loginUser, Long code);

    /**
     * update environment
     *
     * @param loginUser login user
     * @param code environment code
     * @param name environment name
     * @param config environment config
     * @param desc environment desc
     * @param workerGroups worker groups
     */
    Environment updateEnvironmentByCode(User loginUser, Long code, String name, String config, String desc,
                                        String workerGroups);

    /**
     * query environment paging
     *
     * @param pageNo page number
     * @param searchVal search value
     * @param pageSize page size
     * @return environment list page
     */
    Result queryEnvironmentListPaging(User loginUser, Integer pageNo, Integer pageSize, String searchVal);

    /**
     * query all environment
     *
     * @param loginUser
     */
    List<EnvironmentDto> queryAllEnvironmentList(User loginUser);

    /**
     * verify environment name
     *
     * @param environmentName environment name
     */
    void verifyEnvironment(String environmentName);

}
