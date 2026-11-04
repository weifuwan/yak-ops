package io.yak.ops.api.service;

import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.dao.entity.Queue;
import io.yak.ops.dao.entity.User;

import java.util.List;

public interface QueueService {

    /**
     * query queue list
     *
     * @param loginUser login user
     * @return queue list
     */
    List<Queue> queryList(User loginUser);

    /**
     * query queue list paging
     *
     * @param loginUser login user
     * @param pageNo    page number
     * @param searchVal search value
     * @param pageSize  page size
     * @return queue list
     */
    PageInfo<Queue> queryList(User loginUser, String searchVal, Integer pageNo, Integer pageSize);

    /**
     * create queue
     *
     * @param loginUser login user
     * @param queue     queue
     * @param queueName queue name
     * @return create result
     */
    Queue createQueue(User loginUser, String queue, String queueName);

    /**
     * update queue
     *
     * @param loginUser login user
     * @param queue     queue
     * @param id        queue id
     * @param queueName queue name
     * @return update result code
     */
    Queue updateQueue(User loginUser, int id, String queue, String queueName);

    /**
     * delete queue
     *
     * @param loginUser login user
     * @param id        queue id
     * @return delete result code
     * @throws Exception exception
     */
    void deleteQueueById(User loginUser, int id) throws Exception;

    /**
     * verify queue and queueName
     *
     * @param queue     queue
     * @param queueName queue name
     */
    void verifyQueue(String queue, String queueName);

    /**
     * Make sure queue with given name exists, and create the queue if not exists
     *
     * ONLY for python gateway server, and should not use this in web ui function
     *
     * @param queue queue value
     * @param queueName queue name
     * @return Queue object
     */
    Queue createQueueIfNotExists(String queue, String queueName);

}
