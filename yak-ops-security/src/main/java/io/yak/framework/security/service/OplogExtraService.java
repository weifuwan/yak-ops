package io.yak.framework.security.service;

import io.yak.framework.security.common.enums.oplog.OplogCode;

import java.util.List;

/**
 * 操作日志扩展服务接口。
 *
 * @author weifuwan
 */
public interface OplogExtraService {

  /**
   * 根据类型查询操作日志扩展名称。
   *
   * @param type 操作日志扩展类型
   * @return 操作日志扩展名称列表
   */
  List<String> getOplogExtraNameListByType(
          Integer type);

  /**
   * 批量保存操作日志扩展信息。
   *
   * @param nameList 扩展名称列表
   * @param oplogCode 操作日志类型
   */
  void saveOplogExtraList(
          List<String> nameList,
          OplogCode oplogCode);
}