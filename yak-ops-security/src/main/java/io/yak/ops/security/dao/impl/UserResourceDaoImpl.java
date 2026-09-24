package io.yak.ops.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.security.common.dto.resource.ControlLevelQueryDTO;
import io.yak.ops.security.common.dto.resource.UserResourceQueryDTO;
import io.yak.ops.security.common.entity.UserResource;
import io.yak.ops.security.common.enums.resource.ControlLevelCode;
import io.yak.ops.security.common.po.UserResourcePO;
import io.yak.ops.security.dao.UserResourceDao;
import io.yak.ops.security.dao.mapper.UserResourceMapper;
import io.yak.ops.security.util.CopyBeanUtil;
import io.yak.ops.security.util.DatabaseNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户资源权限数据访问实现。
 *
 * @author weifuwan
 */
@Repository
@RequiredArgsConstructor
public class UserResourceDaoImpl
        implements UserResourceDao {

    private final UserResourceMapper userResourceMapper;

    /**
     * 将 MyBatis-Plus 的统计结果转换为整数。
     */
    private static int toInt(Long value) {
        return Math.toIntExact(value == null ? 0L : value);
    }

    /**
     * 判断集合是否为空。
     */
    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    /**
     * 根据用户及资源条件统计权限数量。
     *
     * @param userId   用户标识，可为空
     * @param queryDTO 资源权限查询条件
     * @return 权限数量
     */
    @Override
    public int selectCountByUserId(
            Long userId,
            UserResourceQueryDTO queryDTO) {

        return toInt(
                userResourceMapper.selectCount(
                        queryCriteria(userId, queryDTO)
                )
        );
    }

    /**
     * 删除指定用户符合条件的资源权限。
     *
     * @param userId   用户标识
     * @param queryDTO 资源权限查询条件
     */
    @Override
    public void deleteByUserId(
            Long userId,
            UserResourceQueryDTO queryDTO) {

        if (userId == null) {
            return;
        }

        userResourceMapper.delete(
                queryCriteria(userId, queryDTO)
        );
    }

    /**
     * 根据控制级别删除资源权限。
     *
     * @param controlLevel 控制级别
     */
    @Override
    public void deleteByControlLevel(
            ControlLevelCode controlLevel) {

        if (controlLevel == null) {
            return;
        }

        userResourceMapper.delete(
                Wrappers.<UserResourcePO>lambdaQuery()
                        .eq(
                                UserResourcePO::getControlLevel,
                                controlLevel.getType()
                        )
        );
    }

    /**
     * 新增用户资源权限。
     *
     * @param userResource 用户资源权限
     */
    @Override
    public void insert(UserResource userResource) {
        UserResourcePO userResourcePO =
                CopyBeanUtil.copy(
                        userResource,
                        UserResourcePO.class
                );

        userResourceMapper.insert(userResourcePO);
    }

    /**
     * 批量新增用户资源权限。
     *
     * <p>当前采用循环插入，适合单次数据量较小的场景。</p>
     *
     * @param userResourceList 用户资源权限列表
     */
    @Override
    public void insertBatch(
            List<UserResource> userResourceList) {

        if (isEmpty(userResourceList)) {
            return;
        }

        CopyBeanUtil.copyList(
                userResourceList,
                UserResourcePO.class
        )
                .forEach(userResourceMapper::insert);
    }

    /**
     * 根据用户标识列表删除资源权限。
     *
     * @param userIdList 用户标识列表
     * @param queryDTO   资源权限查询条件
     */
    @Override
    public void deleteByUserIdList(
            List<Long> userIdList,
            UserResourceQueryDTO queryDTO) {

        if (isEmpty(userIdList)) {
            return;
        }

        userResourceMapper.delete(
                queryCriteria(null, queryDTO)
                        .in(
                                UserResourcePO::getUserId,
                                userIdList
                        )
        );
    }

    /**
     * 根据项目标识列表删除资源权限。
     *
     * @param projectIdList 项目标识列表
     * @param queryDTO      资源权限查询条件
     */
    @Override
    public void deleteByProjectIdList(
            List<Long> projectIdList,
            UserResourceQueryDTO queryDTO) {

        if (isEmpty(projectIdList)) {
            return;
        }

        userResourceMapper.delete(
                queryCriteria(null, queryDTO)
                        .in(
                                UserResourcePO::getProjectId,
                                projectIdList
                        )
        );
    }

    /**
     * 根据资源类型标识列表删除资源权限。
     *
     * @param resourceTypeIdList 资源类型标识列表
     * @param queryDTO           资源权限查询条件
     */
    @Override
    public void deleteByResourceTypeIdList(
            List<Long> resourceTypeIdList,
            UserResourceQueryDTO queryDTO) {

        if (isEmpty(resourceTypeIdList)) {
            return;
        }

        userResourceMapper.delete(
                queryCriteria(null, queryDTO)
                        .in(
                                UserResourcePO::getResourceTypeId,
                                resourceTypeIdList
                        )
        );
    }

    /**
     * 根据资源标识列表删除资源权限。
     *
     * @param resourceIdList 资源标识列表
     * @param queryDTO       资源权限查询条件
     */
    @Override
    public void deleteByResourceIdList(
            List<Long> resourceIdList,
            UserResourceQueryDTO queryDTO) {

        if (isEmpty(resourceIdList)) {
            return;
        }

        userResourceMapper.delete(
                queryCriteria(null, queryDTO)
                        .in(
                                UserResourcePO::getResourceId,
                                resourceIdList
                        )
        );
    }

    /**
     * 根据用户和控制级别统计权限数量。
     *
     * @param userId       用户标识，可为空
     * @param controlLevel 控制级别
     * @return 权限数量
     */
    @Override
    public int selectCountByUserIdAndControlLevel(
            Long userId,
            ControlLevelCode controlLevel) {

        if (controlLevel == null) {
            return 0;
        }

        return toInt(
                userResourceMapper.selectCount(
                        Wrappers.<UserResourcePO>lambdaQuery()
                                .eq(
                                        userId != null,
                                        UserResourcePO::getUserId,
                                        userId
                                )
                                .eq(
                                        UserResourcePO::getControlLevel,
                                        controlLevel.getType()
                                )
                )
        );
    }

    /**
     * 根据资源条件统计权限数量。
     *
     * @param queryDTO 查询条件
     * @return 权限数量
     */
    @Override
    public int selectCount(UserResourceQueryDTO queryDTO) {
        return toInt(
                userResourceMapper.selectCount(
                        queryCriteria(null, queryDTO)
                )
        );
    }

    /**
     * 查询指定用户关联的资源标识。
     *
     * @param userId   用户标识
     * @param queryDTO 查询条件
     * @return 资源标识列表
     */
    @Override
    public List<Long> selectResourceIdListByUserId(
            Long userId,
            UserResourceQueryDTO queryDTO) {

        if (userId == null) {
            return java.util.Collections.emptyList();
        }

        return userResourceMapper.selectObjs(
                queryCriteria(userId, queryDTO)
                        .select(
                                UserResourcePO::getResourceId
                        )
        )
                .stream()
                .map(DatabaseNumberUtils::toLong)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 删除除指定用户外符合条件的资源权限。
     *
     * @param queryDTO          查询条件
     * @param excludeUserIdList 排除的用户标识列表
     */
    @Override
    public void deleteWithoutUserIdList(
            UserResourceQueryDTO queryDTO,
            List<Long> excludeUserIdList) {

        LambdaQueryWrapper<UserResourcePO> wrapper =
                queryCriteria(null, queryDTO);

        if (!isEmpty(excludeUserIdList)) {
            wrapper.notIn(
                    UserResourcePO::getUserId,
                    excludeUserIdList
            );
        }

        userResourceMapper.delete(wrapper);
    }

    /**
     * 删除指定用户除指定项目外的资源权限。
     *
     * @param userId        用户标识
     * @param queryDTO      查询条件
     * @param excludeIdList 排除的项目标识列表
     */
    @Override
    public void deleteByUserIdWithoutProjectIdList(
            Long userId,
            UserResourceQueryDTO queryDTO,
            List<Long> excludeIdList) {

        if (userId == null) {
            return;
        }

        LambdaQueryWrapper<UserResourcePO> wrapper =
                queryCriteria(userId, queryDTO);

        if (!isEmpty(excludeIdList)) {
            wrapper.notIn(
                    UserResourcePO::getProjectId,
                    excludeIdList
            );
        }

        userResourceMapper.delete(wrapper);
    }

    /**
     * 删除指定用户除指定资源类型外的资源权限。
     *
     * @param userId        用户标识
     * @param queryDTO      查询条件
     * @param excludeIdList 排除的资源类型标识列表
     */
    @Override
    public void deleteByUserIdWithoutResourceTypeIdList(
            Long userId,
            UserResourceQueryDTO queryDTO,
            List<Long> excludeIdList) {

        if (userId == null) {
            return;
        }

        LambdaQueryWrapper<UserResourcePO> wrapper =
                queryCriteria(userId, queryDTO);

        if (!isEmpty(excludeIdList)) {
            wrapper.notIn(
                    UserResourcePO::getResourceTypeId,
                    excludeIdList
            );
        }

        userResourceMapper.delete(wrapper);
    }

    /**
     * 统计符合条件的不重复用户数量。
     *
     * @param queryDTO 查询条件
     * @return 用户数量
     */
    @Override
    public int selectCountGroupByUserId(
            UserResourceQueryDTO queryDTO) {

        return selectUserIdListGroupByUserId(queryDTO).size();
    }

    /**
     * 查询符合条件的不重复用户标识。
     *
     * @param queryDTO 查询条件
     * @return 用户标识列表
     */
    @Override
    public List<Long> selectUserIdListGroupByUserId(
            UserResourceQueryDTO queryDTO) {

        return userResourceMapper.selectObjs(
                queryCriteria(null, queryDTO)
                        .select(UserResourcePO::getUserId)
                        .groupBy(UserResourcePO::getUserId)
        )
                .stream()
                .map(DatabaseNumberUtils::toLong)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 查询指定资源上的最高控制级别。
     *
     * @param queryDTO 控制级别查询条件
     * @return 最高控制级别；不存在时返回 {@code null}
     */
    @Override
    public Integer selectControlLevel(
            ControlLevelQueryDTO queryDTO) {

        if (queryDTO == null) {
            return null;
        }

        UserResourcePO result =
                userResourceMapper.selectOne(
                        Wrappers.<UserResourcePO>lambdaQuery()
                                .select(
                                        UserResourcePO::getControlLevel
                                )
                                .eq(
                                        queryDTO.getUserId() != null,
                                        UserResourcePO::getUserId,
                                        queryDTO.getUserId()
                                )
                                .eq(
                                        queryDTO.getProjectId() != null,
                                        UserResourcePO::getProjectId,
                                        queryDTO.getProjectId()
                                )
                                .eq(
                                        queryDTO.getResourceTypeId() != null,
                                        UserResourcePO::getResourceTypeId,
                                        queryDTO.getResourceTypeId()
                                )
                                .eq(
                                        queryDTO.getResourceId() != null,
                                        UserResourcePO::getResourceId,
                                        queryDTO.getResourceId()
                                )
                                .orderByDesc(
                                        UserResourcePO::getControlLevel
                                )
                                .last("LIMIT 1")
                );

        return result == null
                ? null
                : result.getControlLevel();
    }

    /**
     * 创建用户资源权限查询条件。
     */
    private LambdaQueryWrapper<UserResourcePO> queryCriteria(
            Long userId,
            UserResourceQueryDTO queryDTO) {

        LambdaQueryWrapper<UserResourcePO> wrapper =
                Wrappers.<UserResourcePO>lambdaQuery()
                        .eq(
                                userId != null,
                                UserResourcePO::getUserId,
                                userId
                        );

        if (queryDTO == null) {
            return wrapper;
        }

        return wrapper
                .eq(
                        queryDTO.getControlLevel() != null,
                        UserResourcePO::getControlLevel,
                        queryDTO.getControlLevel()
                )
                .eq(
                        queryDTO.getProjectId() != null,
                        UserResourcePO::getProjectId,
                        queryDTO.getProjectId()
                )
                .eq(
                        queryDTO.getResourceTypeId() != null,
                        UserResourcePO::getResourceTypeId,
                        queryDTO.getResourceTypeId()
                )
                .eq(
                        queryDTO.getResourceId() != null,
                        UserResourcePO::getResourceId,
                        queryDTO.getResourceId()
                );
    }
}