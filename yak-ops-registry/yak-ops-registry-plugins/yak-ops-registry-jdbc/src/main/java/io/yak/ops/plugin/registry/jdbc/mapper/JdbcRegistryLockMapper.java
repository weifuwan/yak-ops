package io.yak.ops.plugin.registry.jdbc.mapper;

import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryLock;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface JdbcRegistryLockMapper extends BaseMapper<JdbcRegistryLock> {

    @Delete({"<script>",
            "delete from t_ds_jdbc_registry_lock",
            "where client_id IN ",
            "<foreach item='clientId' index='index' collection='clientIds' open='(' separator=',' close=')'>",
            "   #{clientId}",
            "</foreach>",
            "</script>"})
    void deleteByClientIds(@Param("clientIds") List<Long> clientIds);
}
