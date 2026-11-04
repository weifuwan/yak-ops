package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.AlertSendStatus;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface AlertSendStatusMapper extends BaseMapper<AlertSendStatus> {

    int batchInsert(@Param("alertSendStatuses") List<AlertSendStatus> alertSendStatuses);

    void deleteByAlertIds(@Param("alertIds") List<Integer> alertIds);
}
