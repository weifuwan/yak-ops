package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.security.common.dto.user.UserBriefQueryDTO;
import io.yak.framework.security.common.dto.user.UserQueryDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.common.entity.user.UserBrief;
import io.yak.framework.security.common.po.UserPO;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.dao.mapper.UserMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.DatabaseNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户数据访问实现。
 *
 * <p>负责用户信息的新增、修改、删除和查询。</p>
 *
 * @author weifuwan
 */
@Repository
@RequiredArgsConstructor
public class UserDaoImpl
        implements UserDao {

    private final UserMapper userMapper;

    /**
     * 判断权限范围是否明确为空。
     *
     * <p>{@code null} 表示不限制范围，空集合表示没有可访问数据。</p>
     */
    private static boolean isEmptyScope(List<?> values) {
        return values != null && values.isEmpty();
    }

    /**
     * 新增用户。
     *
     * @param userPO 用户持久化对象
     * @return 受影响行数
     */
    @Override
    public int addUser(UserPO userPO) {
        return userMapper.insert(userPO);
    }

    /**
     * 根据主键修改用户。
     *
     * @param userPO 用户持久化对象
     * @return 受影响行数
     */
    @Override
    public int editUser(UserPO userPO) {
        return userMapper.updateById(userPO);
    }

    /**
     * 根据用户范围分页查询用户。
     *
     * <p>{@code userIdList} 为 {@code null} 时不限制用户范围；
     * 为空集合时返回空分页，避免权限范围为空时查询全部用户。</p>
     *
     * @param queryDTO   查询条件
     * @param userIdList 用户标识范围
     * @return 用户分页数据
     */
    @Override
    public IPage<User> selectPageByUserIdList(
            UserQueryDTO queryDTO,
            List<Long> userIdList) {

        Page<UserPO> page = Page.of(
                queryDTO.getPage(),
                queryDTO.getSize()
        );

        if (isEmptyScope(userIdList)) {
            return CopyBeanUtil.copyPage(page, User.class);
        }

        LambdaQueryWrapper<UserPO> wrapper =
                Wrappers.<UserPO>lambdaQuery()
                        .eq(
                                queryDTO.getId() != null,
                                UserPO::getId,
                                queryDTO.getId()
                        )
                        .like(
                                StringUtils.hasText(queryDTO.getUserName()),
                                UserPO::getUserName,
                                queryDTO.getUserName()
                        )
                        .like(
                                StringUtils.hasText(queryDTO.getRealName()),
                                UserPO::getRealName,
                                queryDTO.getRealName()
                        )
                        .in(
                                userIdList != null,
                                UserPO::getId,
                                userIdList
                        )
                        .orderByDesc(UserPO::getCreateTime);

        IPage<UserPO> result =
                userMapper.selectPage(page, wrapper);

        return CopyBeanUtil.copyPage(result, User.class);
    }

    /**
     * 根据部门范围分页查询用户简要信息。
     *
     * @param queryDTO   查询条件
     * @param deptIdList 部门标识范围
     * @return 用户简要信息分页数据
     */
    @Override
    public IPage<UserBrief> selectBriefPageByDeptIdList(
            UserBriefQueryDTO queryDTO,
            List<Long> deptIdList) {

        Page<UserPO> page = Page.of(
                queryDTO.getPage(),
                queryDTO.getSize()
        );

        if (isEmptyScope(deptIdList)) {
            return CopyBeanUtil.copyPage(page, UserBrief.class);
        }

        LambdaQueryWrapper<UserPO> wrapper =
                briefQuery()
                        .like(
                                StringUtils.hasText(queryDTO.getUserName()),
                                UserPO::getUserName,
                                queryDTO.getUserName()
                        )
                        .like(
                                StringUtils.hasText(queryDTO.getRealName()),
                                UserPO::getRealName,
                                queryDTO.getRealName()
                        )
                        .in(
                                deptIdList != null,
                                UserPO::getDeptId,
                                deptIdList
                        )
                        .orderByDesc(UserPO::getCreateTime);

        IPage<UserPO> result =
                userMapper.selectPage(page, wrapper);

        return CopyBeanUtil.copyPage(result, UserBrief.class);
    }

    /**
     * 根据用户标识查询用户。
     *
     * @param userId 用户标识
     * @return 用户信息
     */
    @Override
    public User selectByUserId(Long userId) {
        if (userId == null) {
            return null;
        }

        return CopyBeanUtil.copy(
                userMapper.selectById(userId),
                User.class
        );
    }

    /**
     * 根据电子邮箱查询用户。
     *
     * @param email 电子邮箱
     * @return 用户信息
     */
    @Override
    public User selectByUserMail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }

        UserPO userPO = userMapper.selectOne(
                Wrappers.<UserPO>lambdaQuery()
                        .eq(UserPO::getEmail, email)
        );

        return CopyBeanUtil.copy(userPO, User.class);
    }

    /**
     * 根据手机号码查询用户。
     *
     * @param phone 手机号码
     * @return 用户信息
     */
    @Override
    public User selectByUserPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }

        UserPO userPO = userMapper.selectOne(
                Wrappers.<UserPO>lambdaQuery()
                        .eq(UserPO::getPhone, phone)
        );

        return CopyBeanUtil.copy(userPO, User.class);
    }

    /**
     * 根据用户标识删除用户。
     *
     * @param userId 用户标识
     * @return 是否删除成功
     */
    @Override
    public boolean deleteByUserId(Long userId) {
        return userId != null
                && userMapper.deleteById(userId) > 0;
    }

    /**
     * 根据用户标识列表查询用户简要信息。
     *
     * @param userIdList 用户标识列表
     * @return 用户简要信息列表
     */
    @Override
    public List<UserBrief> selectBriefListByUserIdList(
            List<Long> userIdList) {

        if (userIdList == null || userIdList.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        List<UserPO> users = userMapper.selectList(
                briefQuery()
                        .in(UserPO::getId, userIdList)
        );

        return CopyBeanUtil.copyList(users, UserBrief.class);
    }

    /**
     * 根据用户名或真实姓名查询用户简要信息。
     *
     * @param name 用户名或真实姓名
     * @return 用户简要信息列表
     */
    @Override
    public List<UserBrief>
    selectBriefListByNameAndDescOrderByCreateTime(
            String name) {

        LambdaQueryWrapper<UserPO> wrapper =
                briefQuery()
                        .and(
                                StringUtils.hasText(name),
                                nested -> nested
                                        .like(UserPO::getUserName, name)
                                        .or()
                                        .like(UserPO::getRealName, name)
                        )
                        .orderByDesc(UserPO::getCreateTime);

        return CopyBeanUtil.copyList(
                userMapper.selectList(wrapper),
                UserBrief.class
        );
    }

    /**
     * 根据部门范围查询用户简要信息。
     *
     * @param deptIdList 部门标识列表
     * @return 用户简要信息列表
     */
    @Override
    public List<UserBrief> selectBriefListByDeptIdList(
            List<Long> deptIdList) {

        if (isEmptyScope(deptIdList)) {
            return java.util.Collections.emptyList();
        }

        List<UserPO> users = userMapper.selectList(
                briefQuery()
                        .in(
                                deptIdList != null,
                                UserPO::getDeptId,
                                deptIdList
                        )
        );

        return CopyBeanUtil.copyList(users, UserBrief.class);
    }

    /**
     * 查询用户简要信息，并按创建时间排序。
     *
     * @param ascending 是否升序
     * @return 用户简要信息列表
     */
    @Override
    public List<UserBrief> selectBriefListOrderByCreateTime(
            boolean ascending) {

        List<UserPO> users = userMapper.selectList(
                briefQuery()
                        .orderBy(
                                true,
                                ascending,
                                UserPO::getCreateTime
                        )
        );

        return CopyBeanUtil.copyList(users, UserBrief.class);
    }

    /**
     * 查询全部用户简要信息。
     *
     * @return 用户简要信息列表
     */
    @Override
    public List<UserBrief> selectAllBriefList() {
        List<UserPO> users = userMapper.selectList(
                briefQuery()
                        .orderByAsc(UserPO::getId)
        );

        return CopyBeanUtil.copyList(users, UserBrief.class);
    }

    /**
     * 根据用户名或真实姓名查询用户标识。
     *
     * @param name 用户名或真实姓名
     * @return 用户标识列表
     */
    @Override
    public List<Long> selectUserIdListByUsernameOrRealName(
            String name) {

        if (!StringUtils.hasText(name)) {
            return java.util.Collections.emptyList();
        }

        return userMapper.selectObjs(
                Wrappers.<UserPO>lambdaQuery()
                        .select(UserPO::getId)
                        .and(
                                nested -> nested
                                        .like(
                                                UserPO::getUserName,
                                                name
                                        )
                                        .or()
                                        .like(
                                                UserPO::getRealName,
                                                name
                                        )
                        )
        )
                .stream()
                .map(DatabaseNumberUtils::toLong)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据用户名查询用户。
     *
     * @param username 用户名
     * @return 用户信息
     */
    @Override
    public User selectByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }

        UserPO userPO = userMapper.selectOne(
                Wrappers.<UserPO>lambdaQuery()
                        .eq(UserPO::getUserName, username)
        );

        return CopyBeanUtil.copy(userPO, User.class);
    }

    /**
     * 创建用户简要信息查询条件。
     */
    private LambdaQueryWrapper<UserPO> briefQuery() {
        return Wrappers.<UserPO>lambdaQuery()
                .select(
                        UserPO::getId,
                        UserPO::getUserName,
                        UserPO::getRealName,
                        UserPO::getDeptId
                );
    }
}