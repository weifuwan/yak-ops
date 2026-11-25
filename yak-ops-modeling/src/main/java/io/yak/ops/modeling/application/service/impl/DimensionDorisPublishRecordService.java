package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.DimensionDorisPublishPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DorisPublishStatusEnum;
import io.yak.ops.dao.repository.modeling.DimensionDorisPublishDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import java.util.Arrays;
import java.util.Date;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns short database transactions for Doris publish-record lifecycle and concurrency control. */
@Service
public class DimensionDorisPublishRecordService {

    @Resource
    private DimensionTableDao dimensionTableDao;

    @Resource
    private DimensionDorisPublishDao dimensionDorisPublishDao;

    @Transactional(rollbackFor = Exception.class)
    public DimensionDorisPublishPO createRunning(
            String tableId, DimensionDorisPublishPO publish, Date startedAt) {
        DimensionTablePO lockedTable = dimensionTableDao.queryOne(
                new QueryWrapper<DimensionTablePO>().eq("id", tableId).last("FOR UPDATE"));
        if (lockedTable == null) {
            throw new IllegalArgumentException("维度建模记录不存在或不属于当前项目");
        }
        if (!Boolean.TRUE.equals(lockedTable.getSubmit())) {
            throw new IllegalArgumentException("请先提交维度模型，再发布 Doris 建表");
        }
        if (lockedTable.getTableType() == null
                || lockedTable.getTableType() == DimensionTableTypeEnum.DIM) {
            throw new IllegalArgumentException("当前模型不是可发布的 Doris 物理表");
        }
        long inProgressCount = dimensionDorisPublishDao.queryCount(
                new QueryWrapper<DimensionDorisPublishPO>()
                        .eq("table_id", tableId)
                        .in("status", Arrays.asList(
                                DorisPublishStatusEnum.PENDING, DorisPublishStatusEnum.RUNNING)));
        if (inProgressCount > 0) {
            throw new IllegalArgumentException("当前模型已有 Doris 发布任务正在执行，请稍后重试");
        }
        publish.setStatus(DorisPublishStatusEnum.RUNNING);
        publish.setStartedAt(startedAt);
        if (dimensionDorisPublishDao.insert(publish) <= 0) {
            throw new IllegalStateException("创建 Doris 发布记录失败");
        }
        return publish;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePhysicalSchema(String publishId, String physicalSchema, String operator) {
        DimensionDorisPublishPO publish = dimensionDorisPublishDao.queryById(publishId);
        if (publish == null) {
            throw new IllegalArgumentException("Doris 发布记录不存在: " + publishId);
        }
        publish.setPhysicalSchema(physicalSchema);
        publish.initUpdate();
        publish.setUpdateBy(StringUtils.defaultIfBlank(operator, "sys"));
        if (!dimensionDorisPublishDao.updateById(publish)) {
            throw new IllegalStateException("保存 Doris 物理结构基线失败: " + publishId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(
            String publishId,
            DorisPublishStatusEnum status,
            String errorMessage,
            Date startedAt,
            Date finishedAt,
            Long executionDurationMs,
            String operator) {
        DimensionDorisPublishPO publish = dimensionDorisPublishDao.queryById(publishId);
        if (publish == null) {
            throw new IllegalArgumentException("Doris 发布记录不存在: " + publishId);
        }
        publish.setStatus(status);
        publish.setErrorMessage(StringUtils.left(errorMessage, 4000));
        if (startedAt != null) {
            publish.setStartedAt(startedAt);
        }
        if (finishedAt != null) {
            publish.setFinishedAt(finishedAt);
        }
        if (executionDurationMs != null) {
            publish.setExecutionDurationMs(executionDurationMs);
        }
        publish.initUpdate();
        publish.setUpdateBy(StringUtils.defaultIfBlank(operator, "sys"));
        if (!dimensionDorisPublishDao.updateById(publish)) {
            throw new IllegalStateException("更新 Doris 发布状态失败: " + publishId);
        }
    }
}
