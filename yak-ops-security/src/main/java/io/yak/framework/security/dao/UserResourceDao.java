package io.yak.framework.security.dao;

import io.yak.framework.security.common.dto.resource.ControlLevelQueryDTO;
import io.yak.framework.security.common.dto.resource.UserResourceQueryDTO;
import io.yak.framework.security.common.entity.UserResource;
import io.yak.framework.security.common.enums.resource.ControlLevelCode;

import java.util.List;

/**
 * 用户资源数据访问接口。
 */
public interface UserResourceDao {
    int selectCountByUserId(Long userId, UserResourceQueryDTO queryDTO);

    void deleteByUserId(Long userId, UserResourceQueryDTO queryDTO);

    void deleteByControlLevel(ControlLevelCode var1);

    void insert(UserResource var1);

    void insertBatch(List<UserResource> var1);

    void deleteByUserIdList(List<Long> var1, UserResourceQueryDTO var2);

    void deleteByProjectIdList(List<Long> var1,
                               UserResourceQueryDTO var2);

    void deleteByResourceTypeIdList(List<Long> var1,
                                    UserResourceQueryDTO var2);

    void deleteByResourceIdList(List<Long> var1,
                                UserResourceQueryDTO var2);

    int selectCountByUserIdAndControlLevel(Long userId,
                                           ControlLevelCode var2);

    int selectCount(UserResourceQueryDTO var1);

    List<Long> selectResourceIdListByUserId(Long userId,
                                            UserResourceQueryDTO var2);

    void deleteWithoutUserIdList(UserResourceQueryDTO var1,
                                 List<Long> var2);

    void deleteByUserIdWithoutProjectIdList(Long userId,
                                            UserResourceQueryDTO var2,
                                            List<Long> var3);

    void deleteByUserIdWithoutResourceTypeIdList(Long userId,
                                                 UserResourceQueryDTO var2,
                                                 List<Long> var3);

    int selectCountGroupByUserId(UserResourceQueryDTO var1);

    List<Long> selectUserIdListGroupByUserId(UserResourceQueryDTO var1);

    Integer selectControlLevel(ControlLevelQueryDTO var1);
}
