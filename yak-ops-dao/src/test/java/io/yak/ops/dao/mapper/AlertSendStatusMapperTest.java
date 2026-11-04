/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
