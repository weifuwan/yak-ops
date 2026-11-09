package io.yak.ops.api.service.impl;

import static io.yak.ops.api.constants.ApiFuncIdentificationConstant.WORKFLOW_INSTANCE;

import io.yak.ops.api.service.AlertService;
import io.yak.ops.api.service.ProjectService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.common.enums.AlertStatus;
import io.yak.ops.common.enums.AlertType;
import io.yak.ops.dao.AlertDao;
import io.yak.ops.dao.entity.Alert;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.ProjectDao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service
public class AlertServiceImpl extends BaseServiceImpl implements AlertService {

    @Autowired
    private AlertDao alertDao;

    @Autowired
    private ProjectDao projectDao;

    @Autowired
    private ProjectService projectService;

    @Override
    public PageInfo<Alert> listPaging(User loginUser,
                                      long projectCode,
                                      AlertStatus alertStatus,
                                      AlertType alertType,
                                      String keyword,
                                      int pageNo,
                                      int pageSize) {
        Project project = projectDao.queryByCode(projectCode);
        projectService.checkProjectAndAuthThrowException(loginUser, project, WORKFLOW_INSTANCE);

        Page<Alert> page = new Page<>(pageNo, pageSize);
        IPage<Alert> alerts = alertDao.queryAlertPage(
                page,
                projectCode,
                alertStatus == null ? null : alertStatus.getCode(),
                alertType == null ? null : alertType.getCode(),
                keyword);
        return PageInfo.of(alerts);
    }
}
