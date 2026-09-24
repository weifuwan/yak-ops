package io.yak.ops.security.service;

import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.ops.security.common.dto.config.ConfigDTO;
import io.yak.ops.security.common.dto.config.ConfigQueryDTO;
import io.yak.ops.security.common.vo.config.ConfigVO;

import java.util.List;

/**
 * 配置服务接口。
 *
 * @author weifuwan
 */
public interface ConfigService {

  /**
   * 新增配置。
   *
   * @param configDTO 配置信息
   * @param operator 操作人
   * @return 新增结果及配置 ID
   */
  Result<Long> addConfig(
          ConfigDTO configDTO,
          String operator);

  /**
   * 根据配置参数新增配置。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param value 配置值
   * @param operator 操作人
   * @return 新增结果及配置 ID
   */
  Result<Long> addConfig(
          String valueGroup,
          String valueName,
          String value,
          String operator);

  /**
   * 删除配置。
   *
   * @param configId 配置 ID
   * @param operator 操作人
   * @return 删除结果
   */
  Result<Void> delConfig(
          Long configId,
          String operator);

  /**
   * 编辑配置。
   *
   * @param configDTO 配置信息
   * @param operator 操作人
   * @return 编辑结果
   */
  Result<Void> editConfig(
          ConfigDTO configDTO,
          String operator);

  /**
   * 切换配置状态。
   *
   * @param configId 配置 ID
   * @param status 配置状态
   * @param operator 操作人
   * @return 状态切换结果
   */
  Result<Void> switchConfig(
          Long configId,
          Integer status,
          String operator);

  /**
   * 分页查询配置。
   *
   * @param queryDTO 查询条件
   * @return 配置分页数据
   */
  PagingData<ConfigVO> pagingConfig(ConfigQueryDTO queryDTO);

  /**
   * 根据条件查询配置。
   *
   * <p>保留原有方法名称，避免影响现有调用方。
   *
   * @param condition 查询条件
   * @return 配置列表
   */
  List<ConfigVO> queryByCondt(ConfigDTO condition);

  /**
   * 查询全部配置分组。
   *
   * @return 配置分组列表
   */
  List<String> listGroups();

  /**
   * 根据分组查询配置。
   *
   * @param valueGroup 配置分组
   * @return 配置列表
   */
  List<ConfigVO> listConfigByGroup(String valueGroup);

  /**
   * 根据配置 ID 查询配置。
   *
   * @param configId 配置 ID
   * @return 配置信息
   */
  ConfigVO getConfigById(Long configId);

  /**
   * 获取字符串类型配置值。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @return 字符串配置值
   */
  String stringSetting(
          String valueGroup,
          String valueName,
          String defaultValue);

  /**
   * 获取布尔类型配置值。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @return 布尔配置值
   */
  Boolean booleanSetting(
          String valueGroup,
          String valueName,
          Boolean defaultValue);

  /**
   * 获取整数类型配置值。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @return 整数配置值
   */
  Integer intSetting(
          String valueGroup,
          String valueName,
          Integer defaultValue);

  /**
   * 获取长整数类型配置值。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @return 长整数配置值
   */
  Long longSetting(
          String valueGroup,
          String valueName,
          Long defaultValue);

  /**
   * 获取双精度浮点类型配置值。
   *
   * @param valueGroup 配置分组
   * @param valueName 配置名称
   * @param defaultValue 默认值
   * @return 双精度浮点配置值
   */
  Double doubleSetting(
          String valueGroup,
          String valueName,
          Double defaultValue);

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
  <T> T objectSetting(
          String valueGroup,
          String valueName,
          T defaultValue,
          Class<T> type);
}
