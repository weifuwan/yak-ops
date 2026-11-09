package io.yak.ops.api.service;

import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.common.enums.AlertStatus;
import io.yak.ops.common.enums.AlertType;
import io.yak.ops.dao.entity.Alert;
import io.yak.ops.dao.entity.User;

public interface AlertService {

    PageInfo<Alert> listPaging(User loginUser,
                               long projectCode,
                               AlertStatus alertStatus,
                               AlertType alertType,
                               String keyword,
                               int pageNo,
                               int pageSize);
}
