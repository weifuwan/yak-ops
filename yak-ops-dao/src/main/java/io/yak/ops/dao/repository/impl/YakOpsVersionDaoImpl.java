package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.YakOpsVersion;
import io.yak.ops.dao.mapper.YakOpsVersionMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.YakOpsVersionDao;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class YakOpsVersionDaoImpl extends BaseDao<YakOpsVersion, YakOpsVersionMapper> implements YakOpsVersionDao {

    public YakOpsVersionDaoImpl(@NonNull YakOpsVersionMapper yakOpsVersionMapper) {
        super(yakOpsVersionMapper);
    }

    @Override
    public Optional<YakOpsVersion> selectVersion() {
        List<YakOpsVersion> yakOpsVersions = mybatisMapper.selectList(null);
        if (CollectionUtils.isEmpty(yakOpsVersions)) {
            log.info("There is no version information in the database");
        }
        if (yakOpsVersions.size() > 1) {
            log.info("There are multiple version information in the database");
        }
        return yakOpsVersions.stream().findFirst();
    }
}
