package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.Cluster;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface ClusterMapper extends BaseMapper<Cluster> {

    /**
     * query cluster by name
     *
     * @param name name
     * @return cluster
     */
    Cluster queryByClusterName(@Param("clusterName") String name);

    /**
     * query cluster by code
     *
     * @param clusterCode clusterCode
     * @return cluster
     */
    Cluster queryByClusterCode(@Param("clusterCode") Long clusterCode);

    /**
     * query all cluster list
     * @return cluster list
     */
    List<Cluster> queryAllClusterList();

    /**
     * cluster page
     * @param page page
     * @param searchName searchName
     * @return cluster IPage
     */
    IPage<Cluster> queryClusterListPaging(IPage<Cluster> page, @Param("searchName") String searchName);

    /**
     * delete cluster by code
     *
     * @param code code
     * @return int
     */
    int deleteByCode(@Param("code") Long code);
}
