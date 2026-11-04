package io.yak.ops.common.sql;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ClasspathSqlScriptParserTest {

    @Test
    void testMysqlDmlSql() throws IOException {
        ClasspathSqlScriptParser classpathSqlScriptParser = new ClasspathSqlScriptParser("sql/mysql_dml.sql");
        List<String> allSql = classpathSqlScriptParser.getAllSql();
        Assertions.assertEquals("drop PROCEDURE if EXISTS dolphin_t_ds_tenant_insert_default;", allSql.get(0));
        Assertions.assertEquals("CREATE PROCEDURE dolphin_t_ds_tenant_insert_default()\n" +
                "BEGIN\n" +
                "    IF\n" +
                "NOT EXISTS(SELECT 1\n" +
                "                   FROM t_ds_tenant\n" +
                "                   WHERE id = -1)\n" +
                "    THEN\n" +
                "        INSERT INTO `t_ds_tenant` VALUES ('-1', 'default', 'default tenant', '1', current_timestamp, current_timestamp);\n"
                +
                "END IF;\n" +
                "END;", String.join("", allSql.get(1)));
    }

    @Test
    void testMysqlDdlSql() throws IOException {
        ClasspathSqlScriptParser classpathSqlScriptParser = new ClasspathSqlScriptParser("sql/mysql_ddl.sql");
        List<String> allSql = classpathSqlScriptParser.getAllSql();
        Assertions.assertEquals("ALTER TABLE t_ds_workflow_definition DROP tenant_id;", allSql.get(0));
    }

    @Test
    void testIndentedLicenseHeaderSql() throws IOException {
        ClasspathSqlScriptParser classpathSqlScriptParser =
                new ClasspathSqlScriptParser("sql/indented_license_header.sql");
        List<String> allSql = classpathSqlScriptParser.getAllSql();
        Assertions.assertEquals(1, allSql.size());
        Assertions.assertEquals("SELECT 1;", allSql.get(0));
    }
}
