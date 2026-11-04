package io.yak.ops.plugin.task.api.model;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.enums.DataType;
import io.yak.ops.plugin.task.api.enums.Direct;

import java.util.ArrayList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Contains some test case related to Property.
 */
public class JSONUtilsTest {

    @Test
    public void createArrayNodeTest() {
        Property property = new Property();
        property.setProp("ds");
        property.setDirect(Direct.IN);
        property.setType(DataType.VARCHAR);
        property.setValue("sssssss");
        String str =
                "[{\"prop\":\"ds\",\"direct\":\"IN\",\"type\":\"VARCHAR\",\"value\":\"sssssss\"},{\"prop\":\"ds\",\"direct\":\"IN\",\"type\":\"VARCHAR\",\"value\":\"sssssss\"}]";
        JsonNode jsonNode = JSONUtils.toJsonNode(property);

        ArrayNode arrayNode = JSONUtils.createArrayNode();
        ArrayList<JsonNode> objects = new ArrayList<>();
        objects.add(jsonNode);
        objects.add(jsonNode);

        ArrayNode jsonNodes = arrayNode.addAll(objects);
        String s = JSONUtils.toJsonString(jsonNodes);
        Assertions.assertEquals(s, str);

    }

    @Test
    public void toJsonNodeTest() {
        Property property = new Property();
        property.setProp("ds");
        property.setDirect(Direct.IN);
        property.setType(DataType.VARCHAR);
        property.setValue("sssssss");
        String str = "{\"prop\":\"ds\",\"direct\":\"IN\",\"type\":\"VARCHAR\",\"value\":\"sssssss\"}";

        JsonNode jsonNodes = JSONUtils.toJsonNode(property);
        String s = JSONUtils.toJsonString(jsonNodes);
        Assertions.assertEquals(s, str);

    }

    @Test
    public void convert2Property() {
        Property property = new Property();
        property.setProp("ds");
        property.setDirect(Direct.IN);
        property.setType(DataType.VARCHAR);
        property.setValue("sssssss");
        String str = "{\"direct\":\"IN\",\"prop\":\"ds\",\"type\":\"VARCHAR\",\"value\":\"sssssss\"}";
        Property property1 = JSONUtils.parseObject(str, Property.class);
        Direct direct = property1.getDirect();
        Assertions.assertEquals(Direct.IN, direct);
    }
}
