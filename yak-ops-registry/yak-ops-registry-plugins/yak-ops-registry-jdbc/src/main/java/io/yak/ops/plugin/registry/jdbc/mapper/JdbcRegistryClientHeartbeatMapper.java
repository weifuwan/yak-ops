package io.yak.ops.plugin.registry.jdbc.mapper;

import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryClientHeartbeat;

import org.apache.ibatis.annotations.Select;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface JdbcRegistryClientHeartbeatMapper extends BaseMapper<JdbcRegistryClientHeartbeat> {

    @Select("select * from t_ds_jdbc_registry_client_heartbeat")
    List<JdbcRegistryClientHeartbeat> selectAll();

}
