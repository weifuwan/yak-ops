package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import org.junit.jupiter.api.Test;

class DwLayerVOTest {

    @Test
    void shouldReturnNullTypeNameWhenLayerTypeIsMissing() {
        assertNull(new DwLayerVO().getDwTypeName());
    }

    @Test
    void shouldReturnLayerTypeNameWhenLayerTypeIsPresent() {
        DwLayerVO vo = new DwLayerVO();
        vo.setDwType(DwTypeEnum.PUBLIC_LAYER);

        assertEquals("公共层", vo.getDwTypeName());
    }

    @Test
    void shouldReturnStoredTypeNameWhenLayerTypeIsMissing() {
        DwLayerVO vo = new DwLayerVO();
        vo.setDwTypeName("未分层");

        assertEquals("未分层", vo.getDwTypeName());
    }
}
