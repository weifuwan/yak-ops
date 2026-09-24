package io.yak.framework.security.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.security.common.dto.oplog.OplogQueryDTO;
import io.yak.framework.security.common.entity.Oplog;

import java.util.List;

/**
 * 操作日志数据访问接口。
 */
public interface OplogDao {
    IPage<Oplog> selectPageWithoutDetail(OplogQueryDTO queryDTO);

    Oplog selectByOplogId(Long oplogId);

    void insert(Oplog oplog);

    List<String> listOperateType();

    List<String> listOperatePage();

    List<String> listOperationMethods();

    List<String> listTargetType();
}
