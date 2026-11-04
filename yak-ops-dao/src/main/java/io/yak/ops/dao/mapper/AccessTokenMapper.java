package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.AccessToken;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * accesstoken mapper interface
 */
public interface AccessTokenMapper extends BaseMapper<AccessToken> {

    /**
     * access token page
     *
     * @param page page
     * @param userId userId
     * @param userName userName
     * @return access token Ipage
     */
    IPage<AccessToken> selectAccessTokenPage(Page page,
                                             @Param("userName") String userName,
                                             @Param("userId") int userId);

    /**
     * Query access token for specified user
     *
     * @param userId userId
     * @return access token for specified user
     */
    List<AccessToken> queryAccessTokenByUser(@Param("userId") int userId);

    /**
     * delete by userId
     *
     * @param userId userId
     * @return delete result
     */
    int deleteAccessTokenByUserId(@Param("userId") int userId);

    /**
     * list authorized Projects
     * @param userId
     * @param accessTokensIds
     * @return access token for specified user
     */
    List<AccessToken> listAuthorizedAccessToken(@Param("userId") int userId,
                                                @Param("accessTokensIds") List<Integer> accessTokensIds);
}
