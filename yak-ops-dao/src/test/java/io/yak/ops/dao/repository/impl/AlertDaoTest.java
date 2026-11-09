package io.yak.ops.dao.repository.impl;

import io.yak.ops.common.enums.AlertStatus;
import io.yak.ops.common.enums.AlertType;
import io.yak.ops.dao.AlertDao;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.Alert;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

class AlertDaoTest extends BaseDaoTest {

    @Autowired
    private AlertDao alertDao;

    @Test
    void testAlertDao() {
        Alert alert = new Alert();
        alert.setTitle("Mysql Exception");
        alert.setContent("[\"alarm time：2018-02-05\", \"service name：MYSQL_ALTER\", \"alarm name：MYSQL_ALTER_DUMP\", "
                + "\"get the alarm exception.！，interface error，exception information：timed out\", \"request address：http://blog.csdn.net/dreamInTheWorld/article/details/78539286\"]");
        alert.setAlertGroupId(1);
        alert.setAlertStatus(AlertStatus.WAIT_EXECUTION);
        alertDao.addAlert(alert);

        List<Alert> alerts = alertDao.listPendingAlerts(-1);
        Assertions.assertNotNull(alerts);
        Assertions.assertNotEquals(0, alerts.size());
    }


    @Test
    void testQueryAlertPage() {
        Alert alert = new Alert();
        alert.setTitle("Project workflow failed");
        alert.setContent("[{\"workflowInstanceName\":\"orders-sync\"}]");
        alert.setProjectCode(1001L);
        alert.setAlertGroupId(1);
        alert.setAlertStatus(AlertStatus.EXECUTION_FAILURE);
        alert.setAlertType(AlertType.WORKFLOW_INSTANCE_FAILURE);
        alertDao.addAlert(alert);

        IPage<Alert> page = alertDao.queryAlertPage(
                new Page<>(1, 10),
                1001L,
                AlertStatus.EXECUTION_FAILURE.getCode(),
                AlertType.WORKFLOW_INSTANCE_FAILURE.getCode(),
                "workflow");

        Assertions.assertEquals(1L, page.getTotal());
        Assertions.assertEquals("Project workflow failed", page.getRecords().get(0).getTitle());
    }

    @Test
    void testAddAlertSendStatus() {
        int insertCount = alertDao.addAlertSendStatus(AlertStatus.EXECUTION_SUCCESS, "success", 1, 1);
        Assertions.assertEquals(1, insertCount);
    }

    @Test
    void testSendServerStoppedAlert() {
        String host = "127.0.0.998165432";
        String serverType = "Master";
        alertDao.sendServerStoppedAlert(host, serverType);
        alertDao.sendServerStoppedAlert(host, serverType);
        long count = alertDao.listPendingAlerts(-1)
                .stream()
                .filter(alert -> alert.getContent().contains(host))
                .count();
        Assertions.assertEquals(1L, count);
    }
}
