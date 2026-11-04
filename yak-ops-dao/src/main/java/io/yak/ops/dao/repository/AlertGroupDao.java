package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.AlertGroup;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface AlertGroupDao extends IDao<AlertGroup> {

    IPage<AlertGroup> queryAlertGroupPage(Page<AlertGroup> page, String groupName);

    IPage<AlertGroup> queryAlertGroupPageByIds(Page<AlertGroup> page, List<Integer> ids, String searchVal);

    List<AlertGroup> queryAllGroupList();

    List<AlertGroup> queryByUserId(int userId);

    List<String> queryInstanceIdsList();

    boolean existGroupName(String groupName);
}
