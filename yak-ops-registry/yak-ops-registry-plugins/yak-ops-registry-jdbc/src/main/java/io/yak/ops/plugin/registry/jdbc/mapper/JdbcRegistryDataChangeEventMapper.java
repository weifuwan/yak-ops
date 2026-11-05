package io.yak.ops.plugin.registry.jdbc.mapper;

import io.yak.ops.plugin.registry.jdbc.model.DO.JdbcRegistryDataChangeEvent;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface JdbcRegistryDataChangeEventMapper extends BaseMapper<JdbcRegistryDataChangeEvent> {

    @Select("select max(id) from t_yak_ops_jdbc_registry_data_change_event")
    Long getMaxId();

    @Select("select * from t_yak_ops_jdbc_registry_data_change_event where id > #{id} order by id asc limit 1000")
    List<JdbcRegistryDataChangeEvent> selectJdbcRegistryDataChangeEventWhereIdAfter(@Param("id") long id);

    @Delete("delete from t_yak_ops_jdbc_registry_data_change_event where create_time < #{createTime}")
    void deleteJdbcRegistryDataChangeEventBeforeCreateTime(@Param("createTime") Date createTime);
}
