package io.yak.ops.security.service.impl;

import io.yak.ops.common.bean.dto.security.user.UserDTO;
import io.yak.ops.common.bean.dto.security.user.UserQueryDTO;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.ops.common.bean.vo.security.user.UserVO;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.ObjectUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.security.UserEntity;
import io.yak.ops.dao.repository.security.UserRepository;
import io.yak.ops.security.constant.SecurityConstants;
import io.yak.ops.security.enums.SecurityErrorCode;
import io.yak.ops.security.exception.YakSecurityException;
import io.yak.ops.security.extend.PasswordEncoder;
import io.yak.ops.security.model.UserAccount;
import io.yak.ops.security.model.UserCheckType;
import io.yak.ops.security.service.UserService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UserAccount-only service implementation. */
@ConditionalOnProperty(
        prefix = SecurityConstants.CONFIG_PREFIX,
        name = {"enabled", "database-enabled"},
        havingValue = "true",
        matchIfMissing = true)
@Service("yakSecurityUserServiceImpl")
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final Pattern USER_NAME_PATTERN = Pattern.compile("^[0-9a-zA-Z_]{3,50}$");
    private static final Pattern USER_PHONE_PATTERN =
            Pattern.compile("^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\\d{8}$");
    private static final Pattern USER_MAIL_PATTERN =
            Pattern.compile("^\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$");

    @Resource
    private UserRepository userRepository;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public Result<Void> check(Integer checkType, String checkValue) {
        if (ObjectUtils.isNull(checkType)) return Result.buildParamIllegal("校验类型不能为空");
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
        UserQueryDTO query = ObjectUtils.isNull(queryDTO) ? new UserQueryDTO() : queryDTO;
        if (CollectionUtils.isNotEmpty(query.getSorts())) {
            throw new YakSecurityException("用户分页暂不支持自定义排序");
        }
        PageData<UserEntity> page = userRepository.queryPage(
                query.getId(),
                query.getUserName(),
                query.getRealName(),
                query.getPageNo(),
                query.getPageSize());
        PageData<UserVO> result = page.map(entity -> BeanCopyUtils.copy(entity, UserVO.class));
        result.records().forEach(this::privacyProcessing);
        return PagingData.from(result);
    }

    @Override
    public UserVO getUserDetailByUserId(String userId) {
        UserAccount user = requireUser(userId);
        UserVO result = BeanCopyUtils.copy(user, UserVO.class);
        privacyProcessing(result);
        return result;
    }

    @Override
    public Result<Void> deleteByUserId(String userId) {
        requireUser(userId);
        return userRepository.deleteById(userId) > 0
                ? Result.success()
                : Result.fail(SecurityErrorCode.USER_ACCOUNT_UPDATE_FAIL);
    }

    @Override
    public UserBriefVO getUserBriefByUsername(String username) {
        return BeanCopyUtils.copy(userRepository.queryByUsername(username).orElse(null), UserBriefVO.class);
    }

    @Override
    public UserAccount getUserByUsername(String username) {
        return toUser(userRepository.queryByUsername(username).orElse(null));
    }

    @Override
    public List<UserBriefVO> getUserBriefListByUserIds(List<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) return List.of();
        return BeanCopyUtils.copyList(userRepository.queryByIds(userIds), UserBriefVO.class);
    }

    @Override
    public List<UserBriefVO> searchUserBriefList(String keyword) {
        return BeanCopyUtils.copyList(userRepository.queryByName(keyword), UserBriefVO.class);
    }

    @Override
    public List<UserBriefVO> getAllUserBriefList() {
        return BeanCopyUtils.copyList(userRepository.queryList(), UserBriefVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
            UserEntity user = toUserEntity(userDTO);
            user.setPw(passwordEncoder.encode(userDTO.getPw()));
            user.initCreate(operator);
            userRepository.add(user);
            LOGGER.info("新增用户成功，用户ID={}，用户名={}，操作人={}", user.getId(), user.getUserName(), operator);
            return Result.success();
        } catch (YakSecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("新增用户失败，用户名={}，操作人={}", userDTO.getUserName(), operator, exception);
            throw new YakSecurityException(SecurityErrorCode.USER_ACCOUNT_INSERT_FAIL, exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> editUser(UserDTO userDTO, String operator) {
        Result<Void> checkResult = checkUserParam(userDTO, false);
        if (checkResult.failed()) return checkResult;

        UserAccount current = getUserByUsername(userDTO.getUserName());
        if (ObjectUtils.isNull(current)) return Result.fail(SecurityErrorCode.USER_ACCOUNT_NOT_EXIST);

        Result<Void> uniqueResult = userPhoneCheck(userDTO.getPhone(), current.getId());
        if (uniqueResult.failed()) return uniqueResult;
        uniqueResult = userMailCheck(userDTO.getEmail(), current.getId());
        if (uniqueResult.failed()) return uniqueResult;

        try {
            UserEntity user = toUserEntity(userDTO);
            user.setId(current.getId());
            user.setPw(StringUtils.isNotBlank(userDTO.getPw()) ? passwordEncoder.encode(userDTO.getPw()) : null);
            user.initUpdate(operator);
            userRepository.update(user);
            LOGGER.info("编辑用户成功，用户ID={}，用户名={}，操作人={}", user.getId(), user.getUserName(), operator);
            return Result.success();
        } catch (YakSecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("编辑用户失败，用户名={}，操作人={}", userDTO.getUserName(), operator, exception);
            throw new YakSecurityException(SecurityErrorCode.USER_ACCOUNT_UPDATE_FAIL, exception);
        }
    }

    @Override
    public Result<List<UserVO>> getUserDetailsByUserIds(List<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) return Result.success(List.of());
        List<UserVO> users = BeanCopyUtils.copyList(userRepository.queryByIds(userIds), UserVO.class);
        users.forEach(this::privacyProcessing);
        return Result.success(users);
    }

    private UserAccount requireUser(String userId) {
        if (ObjectUtils.isNull(userId)) throw new YakSecurityException(SecurityErrorCode.USER_ID_CANNOT_BE_NULL);
        UserAccount user = toUser(userRepository.queryById(userId).orElse(null));
        if (ObjectUtils.isNull(user)) throw new YakSecurityException(SecurityErrorCode.USER_NOT_EXISTS);
        return user;
    }

    private UserAccount toUser(UserEntity source) {
        return BeanCopyUtils.copy(source, UserAccount.class);
    }

    private UserEntity toUserEntity(UserDTO source) {
        UserEntity target = new UserEntity();
        target.setUserName(source.getUserName().trim());
        target.setRealName(StringUtils.trimToNull(source.getRealName()));
        target.setPhone(StringUtils.trimToNull(source.getPhone()));
        target.setEmail(StringUtils.trimToNull(source.getEmail()));
        return target;
    }

    private Result<Void> checkUserParam(UserDTO userDTO, boolean passwordRequired) {
        if (ObjectUtils.isNull(userDTO)) return Result.buildParamIllegal("用户信息不能为空");
        if (StringUtils.isBlank(userDTO.getUserName())) {
            return Result.buildParamIllegal("用户名不能为空");
        }
        if (passwordRequired && StringUtils.isBlank(userDTO.getPw())) {
            return Result.buildParamIllegal("用户密码不能为空");
        }
        return Result.success();
    }

    private Result<Void> userNameCheck(String username, String currentUserId) {
        if (StringUtils.isBlank(username)
                || !USER_NAME_PATTERN.matcher(username.trim()).matches()) {
            return Result.fail(SecurityErrorCode.USER_NAME_FORMAT_ERROR);
        }
        UserEntity existing = userRepository.queryByUsername(username.trim()).orElse(null);
        return ObjectUtils.isNotNull(existing) && !Objects.equals(existing.getId(), currentUserId)
                ? Result.fail(SecurityErrorCode.USER_NAME_EXISTS)
                : Result.success();
    }

    private Result<Void> userPhoneCheck(String phone, String currentUserId) {
        if (StringUtils.isBlank(phone)) return Result.success();
        String normalized = phone.trim();
        if (!USER_PHONE_PATTERN.matcher(normalized).matches()) {
            return Result.fail(SecurityErrorCode.USER_PHONE_FORMAT_ERROR);
        }
        UserEntity existing = userRepository.queryByPhone(normalized).orElse(null);
        return ObjectUtils.isNotNull(existing) && !Objects.equals(existing.getId(), currentUserId)
                ? Result.fail(SecurityErrorCode.USER_PHONE_EXIST)
                : Result.success();
    }

    private Result<Void> userMailCheck(String email, String currentUserId) {
        if (StringUtils.isBlank(email)) return Result.success();
        String normalized = email.trim();
        if (!USER_MAIL_PATTERN.matcher(normalized).matches()) {
            return Result.fail(SecurityErrorCode.USER_EMAIL_FORMAT_ERROR);
        }
        UserEntity existing = userRepository.queryByEmail(normalized).orElse(null);
        return ObjectUtils.isNotNull(existing) && !Objects.equals(existing.getId(), currentUserId)
                ? Result.fail(SecurityErrorCode.USER_EMAIL_EXIST)
                : Result.success();
    }

    private void privacyProcessing(UserVO userVO) {
        if (ObjectUtils.isNull(userVO) || StringUtils.isBlank(userVO.getPhone())) return;
        userVO.setPhone(userVO.getPhone().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
    }
}
