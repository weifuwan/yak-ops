/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.plugin.registry.jdbc.mapper;

import org.apache.dolphinscheduler.plugin.registry.jdbc.model.DO.JdbcRegistryData;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface JdbcRegistryDataMapper extends BaseMapper<JdbcRegistryData> {

    @Select("select * from yak_ops_jdbc_registry_data")
    List<JdbcRegistryData> selectAll();

    @Select("select * from yak_ops_jdbc_registry_data where data_key = #{key}")
    JdbcRegistryData selectByKey(@Param("key") String key);

    @Delete("delete from yak_ops_jdbc_registry_data where data_key = #{key}")
    void deleteByKey(@Param("key") String key);

    @Delete({"<script>",
            "delete from yak_ops_jdbc_registry_data",
            "where client_id IN ",
            "<foreach item='clientId' index='index' collection='clientIds' open='(' separator=',' close=')'>",
            "   #{clientId}",
            "</foreach>",
            "and data_type = #{dataType}",
            "</script>"})
    void deleteByClientIds(@Param("clientIds") List<Long> clientIds, @Param("dataType") String dataType);

    /**
     * Only purge the exact orphaned ephemeral row after rechecking its owner in the database.
     * A client registered after a purge snapshot must never have its live data removed.
     */
    @Delete("delete from yak_ops_jdbc_registry_data where id = #{id} and client_id = #{clientId}"
            + " and data_type = 'EPHEMERAL'"
            + " and not exists (select 1 from yak_ops_jdbc_registry_client_heartbeat"
            + " where id = #{clientId})")
    int deleteOrphanByIdAndClientId(@Param("id") Long id, @Param("clientId") Long clientId);

}
