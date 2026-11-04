package io.yak.ops.api.service;

import io.yak.ops.api.service.impl.EnvironmentWorkerGroupRelationServiceImpl;
import io.yak.ops.dao.entity.EnvironmentWorkerGroupRelation;
import io.yak.ops.dao.mapper.EnvironmentWorkerGroupRelationMapper;

import java.util.List;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * environment service test
 */
@ExtendWith(MockitoExtension.class)
public class EnvironmentWorkerGroupRelationServiceTest {

    public static final Logger logger = LoggerFactory.getLogger(EnvironmentWorkerGroupRelationServiceTest.class);

    @InjectMocks
    private EnvironmentWorkerGroupRelationServiceImpl relationService;

    @Mock
    private EnvironmentWorkerGroupRelationMapper relationMapper;

    @Test
    public void testQueryEnvironmentWorkerGroupRelation() {
        Mockito.when(relationMapper.queryByEnvironmentCode(1L))
                .thenReturn(Lists.newArrayList(new EnvironmentWorkerGroupRelation()));
        List<EnvironmentWorkerGroupRelation> relations = relationService.queryEnvironmentWorkerGroupRelation(1L);
        logger.info(relations.toString());
        Assertions.assertEquals(1, relations.size());
    }

    @Test
    public void testQueryAllEnvironmentWorkerGroupRelationList() {
        Mockito.when(relationMapper.selectList(Mockito.any()))
                .thenReturn(Lists.newArrayList(new EnvironmentWorkerGroupRelation()));
        List<EnvironmentWorkerGroupRelation> relations = relationService.queryAllEnvironmentWorkerGroupRelationList();
        logger.info(relations.toString());
        Assertions.assertEquals(1, relations.size());
    }

}
