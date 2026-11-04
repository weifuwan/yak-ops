package io.yak.ops.api.service;

import io.yak.ops.dao.entity.EnvironmentWorkerGroupRelation;

import java.util.List;

public interface EnvironmentWorkerGroupRelationService {

    /**
     * query environment worker group relation
     *
     * @param environmentCode environment code
     */
    List<EnvironmentWorkerGroupRelation> queryEnvironmentWorkerGroupRelation(Long environmentCode);

    /**
     * query all environment worker group relation
     *
     * @return all relation list
     */
    List<EnvironmentWorkerGroupRelation> queryAllEnvironmentWorkerGroupRelationList();
}
