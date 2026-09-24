package io.yak.framework.security.service.impl;

import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.config.ConfigDTO;
import io.yak.framework.security.common.dto.config.ConfigQueryDTO;
import io.yak.framework.security.common.enums.ConfigStatusEnum;
import io.yak.framework.security.common.po.ConfigPO;
import io.yak.framework.security.common.vo.config.ConfigVO;
import io.yak.framework.security.dao.ConfigDao;
import io.yak.framework.security.service.ConfigService;
import io.yak.framework.security.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 状态感知的配置服务。
 *
 * <p>管理类操作继续复用原有 {@link ConfigServiceImpl}，运行时配置读取则
 * 额外检查配置状态。这样在管理页面将配置切换为“停用”后，业务代码会
 * 立即回退到调用方提供的默认值，而不是继续使用已停用的数据库值。</p>
 */
@Service("yakSecurityStatusAwareConfigService")
@Primary
public class StatusAwareConfigService implements ConfigService {

  private static final Logger LOGGER =
          LoggerFactory.getLogger(StatusAwareConfigService.class);

  private final ConfigService delegate;
  private final ConfigDao configDao;

  public StatusAwareConfigService(
          @Qualifier("configServiceImpl") ConfigService delegate,
          ConfigDao configDao) {
    this.delegate = delegate;
    this.configDao = configDao;
  }

  @Override
  public Result<Long> addConfig(
          ConfigDTO configDTO,
          String operator) {
    return delegate.addConfig(configDTO, operator);
  }

  @Override
  public Result<Long> addConfig(
          String valueGroup,
          String valueName,
          String value,
          String operator) {
    return delegate.addConfig(
            valueGroup,
            valueName,
            value,
            operator);
  }

  @Override
  public Result<Void> delConfig(
          Long configId,
          String operator) {
    return delegate.delConfig(configId, operator);
  }

  @Override
  public Result<Void> editConfig(
          ConfigDTO configDTO,
          String operator) {
    return delegate.editConfig(configDTO, operator);
  }

  @Override
  public Result<Void> switchConfig(
          Long configId,
          Integer status,
          String operator) {
    return delegate.switchConfig(configId, status, operator);
  }

  @Override
  public PagingData<ConfigVO> pagingConfig(
          ConfigQueryDTO queryDTO) {
    return delegate.pagingConfig(queryDTO);
  }

  @Override
  public List<ConfigVO> queryByCondt(ConfigDTO condition) {
    return delegate.queryByCondt(condition);
  }

  @Override
  public List<String> listGroups() {
    return delegate.listGroups();
  }

  @Override
  public List<ConfigVO> listConfigByGroup(String valueGroup) {
    return delegate.listConfigByGroup(valueGroup);
  }

  @Override
  public ConfigVO getConfigById(Long configId) {
    return delegate.getConfigById(configId);
  }

  @Override
  public String stringSetting(
          String valueGroup,
          String valueName,
          String defaultValue) {
    return getEnabledSetting(
            valueGroup,
            valueName,
            defaultValue,
            value -> value,
            "stringSetting");
  }

  @Override
  public Boolean booleanSetting(
          String valueGroup,
          String valueName,
          Boolean defaultValue) {
    return getEnabledSetting(
            valueGroup,
            valueName,
            defaultValue,
            this::parseBoolean,
            "booleanSetting");
  }

  @Override
  public Integer intSetting(
          String valueGroup,
          String valueName,
          Integer defaultValue) {
    return getEnabledSetting(
            valueGroup,
            valueName,
            defaultValue,
            Integer::valueOf,
            "intSetting");
  }

  @Override
  public Long longSetting(
          String valueGroup,
          String valueName,
          Long defaultValue) {
    return getEnabledSetting(
            valueGroup,
            valueName,
            defaultValue,
            Long::valueOf,
            "longSetting");
  }

  @Override
  public Double doubleSetting(
          String valueGroup,
          String valueName,
          Double defaultValue) {
    return getEnabledSetting(
            valueGroup,
            valueName,
            defaultValue,
            Double::valueOf,
            "doubleSetting");
  }

  @Override
  public <T> T objectSetting(
          String valueGroup,
          String valueName,
          T defaultValue,
          Class<T> type) {
    if (type == null) {
      return defaultValue;
    }

    return getEnabledSetting(
            valueGroup,
            valueName,
            defaultValue,
            value -> JsonUtils.fromJson(value, type),
            "objectSetting");
  }

  private <T> T getEnabledSetting(
          String valueGroup,
          String valueName,
          T defaultValue,
          Function<String, T> converter,
          String methodName) {
    try {
      ConfigPO config = configDao.getByGroupAndName(
              valueGroup,
              valueName);

      if (config == null
              || !Objects.equals(
              config.getStatus(),
              ConfigStatusEnum.NORMAL.getCode())
              || !StringUtils.hasText(config.getValue())) {
        return defaultValue;
      }

      return converter.apply(config.getValue());
    } catch (Exception exception) {
      LOGGER.warn(
              "读取启用配置失败，方法={}，配置组={}，配置名={}",
              methodName,
              valueGroup,
              valueName,
              exception);
      return defaultValue;
    }
  }

  private Boolean parseBoolean(String value) {
    if ("true".equalsIgnoreCase(value)) {
      return Boolean.TRUE;
    }
    if ("false".equalsIgnoreCase(value)) {
      return Boolean.FALSE;
    }
    throw new IllegalArgumentException(
            "配置值不是有效的布尔类型");
  }
}
