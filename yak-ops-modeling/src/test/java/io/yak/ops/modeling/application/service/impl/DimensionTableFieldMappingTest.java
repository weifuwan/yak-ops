package io.yak.ops.modeling.application.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.yak.ops.dao.entity.DimensionTableFieldPO;
import java.util.Arrays;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

class DimensionTableFieldMappingTest {

    @Test
    void shouldUseFieldPrecisionWithoutLegacyPrecisionAlias() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfo tableInfo = TableInfoHelper.initTableInfo(assistant, DimensionTableFieldPO.class);

        assertEquals("t_yak_ops_dimension_table_fields", tableInfo.getTableName());

        TableFieldInfo precisionField = tableInfo.getFieldList().stream()
                .filter(field -> "fieldPrecision".equals(field.getProperty()))
                .findFirst()
                .orElse(null);
        assertNotNull(precisionField);
        assertEquals("field_precision", precisionField.getColumn());

        assertFieldColumn(tableInfo, "ordinal", "ordinal");
        assertFieldColumn(tableInfo, "fieldScale", "field_scale");
        assertFieldColumn(tableInfo, "datetimePrecision", "datetime_precision");
        assertFieldColumn(tableInfo, "defaultValue", "default_value");

        String sqlSelect = tableInfo.getAllSqlSelect();
        assertNotNull(sqlSelect);
        assertTrue(sqlSelect.contains("field_precision"));
        assertFalse(sqlSelect.matches("(?is).*field_precision\\s+AS\\s+precision(?:\\s|,|$).*"), sqlSelect);

        assertTrue(Arrays.stream(DimensionTableFieldPO.class.getDeclaredFields())
                .anyMatch(field -> "fieldPrecision".equals(field.getName())));
        assertFalse(Arrays.stream(DimensionTableFieldPO.class.getDeclaredFields())
                .anyMatch(field -> "precision".equals(field.getName())));
    }

    private void assertFieldColumn(TableInfo tableInfo, String property, String column) {
        TableFieldInfo field = tableInfo.getFieldList().stream()
                .filter(item -> property.equals(item.getProperty()))
                .findFirst()
                .orElse(null);
        assertNotNull(field);
        assertEquals(column, field.getColumn());
    }
}
