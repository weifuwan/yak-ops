package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.entity.OplogExtra;
import io.yak.framework.security.common.enums.oplog.OplogCode;
import io.yak.framework.security.dao.OplogExtraDao;
import io.yak.framework.security.service.OplogExtraService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 操作日志扩展服务实现类。
 *
 * @author weifuwan
 */
@Service("yakSecurityOplogExtraServiceImpl")
public class OplogExtraServiceImpl
        implements OplogExtraService {

  private final OplogExtraDao oplogExtraDao;

  /**
   * 创建操作日志扩展服务。
   *
   * @param oplogExtraDao 操作日志扩展数据访问对象
   */
  public OplogExtraServiceImpl(
          OplogExtraDao oplogExtraDao) {

    this.oplogExtraDao = oplogExtraDao;
  }

  /**
   * 批量保存操作日志扩展信息。
   *
   * @param nameList 扩展名称列表
   * @param oplogCode 操作日志类型
   */
  @Override
  @Transactional(
          transactionManager =
                  "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void saveOplogExtraList(
          List<String> nameList,
          OplogCode oplogCode) {

    if (oplogCode == null) {
      return;
    }

    List<String> validNameList =
            normalizeNames(nameList);

    if (validNameList.isEmpty()) {
      return;
    }

    List<OplogExtra> oplogExtraList =
            buildOplogExtraList(
                    validNameList,
                    oplogCode);

    if (oplogExtraList.isEmpty()) {
      return;
    }

    oplogExtraDao.insertBatch(
            oplogExtraList);
  }

  /**
   * 根据类型查询操作日志扩展名称。
   *
   * @param type 操作日志扩展类型
   * @return 操作日志扩展名称列表
   */
  @Override
  public List<String> getOplogExtraNameListByType(
          Integer type) {

    if (type == null) {
      return new ArrayList<>();
    }

    List<OplogExtra> oplogExtraList =
            oplogExtraDao.selectListByType(
                    type);

    if (CollectionUtils.isEmpty(
            oplogExtraList)) {

      return new ArrayList<>();
    }

    return oplogExtraList.stream()
            .filter(Objects::nonNull)
            .map(OplogExtra::getInfo)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .collect(Collectors.toList());
  }

  /**
   * 构建操作日志扩展实体列表。
   *
   * @param nameList 扩展名称列表
   * @param oplogCode 操作日志类型
   * @return 操作日志扩展实体列表
   */
  private List<OplogExtra> buildOplogExtraList(
          List<String> nameList,
          OplogCode oplogCode) {

    List<OplogExtra> oplogExtraList =
            new ArrayList<>(nameList.size());

    for (String name : nameList) {
      OplogExtra oplogExtra =
              new OplogExtra();

      oplogExtra.setInfo(name);
      oplogExtra.setType(
              oplogCode.getType());

      /*
       * 原实现缺少这一行，
       * 导致最终批量插入空集合。
       */
      oplogExtraList.add(
              oplogExtra);
    }

    return oplogExtraList;
  }

  /**
   * 清理操作日志扩展名称。
   *
   * <p>过滤空名称、去除首尾空格并去重。
   *
   * @param nameList 原始名称列表
   * @return 有效名称列表
   */
  private List<String> normalizeNames(
          List<String> nameList) {

    if (CollectionUtils.isEmpty(nameList)) {
      return new ArrayList<>();
    }

    return nameList.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .collect(Collectors.toList());
  }
}