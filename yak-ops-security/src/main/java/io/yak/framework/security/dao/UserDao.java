package io.yak.framework.security.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.security.common.dto.user.UserQueryDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.common.po.UserPO;
import java.util.List;

/** User persistence boundary. */
public interface UserDao {

  int addUser(UserPO userPO);

  int editUser(UserPO userPO);

  IPage<User> selectPage(UserQueryDTO queryDTO);

  User selectByUserId(Long userId);

  User selectByUserMail(String email);

  User selectByUserPhone(String phone);

  boolean deleteByUserId(Long userId);

  List<User> selectListByUserIdList(List<Long> userIdList);

  List<User> selectListByNameAndDescOrderByCreateTime(String name);

  List<User> selectAllList();

  User selectByUsername(String username);
}
