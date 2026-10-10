package io.yak.ops.dao.repository.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.task.InstanceStatus;
import io.yak.ops.common.enums.task.ScheduleTargetType;
import io.yak.ops.dao.entity.datasync.DataSyncAttemptEntity;
import io.yak.ops.dao.entity.datasync.DataSyncExecutionEventEntity;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.EventEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.entity.task.ScheduleEntity;
import org.junit.jupiter.api.Test;

/**
 * 保证通用Task与DATA_SYNC兼容投影读取同一组物理表和历史ID列。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
class TaskStorageMappingContractTest {

    @Test
    void shouldSharePhysicalTablesAcrossTaskAndDataSyncModels() throws Exception {
        assertEquals(InstanceEntity.class.getAnnotation(TableName.class).value(),
                DataSyncInstanceEntity.class.getAnnotation(TableName.class).value());
        assertEquals(AttemptEntity.class.getAnnotation(TableName.class).value(),
                DataSyncAttemptEntity.class.getAnnotation(TableName.class).value());
        assertEquals(EventEntity.class.getAnnotation(TableName.class).value(),
                DataSyncExecutionEventEntity.class.getAnnotation(TableName.class).value());
        assertEquals(ScheduleEntity.class.getAnnotation(TableName.class).value(),
                DataSyncScheduleEntity.class.getAnnotation(TableName.class).value());

        assertEquals("instance_id",
                DataSyncAttemptEntity.class.getDeclaredField("executionId")
                        .getAnnotation(TableField.class).value());
        assertEquals("instance_id",
                DataSyncExecutionEventEntity.class.getDeclaredField("executionId")
                        .getAnnotation(TableField.class).value());
        assertEquals("target_id",
                DataSyncScheduleEntity.class.getDeclaredField("taskId")
                        .getAnnotation(TableField.class).value());
        assertEquals(ScheduleTargetType.TASK, new DataSyncScheduleEntity().getTargetType());
        assertEquals("DATA_SYNC", new DataSyncInstanceEntity().getTaskType());
        assertEquals(7, InstanceStatus.RETRY_WAITING.getValue());
    }
}
