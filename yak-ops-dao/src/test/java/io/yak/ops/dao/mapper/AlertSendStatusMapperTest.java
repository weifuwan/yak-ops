package io.yak.ops.dao.mapper;

import io.yak.ops.common.enums.AlertStatus;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.AlertSendStatus;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class AlertSendStatusMapperTest extends BaseDaoTest {

    @Autowired
    private AlertSendStatusMapper alertSendStatusMapper;

    /**
     * test insert
     */
    @Test
    public void testInsert() {
        AlertSendStatus alertSendStatus = new AlertSendStatus();
        alertSendStatus.setAlertId(1);
        alertSendStatus.setAlertPluginInstanceId(1);
        alertSendStatus.setSendStatus(AlertStatus.EXECUTION_SUCCESS);
        alertSendStatus.setLog("success");
        alertSendStatus.setCreateTime(DateUtils.getCurrentDate());

        alertSendStatusMapper.insert(alertSendStatus);
        Assertions.assertThat(alertSendStatus.getId() > 0);
    }
}
