package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.AuditLog;

import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * auditlog mapper interface
 */
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    IPage<AuditLog> queryAuditLog(IPage<AuditLog> page,
                                  @Param("modelTypeList") List<String> modelTypeList,
                                  @Param("operationTypeList") List<String> operationTypeList,
                                  @Param("userId") Integer userId,
                                  @Param("userName") String userName,
                                  @Param("modelName") String modelName,
                                  @Param("startDate") Date startDate,
                                  @Param("endDate") Date endDate);

}
