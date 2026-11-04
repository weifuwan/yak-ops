package io.yak.ops.plugin.task.api.utils;

import io.yak.ops.plugin.task.api.enums.DataType;
import io.yak.ops.plugin.task.api.enums.Direct;
import io.yak.ops.plugin.task.api.model.Property;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;
import com.google.common.truth.Truth;

class VarPoolUtilsTest {

    @Test
    void mergeVarPool() {
        Truth.assertThat(VarPoolUtils.mergeVarPool(null)).isEmpty();

        // Override the value of the same property
        // Merge the property with different key.
        List<Property> varpool1 = Lists.newArrayList(new Property("name", Direct.OUT, DataType.VARCHAR, "tom"));
        List<Property> varpool2 = Lists.newArrayList(
                new Property("name", Direct.OUT, DataType.VARCHAR, "tim"),
                new Property("age", Direct.OUT, DataType.INTEGER, "10"));

        Truth.assertThat(VarPoolUtils.mergeVarPool(Lists.newArrayList(varpool1, varpool2)))
                .containsExactly(
                        new Property("name", Direct.OUT, DataType.VARCHAR, "tim"),
                        new Property("age", Direct.OUT, DataType.INTEGER, "10"));

    }

    @Test
    void subtractVarPool() {
        Truth.assertThat(VarPoolUtils.subtractVarPool(null, null)).isNull();
        List<Property> varpool1 = Lists.newArrayList(
                new Property("name", Direct.OUT, DataType.VARCHAR, "tom"),
                new Property("age", Direct.OUT, DataType.INTEGER, "10"));
        List<Property> varpool2 = Lists.newArrayList(new Property("name", Direct.OUT, DataType.VARCHAR, "tom"));
        List<Property> varpool3 = Lists.newArrayList(new Property("location", Direct.OUT, DataType.VARCHAR, "china"));

        Truth.assertThat(VarPoolUtils.subtractVarPool(varpool1, Lists.newArrayList(varpool2, varpool3)))
                .containsExactly(new Property("age", Direct.OUT, DataType.INTEGER, "10"));
    }
}
