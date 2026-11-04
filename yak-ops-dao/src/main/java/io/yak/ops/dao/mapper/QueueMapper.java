package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.Queue;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface QueueMapper extends BaseMapper<Queue> {

    /**
     * queue page
     * @param page page
     * @param searchVal searchVal
     * @return queue IPage
     */
    IPage<Queue> queryQueuePaging(IPage<Queue> page, @Param("ids") List<Integer> ids,
                                  @Param("searchVal") String searchVal);

    /**
     *  query all queue list
     * @param queue queue
     * @param queueName queueName
     * @return queue list
     */
    List<Queue> queryAllQueueList(@Param("queue") String queue,
                                  @Param("queueName") String queueName);

    /**
     * check the target queue exist
     * @param queue queue
     * @param queueName queueName
     * @return true if exist else return null
     */
    Boolean existQueue(@Param("queue") String queue, @Param("queueName") String queueName);

    /**
     * query simple queue object by queue name and queue
     * @param queue queue
     * @param queueName queueName
     * @return queue object
     */
    Queue queryQueueName(@Param("queue") String queue, @Param("queueName") String queueName);
}
