package io.yak.ops.dao.mapper;

import io.yak.ops.common.enums.AlertStatus;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.common.utils.DateUtils;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.Alert;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class AlertMapperTest extends BaseDaoTest {

    @Autowired
    private AlertMapper alertMapper;

    /**
     * test insert
     *
     * @return
     */
    @Test
    public void testInsert() {
        Alert expectedAlert = createAlert();
        Assertions.assertTrue(expectedAlert.getId() > 0);
    }

    /**
     * test select by id
     *
     * @return
     */
    @Test
    public void testSelectById() {
        Alert expectedAlert = createAlert();
        Alert actualAlert = alertMapper.selectById(expectedAlert.getId());
        Assertions.assertEquals(expectedAlert, actualAlert);
    }

    /**
     * test update
     */
    @Test
    public void testUpdate() {

        Alert expectedAlert = createAlert();

        expectedAlert.setAlertStatus(AlertStatus.EXECUTION_FAILURE);
        expectedAlert.setLog("error");
        expectedAlert.setUpdateTime(DateUtils.getCurrentDate());

        alertMapper.updateById(expectedAlert);

        Alert actualAlert = alertMapper.selectById(expectedAlert.getId());

        Assertions.assertEquals(expectedAlert, actualAlert);
    }

    /**
     * test delete
     */
    @Test
    public void testDelete() {
        Alert expectedAlert = createAlert();

        alertMapper.deleteById(expectedAlert.getId());

        Alert actualAlert = alertMapper.selectById(expectedAlert.getId());

        Assertions.assertNull(actualAlert);
    }

    /**
     * create alert map
     *
     * @param count       alert count
     * @param alertStatus alert status
     * @return alert map
     */
    private Map<Integer, Alert> createAlertMap(Integer count, AlertStatus alertStatus) {
        Map<Integer, Alert> alertMap = new HashMap<>();

        for (int i = 0; i < count; i++) {
            Alert alert = createAlert(alertStatus);
            alertMap.put(alert.getId(), alert);
        }
        return alertMap;
    }

    /**
     * create alert
     *
     * @return alert
     * @throws Exception
     */
    private Alert createAlert() {
        return createAlert(AlertStatus.WAIT_EXECUTION);
    }

    /**
     * create alert
     *
     * @param alertStatus alert status
     * @return alert
     */
    private Alert createAlert(AlertStatus alertStatus) {
        String content = "[{'type':'WORKER','host':'192.168.xx.xx','event':'server down','warning level':'serious'}]";
        Alert alert = new Alert();
        alert.setTitle("test alert");
        alert.setContent(content);
        alert.setSign(DigestUtils.sha1Hex(content));
        alert.setAlertStatus(alertStatus);
        alert.setWarningType(WarningType.FAILURE);
        alert.setLog("success");
        alert.setCreateTime(DateUtils.getCurrentDate());
        alert.setUpdateTime(DateUtils.getCurrentDate());

        alertMapper.insert(alert);
        return alert;
    }
}
