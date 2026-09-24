package io.yak.framework.security.service;

import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.ops.common.bean.dto.security.user.UserDTO;
import io.yak.ops.common.bean.dto.security.user.UserQueryDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.ops.common.bean.vo.security.user.UserVO;
import java.util.List;

/** User management capability used by the current user/login HTTP APIs. */
public interface UserService {

  Result<Void> check(Integer checkType, String checkValue);

  PagingData<UserVO> getUserPage(UserQueryDTO queryDTO);

  UserVO getUserDetailByUserId(Long userId);

  Result<Void> deleteByUserId(Long userId);

  UserBriefVO getUserBriefByUsername(String username);

  User getUserByUsername(String username);

  List<UserBriefVO> getUserBriefListByUserIds(List<Long> userIds);

  List<UserBriefVO> searchUserBriefList(String keyword);

  List<UserBriefVO> getAllUserBriefList();

  Result<Void> addUser(UserDTO userDTO, String operator);

  Result<Void> editUser(UserDTO userDTO, String operator);

  Result<List<UserVO>> getUserDetailsByUserIds(List<Long> userIds);
}
