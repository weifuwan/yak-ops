package io.yak.ops.dao.entity;

import java.util.Objects;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_ds_relation_workflow_instance")
public class WorkflowInstanceRelation {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private int parentWorkflowInstanceId;

    private int parentTaskInstanceId;

    private int workflowInstanceId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        WorkflowInstanceRelation that = (WorkflowInstanceRelation) o;

        if (!Objects.equals(id, that.id)) {
            return false;
        }
        if (parentWorkflowInstanceId != that.parentWorkflowInstanceId) {
            return false;
        }
        if (parentTaskInstanceId != that.parentTaskInstanceId) {
            return false;
        }
        return workflowInstanceId == that.workflowInstanceId;
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + parentWorkflowInstanceId;
        result = 31 * result + parentTaskInstanceId;
        result = 31 * result + workflowInstanceId;
        return result;
    }

}
