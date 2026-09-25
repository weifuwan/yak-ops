package io.yak.ops.dao.repository.security.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.PageData;
import io.yak.ops.dao.entity.security.UserEntity;
import io.yak.ops.dao.mapper.security.UserMapper;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import io.yak.ops.dao.repository.security.UserRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** MyBatis-Plus user repository. */
@Repository
public class UserRepositoryImpl extends BaseRepositoryImpl<UserMapper, UserEntity> implements UserRepository {

    @Resource
    private UserMapper userMapper;

    @Override
    protected UserMapper mapper() {
        return userMapper;
    }

    @Override
    public PageData<UserEntity> queryPage(String id, String userName, String realName, long pageNo, long pageSize) {
        Page<UserEntity> page = Page.of(pageNo, pageSize);
        LambdaQueryWrapper<UserEntity> wrapper = Wrappers.<UserEntity>lambdaQuery()
                .eq(id != null, UserEntity::getId, id)
                .like(StringUtils.hasText(userName), UserEntity::getUserName, userName)
                .like(StringUtils.hasText(realName), UserEntity::getRealName, realName)
                .orderByDesc(UserEntity::getCreateTime);
        IPage<UserEntity> result = userMapper.selectPage(page, wrapper);
        return new PageData<>(
                result.getRecords(), result.getTotal(), result.getPages(), result.getCurrent(), result.getSize());
    }

    @Override
    public Optional<UserEntity> queryByEmail(String email) {
        if (!StringUtils.hasText(email)) return Optional.empty();
        return Optional.ofNullable(
                userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getEmail, email)));
    }

    @Override
    public Optional<UserEntity> queryByPhone(String phone) {
        if (!StringUtils.hasText(phone)) return Optional.empty();
        return Optional.ofNullable(
                userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getPhone, phone)));
    }

    @Override
    public Optional<UserEntity> queryByUsername(String username) {
        if (!StringUtils.hasText(username)) return Optional.empty();
        return Optional.ofNullable(
                userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUserName, username)));
    }

    @Override
    public List<UserEntity> queryByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                .in(UserEntity::getId, userIds)
                .orderByAsc(UserEntity::getId));
    }

    @Override
    public List<UserEntity> queryByName(String name) {
        return userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                .and(
                        StringUtils.hasText(name),
                        nested ->
                                nested.like(UserEntity::getUserName, name).or().like(UserEntity::getRealName, name))
                .orderByDesc(UserEntity::getCreateTime));
    }
}
