package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.Cluster;
import io.yak.ops.dao.mapper.ClusterMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.ClusterDao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.NonNull;

@Repository
public class ClusterDaoImpl extends BaseDao<Cluster, ClusterMapper> implements ClusterDao {

    public ClusterDaoImpl(@NonNull ClusterMapper clusterMapper) {
        super(clusterMapper);
    }

    @Override
    public Cluster queryByClusterName(String name) {
        return mybatisMapper.queryByClusterName(name);
    }

    @Override
    public Cluster queryByClusterCode(Long clusterCode) {
        return mybatisMapper.queryByClusterCode(clusterCode);
    }

    @Override
    public List<Cluster> queryAllClusterList() {
        return mybatisMapper.queryAllClusterList();
    }

    @Override
    public IPage<Cluster> queryClusterListPaging(IPage<Cluster> page, String searchName) {
        return mybatisMapper.queryClusterListPaging(page, searchName);
    }

    @Override
    public boolean deleteByCode(Long code) {
        return mybatisMapper.deleteByCode(code) > 0;
    }
}
