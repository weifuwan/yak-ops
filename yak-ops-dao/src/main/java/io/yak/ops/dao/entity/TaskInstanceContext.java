package io.yak.ops.dao.entity;

import io.yak.ops.common.enums.ContextType;
import io.yak.ops.common.utils.JSONUtils;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("t_ds_task_instance_context")
public class TaskInstanceContext {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer taskInstanceId;

    private String context;

    private ContextType contextType;

    private Date createTime;

    private Date updateTime;

    public void setTaskInstanceContext(List<AbstractTaskInstanceContext> taskInstanceContexts) {
        this.context = JSONUtils.toJsonString(taskInstanceContexts);
    }

    public List<AbstractTaskInstanceContext> getTaskInstanceContext() {
        return JSONUtils.toList(context, AbstractTaskInstanceContext.class);
    }
}
