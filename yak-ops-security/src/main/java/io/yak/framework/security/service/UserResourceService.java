package io.yak.framework.security.service;

import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.resource.AssignToManyUserDTO;
import io.yak.framework.security.common.dto.resource.AssignToOneUserDTO;
import io.yak.framework.security.common.dto.resource.BatchAssignDTO;
import io.yak.framework.security.common.dto.resource.ControlLevelQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByRQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUDataQueryDTO;
import io.yak.framework.security.common.dto.resource.MByUQueryDTO;
import io.yak.framework.security.common.dto.resource.UserResourceQueryDTO;
import io.yak.framework.security.common.enums.resource.ControlLevelCode;
import io.yak.framework.security.common.vo.resource.MByRDataVO;
import io.yak.framework.security.common.vo.resource.MByRVO;
import io.yak.framework.security.common.vo.resource.MByUDataVO;
import io.yak.framework.security.common.vo.resource.MByUVO;
import io.yak.framework.security.exception.YakSecurityException;

import java.util.List;

/**
 * 用户资源权限服务接口。
 *
 * @author weifuwan
 */
public interface UserResourceService {

  int getResourceCntByUserId(
          Long userId,
          UserResourceQueryDTO queryDTO);

  PagingData<MByRVO> getManageByResourcePage(
          MByRQueryDTO queryDTO)
          throws YakSecurityException;

  PagingData<MByUVO> getManageByUserPage(
          MByUQueryDTO queryDTO);

  void assignResourcePermission(
          AssignToOneUserDTO assignDTO)
          throws YakSecurityException;

  void assignResourcePermission(
          AssignToManyUserDTO assignDTO)
          throws YakSecurityException;

  void batchAssignResourcePermission(
          BatchAssignDTO assignDTO)
          throws YakSecurityException;

  List<MByUDataVO> getManagerByUserDataList(
          MByUDataQueryDTO queryDTO)
          throws YakSecurityException;

  List<MByRDataVO> getManagerByResourceDataList(
          MByRDataQueryDTO queryDTO)
          throws YakSecurityException;

  boolean getViewPermissionControlStatus();

  /**
   * 显式设置资源查看权限控制状态。
   *
   * <p>默认实现兼容旧的“切换”语义；新的实现可以覆盖此方法，避免并发请求
   * 导致状态被重复翻转。</p>
   *
   * @param enabled 是否开启查看权限控制
   */
  default void setViewPermissionControlStatus(boolean enabled) {
    if (getViewPermissionControlStatus() != enabled) {
      changeResourceViewControlStatus();
    }
  }

  void changeResourceViewControlStatus();

  ControlLevelCode getControlLevel(
          ControlLevelQueryDTO queryDTO)
          throws YakSecurityException;
}
