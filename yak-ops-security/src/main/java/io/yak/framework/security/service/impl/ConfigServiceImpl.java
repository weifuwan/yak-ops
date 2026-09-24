package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.dto.config.ConfigDTO;
import io.yak.framework.security.common.dto.config.ConfigQueryDTO;
import io.yak.framework.security.common.enums.ConfigStatusEnum;
import io.yak.framework.security.common.po.ConfigPO;
import io.yak.framework.security.common.vo.config.ConfigVO;
import io.yak.framework.security.dao.ConfigDao;
import io.yak.framework.security.service.ConfigService;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.JsonUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 配置服务实现类。
 *
 * @author weifuwan
 */
@Service
public class ConfigServiceImpl implements ConfigService {

  private static final Logger LOGGER =
          LoggerFactory.getLogger(ConfigServiceImpl.class);

  private static final String CONFIG_NOT_EXIST = "配置不存在";

  private static final String CONFIG_DUPLICATE = "配置重复";

  private final ConfigDao configDao;

  /**
   * 创建配置服务。
   *
   * @param configDao 配置数据访问对象
   */
  public ConfigServiceImpl(ConfigDao configDao) {
    this.configDao = configDao;
  }

  /**
   * 新增配置。
   *
   * @param configDTO 配置信息
   * @param operator 操作人
   * @return 新增结果及配置 ID
   */
  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public Result<Long> addConfig(
          ConfigDTO configDTO,
          String operator) {

    Result<Void> checkResult = checkParam(configDTO);
    if (checkResult.failed()) {
      LOGGER.warn(
              "新增配置参数校验失败，原因：{}",
              checkResult.getMessage());
      return Result.buildFrom(checkResult);
    }

    initConfig(configDTO);

    Result<Void> statusCheckResult =
            checkStatus(configDTO.getStatus());

    if (statusCheckResult.failed()) {
      return Result.buildFrom(statusCheckResult);
    }

    ConfigPO existingConfig =
            getByGroupAndNameFromDB(
                    configDTO.getValueGroup(),
                    configDTO.getValueName());

    if (existingConfig != null) {
      return Result.buildDuplicate(CONFIG_DUPLICATE);
    }

    configDTO.setOperator(operator);

    ConfigPO configPO =
            CopyBeanUtil.copy(configDTO, ConfigPO.class);

    if (configPO == null) {
      throw new IllegalStateException("配置对象转换失败");
    }

    int affectedRows = configDao.insert(configPO);
    if (affectedRows != 1) {
      throw new IllegalStateException("新增配置失败");
    }

    return Result.success(configPO.getId());
  }

  /**
   * 根据配置参数新增配置。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param value 配置值
   * @param operator 操作人
   * @return 新增结果及配置 ID
   */
  @Override
  public Result<Long> addConfig(
          String valueGroup,
          String valueName,
          String value,
          String operator) {

    ConfigDTO configDTO = new ConfigDTO();
    configDTO.setValueGroup(valueGroup);
    configDTO.setValueName(valueName);
    configDTO.setValue(value);
    configDTO.setStatus(
            ConfigStatusEnum.NORMAL.getCode());

    return addConfig(configDTO, operator);
  }

  /**
   * 删除配置。
   *
   * @param configId 配置 ID
   * @param operator 操作人
   * @return 删除结果
   */
  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public Result<Void> delConfig(
          Long configId,
          String operator) {

    if (configId == null) {
      return Result.buildParamIllegal("配置 ID 不能为空");
    }

    ConfigPO configPO = configDao.getbyId(configId);
    if (configPO == null) {
      return Result.buildNotExist(CONFIG_NOT_EXIST);
    }

    boolean success =
            configDao.deleteById(configId) == 1;

    if (success) {
      LOGGER.info(
              "删除配置成功，配置 ID={}，操作人={}",
              configId,
              operator);
    }

    return Result.build(success);
  }

  /**
   * 编辑配置。
   *
   * @param configDTO 配置信息
   * @param operator 操作人
   * @return 编辑结果
   */
  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public Result<Void> editConfig(
          ConfigDTO configDTO,
          String operator) {

    Result<Void> checkResult = checkParam(configDTO);
    if (checkResult.failed()) {
      return checkResult;
    }

    initConfig(configDTO);

    Result<Void> statusCheckResult =
            checkStatus(configDTO.getStatus());

    if (statusCheckResult.failed()) {
      return statusCheckResult;
    }

    ConfigPO currentConfig =
            getCurrentConfig(configDTO);

    if (currentConfig == null) {
      Result<Long> addResult =
              addConfig(configDTO, operator);

      return Result.buildFrom(addResult);
    }

    ConfigPO sameNameConfig =
            getByGroupAndNameFromDB(
                    configDTO.getValueGroup(),
                    configDTO.getValueName());

    if (sameNameConfig != null
            && !Objects.equals(
            sameNameConfig.getId(),
            currentConfig.getId())) {

      return Result.buildDuplicate(CONFIG_DUPLICATE);
    }

    ConfigPO updateConfig =
            CopyBeanUtil.copy(configDTO, ConfigPO.class);

    if (updateConfig == null) {
      throw new IllegalStateException("配置对象转换失败");
    }

    updateConfig.setId(currentConfig.getId());
    updateConfig.setOperator(operator);

    boolean success =
            configDao.update(updateConfig) == 1;

    return Result.build(success);
  }

  /**
   * 切换配置状态。
   *
   * @param configId 配置 ID
   * @param status 配置状态
   * @param operator 操作人
   * @return 状态切换结果
   */
  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public Result<Void> switchConfig(
          Long configId,
          Integer status,
          String operator) {

    if (configId == null) {
      return Result.buildParamIllegal("配置 ID 不能为空");
    }

    Result<Void> statusCheckResult =
            checkStatus(status);

    if (statusCheckResult.failed()) {
      return statusCheckResult;
    }

    ConfigPO configPO = configDao.getbyId(configId);
    if (configPO == null) {
      return Result.buildNotExist(CONFIG_NOT_EXIST);
    }

    configPO.setStatus(status);
    configPO.setOperator(operator);

    boolean success =
            configDao.updateById(configPO) == 1;

    return Result.build(success);
  }

  /**
   * 分页查询配置。
   *
   * @param queryDTO 分页查询条件
   * @return 配置分页数据
   */
  @Override
  public PagingData<ConfigVO> pagingConfig(
          ConfigQueryDTO queryDTO) {

    IPage<ConfigPO> configPage =
            configDao.selectPage(queryDTO);

    List<ConfigVO> configList =
            CopyBeanUtil.copyList(
                    configPage.getRecords(),
                    ConfigVO.class);

    return PagingData.from(
            new PageData<>(
                    configList,
                    configPage.getTotal(),
                    configPage.getPages(),
                    configPage.getCurrent(),
                    configPage.getSize()));
  }

  /**
   * 根据条件查询配置。
   *
   * @param condition 查询条件
   * @return 配置列表
   */
  @Override
  public List<ConfigVO> queryByCondt(
          ConfigDTO condition) {

    ConfigPO queryCondition =
            CopyBeanUtil.copy(condition, ConfigPO.class);

    List<ConfigPO> configList =
            configDao.listByCondition(queryCondition);

    return CopyBeanUtil.copyList(
            configList,
            ConfigVO.class);
  }

  /**
   * 查询全部配置分组。
   *
   * @return 配置分组列表
   */
  @Override
  public List<String> listGroups() {
    return configDao.listDistinctGroup();
  }

  /**
   * 根据分组查询配置。
   *
   * @param valueGroup 配置分组
   * @return 配置列表
   */
  @Override
  public List<ConfigVO> listConfigByGroup(
          String valueGroup) {

    List<ConfigPO> configList =
            configDao.listConfigByGroup(valueGroup);

    return CopyBeanUtil.copyList(
            configList,
            ConfigVO.class);
  }

  /**
   * 根据配置 ID 查询配置。
   *
   * @param configId 配置 ID
   * @return 配置信息
   */
  @Override
  public ConfigVO getConfigById(Long configId) {
    ConfigPO configPO = configDao.getbyId(configId);
    return CopyBeanUtil.copy(configPO, ConfigVO.class);
  }

  /**
   * 获取字符串类型配置值。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @return 字符串配置值
   */
  @Override
  public String stringSetting(
          String valueGroup,
          String valueName,
          String defaultValue) {

    return getSetting(
            valueGroup,
            valueName,
            defaultValue,
            value -> value,
            "stringSetting");
  }

  /**
   * 获取整数类型配置值。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @return 整数配置值
   */
  @Override
  public Integer intSetting(
          String valueGroup,
          String valueName,
          Integer defaultValue) {

    return getSetting(
            valueGroup,
            valueName,
            defaultValue,
            Integer::valueOf,
            "intSetting");
  }

  /**
   * 获取长整数类型配置值。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @return 长整数配置值
   */
  @Override
  public Long longSetting(
          String valueGroup,
          String valueName,
          Long defaultValue) {

    return getSetting(
            valueGroup,
            valueName,
            defaultValue,
            Long::valueOf,
            "longSetting");
  }

  /**
   * 获取双精度浮点类型配置值。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @return 双精度浮点配置值
   */
  @Override
  public Double doubleSetting(
          String valueGroup,
          String valueName,
          Double defaultValue) {

    return getSetting(
            valueGroup,
            valueName,
            defaultValue,
            Double::valueOf,
            "doubleSetting");
  }

  /**
   * 获取布尔类型配置值。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @return 布尔配置值
   */
  @Override
  public Boolean booleanSetting(
          String valueGroup,
          String valueName,
          Boolean defaultValue) {

    return getSetting(
            valueGroup,
            valueName,
            defaultValue,
            this::parseBoolean,
            "booleanSetting");
  }

  /**
   * 获取对象类型配置值。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @param type 目标对象类型
   * @param <T> 目标对象类型
   * @return 对象类型配置值
   */
  @Override
  public <T> T objectSetting(
          String valueGroup,
          String valueName,
          T defaultValue,
          Class<T> type) {

    return getSetting(
            valueGroup,
            valueName,
            defaultValue,
            value -> JsonUtils.fromJson(value, type),
            "objectSetting");
  }

  /**
   * 获取配置值并转换为指定类型。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @param converter 配置值转换器
   * @param methodName 调用方法名称
   * @param <T> 配置值类型
   * @return 转换后的配置值
   */
  private <T> T getSetting(
          String valueGroup,
          String valueName,
          T defaultValue,
          Function<String, T> converter,
          String methodName) {

    try {
      ConfigPO configPO =
              getByGroupAndNameFromDB(
                      valueGroup,
                      valueName);

      if (configPO == null
              || !StringUtils.hasText(configPO.getValue())) {
        return defaultValue;
      }

      return converter.apply(configPO.getValue());
    } catch (Exception exception) {
      LOGGER.warn(
              "读取配置失败，方法={}，配置组={}，配置名={}",
              methodName,
              valueGroup,
              valueName,
              exception);

      return defaultValue;
    }
  }

  /**
   * 获取当前需要编辑的配置。
   *
   * @param configDTO 配置信息
   * @return 当前配置，不存在时返回 null
   */
  private ConfigPO getCurrentConfig(
          ConfigDTO configDTO) {

    if (configDTO.getId() != null) {
      return configDao.getbyId(configDTO.getId());
    }

    return getByGroupAndNameFromDB(
            configDTO.getValueGroup(),
            configDTO.getValueName());
  }

  /**
   * 校验配置基础参数。
   *
   * @param configDTO 配置信息
   * @return 参数校验结果
   */
  private Result<Void> checkParam(
          ConfigDTO configDTO) {

    if (configDTO == null) {
      return Result.buildParamIllegal(
              "配置信息不能为空");
    }

    if (!StringUtils.hasText(
            configDTO.getValueGroup())) {

      return Result.buildParamIllegal(
              "配置组不能为空");
    }

    if (!StringUtils.hasText(
            configDTO.getValueName())) {

      return Result.buildParamIllegal(
              "配置名称不能为空");
    }

    return Result.success(null);
  }

  /**
   * 校验配置状态。
   *
   * @param status 配置状态
   * @return 状态校验结果
   */
  private Result<Void> checkStatus(Integer status) {
    boolean normal =
            Objects.equals(
                    status,
                    ConfigStatusEnum.NORMAL.getCode());

    boolean disabled =
            Objects.equals(
                    status,
                    ConfigStatusEnum.DISABLE.getCode());

    if (!normal && !disabled) {
      return Result.buildParamIllegal(
              "配置状态只能是正常或禁用");
    }

    return Result.success(null);
  }

  /**
   * 初始化配置默认值。
   *
   * @param configDTO 配置信息
   */
  private void initConfig(ConfigDTO configDTO) {
    if (configDTO.getStatus() == null) {
      configDTO.setStatus(
              ConfigStatusEnum.NORMAL.getCode());
    }

    if (configDTO.getValue() == null) {
      configDTO.setValue("");
    }

    if (configDTO.getMemo() == null) {
      configDTO.setMemo("");
    }
  }

  /**
   * 将字符串转换为布尔值。
   *
   * @param value 配置值
   * @return 布尔值
   */
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

  /**
   * 根据配置组和配置名称查询配置。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @return 配置信息
   */
  private ConfigPO getByGroupAndNameFromDB(
          String valueGroup,
          String valueName) {

    return configDao.getByGroupAndName(
            valueGroup,
            valueName);
  }
}