package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.Queue;
import io.yak.ops.dao.mapper.QueueMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.QueueDao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.NonNull;

@Repository
public class QueueDaoImpl extends BaseDao<Queue, QueueMapper> implements QueueDao {

    public QueueDaoImpl(@NonNull QueueMapper queueMapper) {
        super(queueMapper);
    }

    @Override
    public IPage<Queue> queryQueuePaging(IPage<Queue> page, List<Integer> ids, String searchVal) {
        return mybatisMapper.queryQueuePaging(page, ids, searchVal);
    }

    @Override
    public boolean existQueue(String queue, String queueName) {
        return Boolean.TRUE.equals(mybatisMapper.existQueue(queue, queueName));
    }

    @Override
    public Queue queryQueueName(String queue, String queueName) {
        return mybatisMapper.queryQueueName(queue, queueName);
    }
}
