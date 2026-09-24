package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.user.UserDTO;
import io.yak.framework.security.common.dto.user.UserQueryDTO;
import io.yak.framework.security.common.entity.user.User;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.enums.user.UserCheckType;
import io.yak.framework.security.common.po.UserPO;
import io.yak.framework.security.common.vo.user.UserBriefVO;
import io.yak.framework.security.common.vo.user.UserVO;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.extend.PasswordEncoder;
import io.yak.framework.security.service.UserService;
import io.yak.framework.security.util.CopyBeanUtil;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** User-only service implementation. */
@Service("yakSecurityUserServiceImpl")
public class UserServiceImpl implements UserService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);
  private static final Pattern USER_NAME_PATTERN = Pattern.compile("^[0-9a-zA-Z_]{3,50}$");
  private static final Pattern USER_PHONE_PATTERN = Pattern.compile(
      "^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\\d{8}$");
  private static final Pattern USER_MAIL_PATTERN = Pattern.compile(
      "^\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$");

  private final UserDao userDao;
  private final PasswordEncoder passwordEncoder;

  public UserServiceImpl(UserDao userDao, PasswordEncoder passwordEncoder) {
    this.userDao = userDao;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public Result<Void> check(Integer checkType, String checkValue) {
    if (checkType == null) return Result.buildParamIllegal("校验类型不能为空");
    for (UserCheckType value : UserCheckType.values()) {
      if (value.getCode() != checkType) continue;
      return switch (value) {
        case USER_NAME -> userNameCheck(checkValue, null);
        case USER_PHONE -> userPhoneCheck(checkValue, null);
        case USER_MAIL -> userMailCheck(checkValue, null);
      };
    }
    return Result.buildParamIllegal("不支持的用户字段校验类型");
  }

  @Override
  public PagingData<UserVO> getUserPage(UserQueryDTO queryDTO) {
    IPage<User> page = userDao.selectPage(queryDTO);
    List<UserVO> records = CopyBeanUtil.copyList(page.getRecords(), UserVO.class);
    records.forEach(this::privacyProcessing);
    return PagingData.from(new PageData<>(
        records, page.getTotal(), page.getPages(), page.getCurrent(), page.getSize()));
  }

  @Override
  public UserVO getUserDetailByUserId(Long userId) {
    User user = requireUser(userId);
    UserVO result = CopyBeanUtil.copy(user, UserVO.class);
    privacyProcessing(result);
    return result;
  }

  @Override
  public Result<Void> deleteByUserId(Long userId) {
    requireUser(userId);
    return userDao.deleteByUserId(userId)
        ? Result.success()
        : Result.fail(ResultCode.USER_ACCOUNT_UPDATE_FAIL);
  }

  @Override
  public UserBriefVO getUserBriefByUsername(String username) {
    return CopyBeanUtil.copy(userDao.selectByUsername(username), UserBriefVO.class);
  }

  @Override
  public User getUserByUsername(String username) {
    return userDao.selectByUsername(username);
  }

  @Override
  public List<UserBriefVO> getUserBriefListByUserIds(List<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) return Collections.emptyList();
    return CopyBeanUtil.copyList(userDao.selectListByUserIdList(userIds), UserBriefVO.class);
  }

  @Override
  public List<UserBriefVO> searchUserBriefList(String keyword) {
    return CopyBeanUtil.copyList(
        userDao.selectListByNameAndDescOrderByCreateTime(keyword), UserBriefVO.class);
  }

  @Override
  public List<UserBriefVO> getAllUserBriefList() {
    return CopyBeanUtil.copyList(userDao.selectAllList(), UserBriefVO.class);
  }

  @Override
  @Transactional(transactionManager = "yakSecurityTransactionManager", rollbackFor = Exception.class)
  public Result<Void> addUser(UserDTO userDTO, String operator) {
    Result<Void> checkResult = checkUserParam(userDTO, true);
    if (checkResult.failed()) return checkResult;

    Result<Void> uniqueResult = userNameCheck(userDTO.getUserName(), null);
    if (uniqueResult.failed()) return uniqueResult;
    uniqueResult = userPhoneCheck(userDTO.getPhone(), null);
    if (uniqueResult.failed()) return uniqueResult;
    uniqueResult = userMailCheck(userDTO.getEmail(), null);
    if (uniqueResult.failed()) return uniqueResult;

    try {
      UserPO userPO = toUserPO(userDTO);
      userPO.setPw(passwordEncoder.encode(userDTO.getPw()));
      if (userDao.addUser(userPO) != 1) return Result.fail(ResultCode.USER_ACCOUNT_INSERT_FAIL);
      LOGGER.info("新增用户成功，用户ID={}，用户名={}，操作人={}",
          userPO.getId(), userPO.getUserName(), operator);
      return Result.success();
    } catch (YakSecurityException exception) {
      throw exception;
    } catch (Exception exception) {
      LOGGER.error("新增用户失败，用户名={}，操作人={}",
          userDTO.getUserName(), operator, exception);
      throw new YakSecurityException(ResultCode.USER_ACCOUNT_INSERT_FAIL, exception);
    }
  }

  @Override
  @Transactional(transactionManager = "yakSecurityTransactionManager", rollbackFor = Exception.class)
  public Result<Void> editUser(UserDTO userDTO, String operator) {
    Result<Void> checkResult = checkUserParam(userDTO, false);
    if (checkResult.failed()) return checkResult;

    User current = userDao.selectByUsername(userDTO.getUserName());
    if (current == null) return Result.fail(ResultCode.USER_ACCOUNT_NOT_EXIST);

    Result<Void> uniqueResult = userPhoneCheck(userDTO.getPhone(), current.getId());
    if (uniqueResult.failed()) return uniqueResult;
    uniqueResult = userMailCheck(userDTO.getEmail(), current.getId());
    if (uniqueResult.failed()) return uniqueResult;

    try {
      UserPO userPO = toUserPO(userDTO);
      userPO.setId(current.getId());
      userPO.setPw(StringUtils.hasText(userDTO.getPw())
          ? passwordEncoder.encode(userDTO.getPw())
          : null);
      if (userDao.editUser(userPO) != 1) return Result.fail(ResultCode.USER_ACCOUNT_UPDATE_FAIL);
      LOGGER.info("编辑用户成功，用户ID={}，用户名={}，操作人={}",
          userPO.getId(), userPO.getUserName(), operator);
      return Result.success();
    } catch (YakSecurityException exception) {
      throw exception;
    } catch (Exception exception) {
      LOGGER.error("编辑用户失败，用户名={}，操作人={}",
          userDTO.getUserName(), operator, exception);
      throw new YakSecurityException(ResultCode.USER_ACCOUNT_UPDATE_FAIL, exception);
    }
  }

  @Override
  public Result<List<UserVO>> getUserDetailsByUserIds(List<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) return Result.success(Collections.emptyList());
    List<UserVO> users = CopyBeanUtil.copyList(
        userDao.selectListByUserIdList(userIds), UserVO.class);
    users.forEach(this::privacyProcessing);
    return Result.success(users);
  }

  private User requireUser(Long userId) {
    if (userId == null) throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
    User user = userDao.selectByUserId(userId);
    if (user == null) throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
    return user;
  }

  private UserPO toUserPO(UserDTO source) {
    UserPO target = new UserPO();
    target.setUserName(source.getUserName().trim());
    target.setRealName(normalize(source.getRealName()));
    target.setPhone(normalize(source.getPhone()));
    target.setEmail(normalize(source.getEmail()));
    return target;
  }

  private Result<Void> checkUserParam(UserDTO userDTO, boolean passwordRequired) {
    if (userDTO == null) return Result.buildParamIllegal("用户信息不能为空");
    if (!StringUtils.hasText(userDTO.getUserName())) {
      return Result.buildParamIllegal("用户名不能为空");
    }
    if (passwordRequired && !StringUtils.hasText(userDTO.getPw())) {
      return Result.buildParamIllegal("用户密码不能为空");
    }
    return Result.success();
  }

  private Result<Void> userNameCheck(String username, Long currentUserId) {
    if (!StringUtils.hasText(username)
        || !USER_NAME_PATTERN.matcher(username.trim()).matches()) {
      return Result.fail(ResultCode.USER_NAME_FORMAT_ERROR);
    }
    User existing = userDao.selectByUsername(username.trim());
    return existing != null && !Objects.equals(existing.getId(), currentUserId)
        ? Result.fail(ResultCode.USER_NAME_EXISTS)
        : Result.success();
  }

  private Result<Void> userPhoneCheck(String phone, Long currentUserId) {
    if (!StringUtils.hasText(phone)) return Result.success();
    String normalized = phone.trim();
    if (!USER_PHONE_PATTERN.matcher(normalized).matches()) {
      return Result.fail(ResultCode.USER_PHONE_FORMAT_ERROR);
    }
    User existing = userDao.selectByUserPhone(normalized);
    return existing != null && !Objects.equals(existing.getId(), currentUserId)
        ? Result.fail(ResultCode.USER_PHONE_EXIST)
        : Result.success();
  }

  private Result<Void> userMailCheck(String email, Long currentUserId) {
    if (!StringUtils.hasText(email)) return Result.success();
    String normalized = email.trim();
    if (!USER_MAIL_PATTERN.matcher(normalized).matches()) {
      return Result.fail(ResultCode.USER_EMAIL_FORMAT_ERROR);
    }
    User existing = userDao.selectByUserMail(normalized);
    return existing != null && !Objects.equals(existing.getId(), currentUserId)
        ? Result.fail(ResultCode.USER_EMAIL_EXIST)
        : Result.success();
  }

  private void privacyProcessing(UserVO userVO) {
    if (userVO == null || !StringUtils.hasText(userVO.getPhone())) return;
    userVO.setPhone(userVO.getPhone().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
  }

  private String normalize(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
