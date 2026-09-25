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

/**
 * 提供当前用户管理 API 所需的查询、维护、密码管理和会话联动能力。
 *
 * <p>Boot 只依赖该稳定 Contract，不直接访问 UserRepository 或 UserServiceImpl。</p>
 *
 * @author weifuwan
 * @since 2026-09-24
 */
public interface UserService {

    /**
     * 校验用户名、手机号或邮箱等字段是否满足格式和唯一性要求。
     *
     * @param checkType 校验字段类型编码
     * @param checkValue 待校验值
     */
    Result<Void> check(Integer checkType, String checkValue);

    PagingData<UserVO> getUserPage(UserQueryDTO queryDTO);

    UserVO getUserDetailByUserId(String userId);

    /**
     * 删除指定用户，并拒绝当前登录用户删除自身。
     *
     * @param userId 目标用户 ID
     * @param operatorId 当前登录用户 ID
     * @param operator 当前登录用户名
     */
    Result<Void> deleteByUserId(String userId, String operatorId, String operator);

    /**
     * 管理员重置指定用户密码，并清理该用户现有登录态。
     *
     * @param userId 目标用户 ID
     * @param request 新密码参数
     * @param operator 操作人用户名
     */
    Result<Void> resetPassword(String userId, UserPasswordResetDTO request, String operator);

    UserBriefVO getUserBriefByUsername(String username);

    UserAccount getUserByUsername(String username);

    List<UserBriefVO> getUserBriefListByUserIds(List<String> userIds);

    List<UserBriefVO> searchUserBriefList(String keyword);

    List<UserBriefVO> getAllUserBriefList();

    /**
     * 创建用户并记录操作人。
     *
     * @param userDTO 用户信息
     * @param operator 操作人用户名
     */
    Result<Void> addUser(UserDTO userDTO, String operator);

    /**
     * 更新用户信息；修改密码时同步清理该用户现有登录态。
     *
     * @param userDTO 用户信息
     * @param operator 操作人用户名
     */
    Result<Void> editUser(UserDTO userDTO, String operator);

    Result<List<UserVO>> getUserDetailsByUserIds(List<String> userIds);
}
