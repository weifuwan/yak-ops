package io.yak.framework.security.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.security.common.dto.user.UserBriefQueryDTO;
import io.yak.framework.security.common.dto.user.UserQueryDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.common.entity.user.UserBrief;
import io.yak.framework.security.common.po.UserPO;

import java.util.List;

/**
 * 用户数据访问接口。
 *
 * <p>提供用户持久化、逻辑删除及用户简要信息查询能力。
 *
 * @author weifuwan
 */
public interface UserDao {
    int addUser(UserPO userPO);

    int editUser(UserPO userPO);

    IPage<User> selectPageByUserIdList(UserQueryDTO queryDTO,
                                       List<Long> userIdList);

    IPage<UserBrief> selectBriefPageByDeptIdList(UserBriefQueryDTO queryDTO,
                                                 List<Long> deptIdList);

    User selectByUserId(Long userId);

    User selectByUserMail(String email);

    User selectByUserPhone(String phone);

    boolean deleteByUserId(Long userId);

    List<UserBrief> selectBriefListByUserIdList(List<Long> userIdList);

    List<UserBrief> selectBriefListByNameAndDescOrderByCreateTime(String name);

    List<UserBrief> selectBriefListByDeptIdList(List<Long> deptIdList);

    List<UserBrief> selectBriefListOrderByCreateTime(boolean ascending);

    List<UserBrief> selectAllBriefList();

    /**
     * 根据用户名或真实姓名模糊查询用户 ID。
     *
     * @param name 用户名或真实姓名关键字
     * @return 匹配的用户 ID 列表，关键字为空时返回空列表
     */
    List<Long> selectUserIdListByUsernameOrRealName(String name);

    User selectByUsername(String username);
}
