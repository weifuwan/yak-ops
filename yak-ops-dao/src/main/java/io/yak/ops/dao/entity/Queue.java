package io.yak.ops.dao.entity;

import java.util.Date;
import java.util.Objects;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_ds_queue")
public class Queue {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String queueName;
    /**
     * yarn queue name
     */
    private String queue;

    private Date createTime;
    private Date updateTime;

    public Queue() {
    }

    public Queue(String queueName, String queue) {
        Date now = new Date();
        this.queueName = queueName;
        this.queue = queue;
        this.createTime = now;
        this.updateTime = now;
    }

    public Queue(int id, String queueName, String queue) {
        Date now = new Date();
        this.id = id;
        this.queueName = queueName;
        this.queue = queue;
        this.createTime = now;
        this.updateTime = now;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Queue queue1 = (Queue) o;

        if (!Objects.equals(id, queue1.id)) {
            return false;
        }
        if (!queueName.equals(queue1.queueName)) {
            return false;
        }
        return queue.equals(queue1.queue);
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + queueName.hashCode();
        result = 31 * result + queue.hashCode();
        return result;
    }
}
