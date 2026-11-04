package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.AccessToken;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface AccessTokenDao extends IDao<AccessToken> {

    IPage<AccessToken> queryAccessTokenPage(Page<AccessToken> page, String userName, int userId);

    List<AccessToken> queryAccessTokenByUser(int userId);

    void deleteByUserId(int userId);

    List<AccessToken> listAuthorizedAccessToken(int userId, List<Integer> accessTokensIds);
}
