package io.yak.ops.dao.mapper;

import io.yak.ops.common.enums.WorkerGroupSource;
import io.yak.ops.dao.entity.WorkerGroup;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface WorkerGroupMapper extends BaseMapper<WorkerGroup> {

    /**
     * query all worker group
     *
     * @return worker group list
     */
    List<WorkerGroup> queryAllWorkerGroup();

    int deleteById(Integer id);

    int insert(WorkerGroup entity);

    int updateById(@Param("et") WorkerGroup entity);

    /**
     * query worker group by name
     *
     * @param name name
     * @return worker group list
     */
    List<WorkerGroup> queryWorkerGroupByName(@Param("name") String name);

    int updateAddrListByWorkerGroupName(@Param("name") String name,
                                        @Param("addrList") String addrList,
                                        @Param("source") WorkerGroupSource source);

    int deleteByWorkerGroupName(@Param("name") String name);
}
