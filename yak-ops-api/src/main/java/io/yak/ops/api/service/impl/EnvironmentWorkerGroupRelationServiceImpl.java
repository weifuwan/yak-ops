package io.yak.ops.api.service.impl;

import io.yak.ops.api.service.EnvironmentWorkerGroupRelationService;
import io.yak.ops.dao.entity.EnvironmentWorkerGroupRelation;
import io.yak.ops.dao.mapper.EnvironmentWorkerGroupRelationMapper;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * environment worker group relation service impl
 */
@Service
@Slf4j
public class EnvironmentWorkerGroupRelationServiceImpl extends BaseServiceImpl
        implements
            EnvironmentWorkerGroupRelationService {

    @Autowired
    private EnvironmentWorkerGroupRelationMapper environmentWorkerGroupRelationMapper;

    /**
     * query environment worker group relation
     *
     * @param environmentCode environment code
     */
    @Override
    public List<EnvironmentWorkerGroupRelation> queryEnvironmentWorkerGroupRelation(Long environmentCode) {
        return environmentWorkerGroupRelationMapper.queryByEnvironmentCode(environmentCode);
    }

    /**
     * query all environment worker group relation
     *
     * @return all relation list
     */
    @Override
    public List<EnvironmentWorkerGroupRelation> queryAllEnvironmentWorkerGroupRelationList() {
        return environmentWorkerGroupRelationMapper.selectList(null);
    }
}
