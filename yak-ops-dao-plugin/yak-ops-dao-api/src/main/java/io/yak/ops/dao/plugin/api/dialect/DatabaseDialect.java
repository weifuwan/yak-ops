package io.yak.ops.dao.plugin.api.dialect;

public interface DatabaseDialect {

    boolean tableExists(String tableName);

}
