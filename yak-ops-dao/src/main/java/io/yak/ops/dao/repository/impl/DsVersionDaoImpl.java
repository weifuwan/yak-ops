package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.DsVersion;
import io.yak.ops.dao.mapper.DsVersionMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.DsVersionDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class DsVersionDaoImpl extends BaseDao<DsVersion, DsVersionMapper> implements DsVersionDao {

    public DsVersionDaoImpl(@NonNull DsVersionMapper dsVersionMapper) {
        super(dsVersionMapper);
    }

    @Override
    public Optional<DsVersion> selectVersion() {
        List<DsVersion> dsVersions = mybatisMapper.selectList(null);
        if (CollectionUtils.isEmpty(dsVersions)) {
            log.info("There is no version information in the database");
        }
        if (dsVersions.size() > 1) {
            log.info("There are multiple version information in the database");
        }
        return dsVersions.stream().findFirst();
    }
}
