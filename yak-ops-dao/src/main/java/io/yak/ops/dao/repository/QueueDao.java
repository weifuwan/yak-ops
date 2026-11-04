package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.Queue;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface QueueDao extends IDao<Queue> {

    IPage<Queue> queryQueuePaging(IPage<Queue> page, List<Integer> ids, String searchVal);

    boolean existQueue(String queue, String queueName);

    Queue queryQueueName(String queue, String queueName);
}
