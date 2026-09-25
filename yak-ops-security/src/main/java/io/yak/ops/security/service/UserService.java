package io.yak.ops.security.service;

import io.yak.ops.common.bean.dto.security.user.UserDTO;
import io.yak.ops.common.bean.dto.security.user.UserPasswordResetDTO;
import io.yak.ops.common.bean.dto.security.user.UserQueryDTO;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.ops.common.bean.vo.security.user.UserVO;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.model.UserAccount;
import java.util.List;

/** UserAccount management capability used by the current user/login HTTP APIs. */
public interface UserService {

    Result<Void> check(Integer checkType, String checkValue);

    PagingData<UserVO> getUserPage(UserQueryDTO queryDTO);

    UserVO getUserDetailByUserId(String userId);

    Result<Void> deleteByUserId(String userId, String operatorId, String operator);

    Result<Void> resetPassword(String userId, UserPasswordResetDTO request, String operator);

    UserBriefVO getUserBriefByUsername(String username);

    UserAccount getUserByUsername(String username);

    List<UserBriefVO> getUserBriefListByUserIds(List<String> userIds);

    List<UserBriefVO> searchUserBriefList(String keyword);

    List<UserBriefVO> getAllUserBriefList();

    Result<Void> addUser(UserDTO userDTO, String operator);

    Result<Void> editUser(UserDTO userDTO, String operator);

    Result<List<UserVO>> getUserDetailsByUserIds(List<String> userIds);
}
