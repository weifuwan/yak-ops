package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.Cluster;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

public interface ClusterDao extends IDao<Cluster> {

    Cluster queryByClusterName(String name);

    Cluster queryByClusterCode(Long clusterCode);

    List<Cluster> queryAllClusterList();

    IPage<Cluster> queryClusterListPaging(IPage<Cluster> page, String searchName);

    boolean deleteByCode(Long code);
}
