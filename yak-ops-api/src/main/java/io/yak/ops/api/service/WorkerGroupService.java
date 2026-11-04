package io.yak.ops.api.service;

import io.yak.ops.api.utils.Result;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkerGroup;
import io.yak.ops.dao.entity.WorkerGroupPageDetail;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface WorkerGroupService {

    /**
     * Create or update a worker group
     *
     * @param loginUser login user
     * @param id worker group id
     * @param name worker group name
     * @param addrList addr list
     * @param description   description
     * @return create or update result code
     */
    WorkerGroup saveWorkerGroup(User loginUser, int id, String name, String addrList, String description);

    /**
     * Query worker group paging
     *
     * @param loginUser login user
     * @param pageNo page number
     * @param searchVal search value
     * @param pageSize page size
     * @return worker group list page
     */
    Result queryAllGroupPaging(User loginUser, Integer pageNo, Integer pageSize, String searchVal);

    /**
     * Query all worker group
     *
     * @param loginUser login user
     * @return distinct worker group names available to the user
     */
    List<String> queryAllGroup(User loginUser);

    /**
     * Delete worker group by id
     * @param loginUser login user
     * @param id worker group id
     */
    void deleteWorkerGroupById(User loginUser, Integer id);

    /**
     * Query all worker address list
     *
     * @return all worker address set
     */
    Set<String> getWorkerAddressList();

    /**
     * Query worker group by workflow definition codes
     * @param workflowDefinitionCodeList workflowDefinitionCodeList
     * @return worker group map
     */
    Map<Long, String> queryWorkerGroupByWorkflowDefinitionCodes(List<Long> workflowDefinitionCodeList);

    List<WorkerGroupPageDetail> getConfigWorkerGroupPageDetail();

}
