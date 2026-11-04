package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.AccessToken;
import io.yak.ops.dao.mapper.AccessTokenMapper;
import io.yak.ops.dao.repository.AccessTokenDao;
import io.yak.ops.dao.repository.BaseDao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.NonNull;

@Repository
public class AccessTokenDaoImpl extends BaseDao<AccessToken, AccessTokenMapper> implements AccessTokenDao {

    public AccessTokenDaoImpl(@NonNull AccessTokenMapper accessTokenMapper) {
        super(accessTokenMapper);
    }

    @Override
    public IPage<AccessToken> queryAccessTokenPage(Page<AccessToken> page, String userName, int userId) {
        return mybatisMapper.selectAccessTokenPage(page, userName, userId);
    }

    @Override
    public List<AccessToken> queryAccessTokenByUser(int userId) {
        return mybatisMapper.queryAccessTokenByUser(userId);
    }

    @Override
    public void deleteByUserId(int userId) {
        mybatisMapper.deleteAccessTokenByUserId(userId);
    }

    @Override
    public List<AccessToken> listAuthorizedAccessToken(int userId, List<Integer> accessTokensIds) {
        return mybatisMapper.listAuthorizedAccessToken(userId, accessTokensIds);
    }
}
