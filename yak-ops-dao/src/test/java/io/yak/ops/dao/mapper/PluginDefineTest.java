package io.yak.ops.dao.mapper;

import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.PluginDefine;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PluginDefineTest extends BaseDaoTest {

    @Autowired
    private PluginDefineMapper pluginDefineMapper;

    @Test
    public void testQueryAllPluginDefineList() {
        createPluginDefine();
        List<PluginDefine> pluginDefines = pluginDefineMapper.queryAllPluginDefineList();
        Assertions.assertTrue(pluginDefines.size() > 0);
    }

    @Test
    public void testQeryByPluginType() {
        PluginDefine pluginDefine = createPluginDefine();
        List<PluginDefine> pluginDefines = pluginDefineMapper.queryByPluginType(pluginDefine.getPluginType());
        Assertions.assertTrue(pluginDefines.size() > 0);
        Assertions.assertEquals(pluginDefines.get(0).getPluginType(), pluginDefine.getPluginType());
    }

    @Test
    public void testQueryByNameAndType() {
        PluginDefine pluginDefine = createPluginDefine();
        PluginDefine pluginDefineSaved =
                pluginDefineMapper.queryByNameAndType(pluginDefine.getPluginName(), pluginDefine.getPluginType());
        Assertions.assertNotNull(pluginDefineSaved);
        Assertions.assertEquals(pluginDefineSaved.getPluginType(), pluginDefine.getPluginType());
        Assertions.assertEquals(pluginDefineSaved.getPluginName(), pluginDefine.getPluginName());
    }

    /**
     * insert
     *
     * @return PluginDefine
     */
    private PluginDefine createPluginDefine() {
        PluginDefine pluginDefine = new PluginDefine("test plugin", "alert", "");
        pluginDefineMapper.insert(pluginDefine);
        return pluginDefine;
    }
}
