package io.yak.ops.security.service.impl;

import io.yak.ops.common.bean.dto.security.user.UserDTO;
import io.yak.ops.common.bean.dto.security.user.UserQueryDTO;
import io.yak.ops.common.bean.vo.security.user.UserBriefVO;
import io.yak.ops.common.bean.vo.security.user.UserVO;
import io.yak.ops.common.enums.security.ResultCode;
import io.yak.ops.common.enums.security.user.UserCheckType;
import io.yak.ops.common.exception.YakSecurityException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.dao.entity.security.UserEntity;
import io.yak.ops.dao.repository.security.UserRepository;
import io.yak.ops.security.extend.PasswordEncoder;
import io.yak.ops.security.model.UserAccount;
import io.yak.ops.security.service.UserService;
import io.yak.ops.security.util.CopyBeanUtil;
import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** UserAccount-only service implementation. */
@ConditionalOnProperty(
        prefix = "yak.security",
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
        UserQueryDTO query = queryDTO == null ? new UserQueryDTO() : queryDTO;
        if (query.getSorts() != null && !query.getSorts().isEmpty()) {
            throw new YakSecurityException("用户分页暂不支持自定义排序");
        }
        PageData<UserEntity> page = userRepository.queryPage(
                query.getId(),
                query.getUserName(),
                query.getRealName(),
                query.getPageNo(),
                query.getPageSize());
        PageData<UserVO> result = page.map(entity -> CopyBeanUtil.copy(entity, UserVO.class));
        result.records().forEach(this::privacyProcessing);
        return PagingData.from(result);
    }

    @Override
    public UserVO getUserDetailByUserId(String userId) {
        UserAccount user = requireUser(userId);
        UserVO result = CopyBeanUtil.copy(user, UserVO.class);
        privacyProcessing(result);
        return result;
    }

    @Override
    public Result<Void> deleteByUserId(String userId) {
        requireUser(userId);
        return userRepository.deleteById(userId) > 0
                ? Result.success()
                : Result.fail(ResultCode.USER_ACCOUNT_UPDATE_FAIL);
    }

    @Override
    public UserBriefVO getUserBriefByUsername(String username) {
        return CopyBeanUtil.copy(userRepository.queryByUsername(username).orElse(null), UserBriefVO.class);
    }

    @Override
    public UserAccount getUserByUsername(String username) {
        return toUser(userRepository.queryByUsername(username).orElse(null));
    }

    @Override
    public List<UserBriefVO> getUserBriefListByUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyList();
        return CopyBeanUtil.copyList(userRepository.queryByIds(userIds), UserBriefVO.class);
    }

    @Override
    public List<UserBriefVO> searchUserBriefList(String keyword) {
        return CopyBeanUtil.copyList(userRepository.queryByName(keyword), UserBriefVO.class);
    }

    @Override
    public List<UserBriefVO> getAllUserBriefList() {
        return CopyBeanUtil.copyList(userRepository.queryList(), UserBriefVO.class);
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
            throw new YakSecurityException(ResultCode.USER_ACCOUNT_INSERT_FAIL, exception);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> editUser(UserDTO userDTO, String operator) {
        Result<Void> checkResult = checkUserParam(userDTO, false);
        if (checkResult.failed()) return checkResult;

        UserAccount current = getUserByUsername(userDTO.getUserName());
        if (current == null) return Result.fail(ResultCode.USER_ACCOUNT_NOT_EXIST);

        Result<Void> uniqueResult = userPhoneCheck(userDTO.getPhone(), current.getId());
        if (uniqueResult.failed()) return uniqueResult;
        uniqueResult = userMailCheck(userDTO.getEmail(), current.getId());
        if (uniqueResult.failed()) return uniqueResult;

        try {
            UserEntity user = toUserEntity(userDTO);
            user.setId(current.getId());
            user.setPw(StringUtils.hasText(userDTO.getPw()) ? passwordEncoder.encode(userDTO.getPw()) : null);
            user.initUpdate(operator);
            userRepository.update(user);
            LOGGER.info("编辑用户成功，用户ID={}，用户名={}，操作人={}", user.getId(), user.getUserName(), operator);
            return Result.success();
        } catch (YakSecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("编辑用户失败，用户名={}，操作人={}", userDTO.getUserName(), operator, exception);
            throw new YakSecurityException(ResultCode.USER_ACCOUNT_UPDATE_FAIL, exception);
        }
    }

    @Override
    public Result<List<UserVO>> getUserDetailsByUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return Result.success(Collections.emptyList());
        List<UserVO> users = CopyBeanUtil.copyList(userRepository.queryByIds(userIds), UserVO.class);
        users.forEach(this::privacyProcessing);
        return Result.success(users);
    }

    private UserAccount requireUser(String userId) {
        if (userId == null) throw new YakSecurityException(ResultCode.USER_ID_CANNOT_BE_NULL);
        UserAccount user = toUser(userRepository.queryById(userId).orElse(null));
        if (user == null) throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
        return user;
    }

    private UserAccount toUser(UserEntity source) {
        return CopyBeanUtil.copy(source, UserAccount.class);
    }

    private UserEntity toUserEntity(UserDTO source) {
        UserEntity target = new UserEntity();
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

    private Result<Void> userNameCheck(String username, String currentUserId) {
        if (!StringUtils.hasText(username)
                || !USER_NAME_PATTERN.matcher(username.trim()).matches()) {
            return Result.fail(ResultCode.USER_NAME_FORMAT_ERROR);
        }
        UserEntity existing = userRepository.queryByUsername(username.trim()).orElse(null);
        return existing != null && !Objects.equals(existing.getId(), currentUserId)
                ? Result.fail(ResultCode.USER_NAME_EXISTS)
                : Result.success();
    }

    private Result<Void> userPhoneCheck(String phone, String currentUserId) {
        if (!StringUtils.hasText(phone)) return Result.success();
        String normalized = phone.trim();
        if (!USER_PHONE_PATTERN.matcher(normalized).matches()) {
            return Result.fail(ResultCode.USER_PHONE_FORMAT_ERROR);
        }
        UserEntity existing = userRepository.queryByPhone(normalized).orElse(null);
        return existing != null && !Objects.equals(existing.getId(), currentUserId)
                ? Result.fail(ResultCode.USER_PHONE_EXIST)
                : Result.success();
    }

    private Result<Void> userMailCheck(String email, String currentUserId) {
        if (!StringUtils.hasText(email)) return Result.success();
        String normalized = email.trim();
        if (!USER_MAIL_PATTERN.matcher(normalized).matches()) {
            return Result.fail(ResultCode.USER_EMAIL_FORMAT_ERROR);
        }
        UserEntity existing = userRepository.queryByEmail(normalized).orElse(null);
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
