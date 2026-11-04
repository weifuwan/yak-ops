package io.yak.ops.common.sql;

import java.io.IOException;
import java.util.List;

public interface SqlScriptParser {

    List<String> getAllSql() throws IOException;

}
