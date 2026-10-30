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

import org.apache.dolphinscheduler.plugin.registry.jdbc.model.DO.JdbcRegistryLock;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface JdbcRegistryLockMapper extends BaseMapper<JdbcRegistryLock> {

    @Delete({"<script>",
            "delete from yak_ops_jdbc_registry_lock",
            "where client_id IN ",
            "<foreach item='clientId' index='index' collection='clientIds' open='(' separator=',' close=')'>",
            "   #{clientId}",
            "</foreach>",
            "</script>"})
    void deleteByClientIds(@Param("clientIds") List<Long> clientIds);
    /** Conditional cleanup prevents a stale purge snapshot from deleting a newly registered client's lock. */
    @Delete("delete from yak_ops_jdbc_registry_lock where id = #{id} and client_id = #{clientId}"
            + " and not exists (select 1 from yak_ops_jdbc_registry_client_heartbeat"
            + " where id = #{clientId})")
    int deleteOrphanByIdAndClientId(@Param("id") Long id, @Param("clientId") Long clientId);

}
