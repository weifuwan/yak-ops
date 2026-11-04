package io.yak.ops.api.service;

import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.dao.entity.AccessToken;
import io.yak.ops.dao.entity.User;

import java.util.List;

public interface AccessTokenService {

    /**
     * query access token list
     *
     * @param loginUser login user
     * @param searchVal search value
     * @param pageNo page number
     * @param pageSize page size
     * @return token list for page number and page size
     */
    PageInfo<AccessToken> queryAccessTokenList(User loginUser, String searchVal, Integer pageNo, Integer pageSize);

    /**
     * query access token for specified user
     *
     * @param loginUser login user
     * @param userId user id
     * @return token list for specified user
     */
    List<AccessToken> queryAccessTokenByUser(User loginUser, Integer userId);

    /**
     * create token
     *
     * @param userId token for user
     * @param expireTime token expire time
     * @param token token string (if it is absent, it will be automatically generated)
     * @return create result code
     */
    AccessToken createToken(User loginUser, int userId, String expireTime, String token);

    /**
     * generate token
     *
     * @param userId token for user
     * @param expireTime token expire time
     * @return token string
     */
    String generateToken(User loginUser, int userId, String expireTime);

    /**
     * delete access token
     *
     * @param loginUser login user
     * @param id token id
     * @return delete result code
     */
    void deleteAccessTokenById(User loginUser, int id);

    /**
     * update token by id
     *
     * @param id token id
     * @param userId token for user
     * @param expireTime token expire time
     * @param token token string (if it is absent, it will be automatically generated)
     * @return updated access token entity
     */
    AccessToken updateToken(User loginUser, int id, int userId, String expireTime, String token);
}
