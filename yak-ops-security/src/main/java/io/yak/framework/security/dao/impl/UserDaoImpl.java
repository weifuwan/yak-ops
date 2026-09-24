package io.yak.framework.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.security.common.dto.user.UserQueryDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.common.po.UserPO;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.dao.mapper.UserMapper;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** User persistence implementation. */
@Repository
@RequiredArgsConstructor
public class UserDaoImpl implements UserDao {

  private final UserMapper userMapper;

  @Override
  public int addUser(UserPO userPO) {
    return userMapper.insert(userPO);
  }

  @Override
  public int editUser(UserPO userPO) {
    return userMapper.updateById(userPO);
  }

  @Override
  public IPage<User> selectPage(UserQueryDTO queryDTO) {
    UserQueryDTO query = queryDTO == null ? new UserQueryDTO() : queryDTO;
    Page<UserPO> page = Page.of(query.getPage(), query.getSize());
    LambdaQueryWrapper<UserPO> wrapper = Wrappers.<UserPO>lambdaQuery()
        .eq(query.getId() != null, UserPO::getId, query.getId())
        .like(StringUtils.hasText(query.getUserName()), UserPO::getUserName, query.getUserName())
        .like(StringUtils.hasText(query.getRealName()), UserPO::getRealName, query.getRealName())
        .orderByDesc(UserPO::getCreateTime);
    return CopyBeanUtil.copyPage(userMapper.selectPage(page, wrapper), User.class);
  }

  @Override
  public User selectByUserId(Long userId) {
    return userId == null ? null : CopyBeanUtil.copy(userMapper.selectById(userId), User.class);
  }

  @Override
  public User selectByUserMail(String email) {
    if (!StringUtils.hasText(email)) return null;
    UserPO value = userMapper.selectOne(
        Wrappers.<UserPO>lambdaQuery().eq(UserPO::getEmail, email));
    return CopyBeanUtil.copy(value, User.class);
  }

  @Override
  public User selectByUserPhone(String phone) {
    if (!StringUtils.hasText(phone)) return null;
    UserPO value = userMapper.selectOne(
        Wrappers.<UserPO>lambdaQuery().eq(UserPO::getPhone, phone));
    return CopyBeanUtil.copy(value, User.class);
  }

  @Override
  public boolean deleteByUserId(Long userId) {
    return userId != null && userMapper.deleteById(userId) > 0;
  }

  @Override
  public List<User> selectListByUserIdList(List<Long> userIdList) {
    if (userIdList == null || userIdList.isEmpty()) return Collections.emptyList();
    return CopyBeanUtil.copyList(
        userMapper.selectList(Wrappers.<UserPO>lambdaQuery()
            .in(UserPO::getId, userIdList)
            .orderByAsc(UserPO::getId)),
        User.class);
  }

  @Override
  public List<User> selectListByNameAndDescOrderByCreateTime(String name) {
    LambdaQueryWrapper<UserPO> wrapper = Wrappers.<UserPO>lambdaQuery()
        .and(StringUtils.hasText(name), nested -> nested
            .like(UserPO::getUserName, name)
            .or()
            .like(UserPO::getRealName, name))
        .orderByDesc(UserPO::getCreateTime);
    return CopyBeanUtil.copyList(userMapper.selectList(wrapper), User.class);
  }

  @Override
  public List<User> selectAllList() {
    return CopyBeanUtil.copyList(
        userMapper.selectList(Wrappers.<UserPO>lambdaQuery().orderByAsc(UserPO::getId)),
        User.class);
  }

  @Override
  public User selectByUsername(String username) {
    if (!StringUtils.hasText(username)) return null;
    UserPO value = userMapper.selectOne(
        Wrappers.<UserPO>lambdaQuery().eq(UserPO::getUserName, username));
    return CopyBeanUtil.copy(value, User.class);
  }
}
