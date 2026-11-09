package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.Alert;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Mapper
public interface AlertMapper extends BaseMapper<Alert> {

    IPage<Alert> queryAlertPage(Page<Alert> page,
                                @Param("projectCode") long projectCode,
                                @Param("alertStatus") Integer alertStatus,
                                @Param("alertType") Integer alertType,
                                @Param("keyword") String keyword);


    /**
     * Query the alert which id > minAlertId and status = alertStatus order by id asc.
     */
    List<Alert> listingAlertByStatus(@Param("minAlertId") int minAlertId, @Param("alertStatus") int alertStatus,
                                     @Param("limit") int limit);

    /**
     * Insert server crash alert
     * <p>This method will ensure that there is at most one unsent alert which has the same content in the database.
     */
    void insertAlertWhenServerCrash(@Param("alert") Alert alert,
                                    @Param("crashAlarmSuppressionStartTime") Date crashAlarmSuppressionStartTime);

    void deleteByWorkflowInstanceId(@Param("workflowInstanceId") Integer processInstanceId);

    List<Alert> selectByWorkflowInstanceId(@Param("workflowInstanceId") Integer processInstanceId);
}
