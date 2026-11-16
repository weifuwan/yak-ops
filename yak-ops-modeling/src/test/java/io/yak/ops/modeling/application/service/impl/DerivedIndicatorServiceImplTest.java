package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.AtomIndicatorPO;
import io.yak.ops.dao.entity.TimePeriodPO;
import io.yak.ops.dao.repository.modeling.ModelingAssetDao;
import io.yak.ops.modeling.api.dto.derivedindicator.DerivedIndicatorDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DerivedIndicatorServiceImplTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ModelingAssetDao modelingAssetDao;

    @InjectMocks
    private DerivedIndicatorServiceImpl service;

    @Test
    void shouldRejectUnknownModifierReference() {
        when(modelingAssetDao.queryById("atom-indicator", "atomic-id")).thenReturn(new AtomIndicatorPO());
        when(modelingAssetDao.queryById("time-period", "time-period-id")).thenReturn(new TimePeriodPO());

        DerivedIndicatorDTO dto = new DerivedIndicatorDTO();
        dto.setCnName("日活跃用户数");
        dto.setEnShortName("daily_active_user");
        dto.setAtomicId("atomic-id");
        dto.setTimePeriodId("time-period-id");
        dto.setModifierId("missing-modifier");

        assertThrows(IllegalArgumentException.class, () -> service.create(dto, "alice"));
        verify(modelingAssetDao, never()).insert(eq("derivative-indicator"), any());
    }
}
