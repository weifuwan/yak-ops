package io.yak.ops.security.service;

import io.yak.framework.common.PagingData;
import io.yak.ops.security.common.dto.oplog.OplogDTO;
import io.yak.ops.security.common.dto.oplog.OplogQueryDTO;
import io.yak.ops.security.common.vo.oplog.OplogOptionsVO;
import io.yak.ops.security.common.vo.oplog.OplogVO;

import java.util.List;

/**
 * 操作日志服务接口。
 *
 * @author weifuwan
 */
public interface OplogService {

  /** 保存操作日志。 */
  Long saveOplog(OplogDTO oplogDTO);

  /** 分页查询操作日志。 */
  PagingData<OplogVO> getOplogPage(
          OplogQueryDTO queryDTO);

  /** 根据操作日志 ID 查询日志详情。 */
  OplogVO getOplogDetailByOplogId(
          Long oplogId);

  /** 查询操作日志筛选选项。 */
  OplogOptionsVO getOptions();

  /**
   * 查询全部操作目标类型。
   *
   * <p>保留该方法兼容现有调用，新的管理页面优先使用
   * {@link #getOptions()}。</p>
   */
  List<String> listTargetType();
}
