package io.yak.ops.security.dao.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.security.common.dto.config.ConfigQueryDTO;
import io.yak.ops.security.common.po.ConfigPO;
import io.yak.ops.security.dao.ConfigDao;
import io.yak.ops.security.dao.mapper.ConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置数据访问实现。
 */
@Repository
@RequiredArgsConstructor
public class ConfigDaoImpl implements ConfigDao {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    private final ConfigMapper configMapper;

    @Override
    public int insert(ConfigPO config) {
        return configMapper.insert(config);
    }

    @Override
    public int updateById(ConfigPO config) {
        return configMapper.updateById(config);
    }

    /**
     * 更新配置。
     *
     * <p>编辑页面允许修改配置分组和配置名称，因此当主键存在时必须
     * 按主键更新。旧实现按修改后的分组和名称作为条件，会导致更名后
     * 无法命中原记录。</p>
     */
    @Override
    public int update(ConfigPO config) {
        if (config == null) {
            return 0;
        }

        if (config.getId() != null) {
            return configMapper.updateById(config);
        }

        LambdaUpdateWrapper<ConfigPO> wrapper =
                Wrappers.<ConfigPO>lambdaUpdate()
                        .eq(ConfigPO::getValueGroup,
                                config.getValueGroup())
                        .eq(ConfigPO::getValueName,
                                config.getValueName());

        return configMapper.update(config, wrapper);
    }

    @Override
    public int deleteById(Long id) {
        return configMapper.deleteById(id);
    }

    @Override
    public IPage<ConfigPO> selectPage(
            ConfigQueryDTO queryDTO) {

        ConfigQueryDTO query = queryDTO == null
                ? new ConfigQueryDTO()
                : queryDTO;

        long pageNo = Math.max(1, query.getPage());
        long pageSize = query.getSize() <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(query.getSize(), MAX_PAGE_SIZE);

        Page<ConfigPO> page = Page.of(pageNo, pageSize);

        LambdaQueryWrapper<ConfigPO> wrapper =
                Wrappers.<ConfigPO>lambdaQuery()
                        .eq(
                                query.getId() != null,
                                ConfigPO::getId,
                                query.getId())
                        .eq(
                                StringUtils.hasText(query.getValueGroup()),
                                ConfigPO::getValueGroup,
                                trim(query.getValueGroup()))
                        .eq(
                                query.getStatus() != null,
                                ConfigPO::getStatus,
                                query.getStatus())
                        .like(
                                StringUtils.hasText(query.getOperator()),
                                ConfigPO::getOperator,
                                trim(query.getOperator()))
                        .like(
                                StringUtils.hasText(query.getMemo()),
                                ConfigPO::getMemo,
                                trim(query.getMemo()))
                        .like(
                                StringUtils.hasText(query.getValueName()),
                                ConfigPO::getValueName,
                                trim(query.getValueName()))
                        .orderByDesc(ConfigPO::getUpdateTime)
                        .orderByDesc(ConfigPO::getCreateTime)
                        .orderByDesc(ConfigPO::getId);

        return configMapper.selectPage(page, wrapper);
    }

    @Override
    public List<ConfigPO> listByCondition(
            ConfigPO condition) {

        if (condition == null) {
            return configMapper.selectList(
                    Wrappers.<ConfigPO>lambdaQuery()
                            .orderByAsc(ConfigPO::getValueGroup)
                            .orderByAsc(ConfigPO::getValueName));
        }

        LambdaQueryWrapper<ConfigPO> wrapper =
                Wrappers.<ConfigPO>lambdaQuery()
                        .eq(
                                StringUtils.hasText(condition.getValueGroup()),
                                ConfigPO::getValueGroup,
                                trim(condition.getValueGroup()))
                        .eq(
                                StringUtils.hasText(condition.getValueName()),
                                ConfigPO::getValueName,
                                trim(condition.getValueName()))
                        .eq(
                                condition.getStatus() != null,
                                ConfigPO::getStatus,
                                condition.getStatus())
                        .orderByAsc(ConfigPO::getValueGroup)
                        .orderByAsc(ConfigPO::getValueName);

        return configMapper.selectList(wrapper);
    }

    @Override
    public List<ConfigPO> listConfigByGroup(
            String groupName) {

        if (!StringUtils.hasText(groupName)) {
            return new ArrayList<>();
        }

        return configMapper.selectList(
                Wrappers.<ConfigPO>lambdaQuery()
                        .eq(ConfigPO::getValueGroup, trim(groupName))
                        .orderByAsc(ConfigPO::getValueName));
    }

    @Override
    public List<String> listDistinctGroup() {
        List<ConfigPO> configs =
                configMapper.selectList(
                        Wrappers.<ConfigPO>lambdaQuery()
                                .select(ConfigPO::getValueGroup)
                                .isNotNull(ConfigPO::getValueGroup)
                                .groupBy(ConfigPO::getValueGroup)
                                .orderByAsc(ConfigPO::getValueGroup));

        if (configs == null || configs.isEmpty()) {
            return new ArrayList<>();
        }

        return configs.stream()
                .map(ConfigPO::getValueGroup)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public ConfigPO getbyId(Long configId) {
        if (configId == null) {
            return null;
        }
        return configMapper.selectById(configId);
    }

    @Override
    public ConfigPO getByGroupAndName(
            String valueGroup,
            String valueName) {

        if (!StringUtils.hasText(valueGroup)
                || !StringUtils.hasText(valueName)) {
            return null;
        }

        return configMapper.selectOne(
                Wrappers.<ConfigPO>lambdaQuery()
                        .eq(ConfigPO::getValueGroup, trim(valueGroup))
                        .eq(ConfigPO::getValueName, trim(valueName)));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
