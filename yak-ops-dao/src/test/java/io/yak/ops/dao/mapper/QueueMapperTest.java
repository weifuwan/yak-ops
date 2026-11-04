package io.yak.ops.dao.mapper;

import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.Queue;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public class QueueMapperTest extends BaseDaoTest {

    @Autowired
    private QueueMapper queueMapper;

    /**
     * insert
     *
     * @return Queue
     */
    private Queue insertOne() {
        // insertOne
        Queue queue = new Queue();
        queue.setQueueName("queue");
        queue.setQueue("queue");
        queue.setCreateTime(new Date());
        queue.setUpdateTime(new Date());
        queueMapper.insert(queue);
        return queue;
    }

    /**
     * test update
     */
    @Test
    public void testUpdate() {
        // insertOne
        Queue queue = insertOne();
        queue.setCreateTime(new Date());
        // update
        int update = queueMapper.updateById(queue);
        Assertions.assertEquals(1, update);
    }

    /**
     * test delete
     */
    @Test
    public void testDelete() {
        Queue queue = insertOne();
        int delete = queueMapper.deleteById(queue.getId());
        Assertions.assertEquals(1, delete);
    }

    /**
     * test query
     */
    @Test
    public void testQuery() {
        Queue queue = insertOne();
        // query
        List<Queue> queues = queueMapper.selectList(null);
        Assertions.assertNotEquals(0, queues.size());
    }

    /**
     * test page
     */
    @Test
    public void testQueryQueuePaging() {

        Queue queue = insertOne();
        Page<Queue> page = new Page(1, 3);

        IPage<Queue> queueIPage = queueMapper.queryQueuePaging(page, Collections.singletonList(queue.getId()), null);
        Assertions.assertNotEquals(0, queueIPage.getTotal());

        queueIPage = queueMapper.queryQueuePaging(page, Collections.singletonList(queue.getId()), queue.getQueueName());
        Assertions.assertNotEquals(0, queueIPage.getTotal());
    }

    /**
     * test query all list
     */
    @Test
    public void queryAllQueueList() {
        Queue queue = insertOne();

        List<Queue> queues = queueMapper.queryAllQueueList(queue.getQueue(), null);
        Assertions.assertNotEquals(0, queues.size());

        queues = queueMapper.queryAllQueueList(null, queue.getQueueName());
        Assertions.assertNotEquals(0, queues.size());
    }

    @Test
    public void existQueue() {
        Assertions.assertNull(queueMapper.existQueue("queue", null));
        Assertions.assertNull(queueMapper.existQueue(null, "queue"));
        Queue queue = insertOne();
        Assertions.assertTrue(queueMapper.existQueue(queue.getQueue(), null) == Boolean.TRUE);
        Assertions.assertTrue(queueMapper.existQueue(null, queue.getQueueName()) == Boolean.TRUE);
    }
}
