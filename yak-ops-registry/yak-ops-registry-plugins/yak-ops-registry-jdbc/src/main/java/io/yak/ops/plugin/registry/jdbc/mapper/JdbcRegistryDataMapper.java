package io.yak.ops.plugin.registry.jdbc.mapper;

import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryData;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface JdbcRegistryDataMapper extends BaseMapper<JdbcRegistryData> {

    @Select("select * from t_ds_jdbc_registry_data")
    List<JdbcRegistryData> selectAll();

    @Select("select * from t_ds_jdbc_registry_data where data_key = #{key}")
    JdbcRegistryData selectByKey(@Param("key") String key);

    @Delete("delete from t_ds_jdbc_registry_data where data_key = #{key}")
    void deleteByKey(@Param("key") String key);

    @Delete({"<script>",
            "delete from t_ds_jdbc_registry_data",
            "where client_id IN ",
            "<foreach item='clientId' index='index' collection='clientIds' open='(' separator=',' close=')'>",
            "   #{clientId}",
            "</foreach>",
            "and data_type = #{dataType}",
            "</script>"})
    void deleteByClientIds(@Param("clientIds") List<Long> clientIds, @Param("dataType") String dataType);

}
