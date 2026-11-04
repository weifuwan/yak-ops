package io.yak.ops.plugin.datasource.postgresql;

import io.yak.ops.plugin.datasource.postgresql.param.PostgreSQLDataSourceProcessor;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PostgreSQLDataSourceProcessorTest {

    private final PostgreSQLDataSourceProcessor processor = new PostgreSQLDataSourceProcessor();

    @Test
    public void testSingleDoBlock() {
        String sql = "DO $$ BEGIN RAISE NOTICE 'Hello'; END $$;";
        List<String> parts = processor.splitAndRemoveComment(sql);
        Assertions.assertEquals(1, parts.size());
        Assertions.assertEquals("DO $$ BEGIN RAISE NOTICE 'Hello'; END $$", parts.get(0));
    }

    @Test
    public void testDoBlockAndInsert() {
        String sql = "DO $$ BEGIN RAISE NOTICE 'Start'; END $$;\nINSERT INTO test_table(id) VALUES (1);";
        List<String> parts = processor.splitAndRemoveComment(sql);
        Assertions.assertEquals(2, parts.size());
    }

    @Test
    public void testStandardSql() {
        String sql = "INSERT INTO test_table(id) VALUES (1); UPDATE test_table SET id = 2 WHERE id = 1;";
        List<String> parts = processor.splitAndRemoveComment(sql);
        Assertions.assertEquals(2, parts.size());
        Assertions.assertEquals("INSERT INTO test_table(id) VALUES (1)", parts.get(0));
        Assertions.assertEquals("UPDATE test_table SET id = 2 WHERE id = 1", parts.get(1));
    }

    @Test
    public void testCustomDollarTag() {
        String sql = "DO $func$ BEGIN RAISE NOTICE 'Tag test'; END $func$;";
        List<String> parts = processor.splitAndRemoveComment(sql);
        Assertions.assertEquals(1, parts.size());
        Assertions.assertTrue(parts.get(0).contains("$func$"));
        Assertions.assertEquals("DO $func$ BEGIN RAISE NOTICE 'Tag test'; END $func$", parts.get(0));
    }

    @Test
    public void testCommentsPreserved() {
        String sql =
                "-- comment here\nDO $$ BEGIN NULL; END $$;\n-- trailing comment\nINSERT INTO test_table VALUES (5);";
        List<String> parts = processor.splitAndRemoveComment(sql);
        Assertions.assertEquals(2, parts.size());
        Assertions.assertEquals("DO $$ BEGIN NULL; END $$", parts.get(0));
        Assertions.assertEquals("INSERT INTO test_table VALUES (5)", parts.get(1));
    }

    @Test
    public void testDoBlockWithSemicolonInsideString() {
        String sql = "DO $$ BEGIN RAISE NOTICE 'hello; world'; END $$;";
        List<String> parts = processor.splitAndRemoveComment(sql);
        Assertions.assertEquals(1, parts.size());
        Assertions.assertEquals("DO $$ BEGIN RAISE NOTICE 'hello; world'; END $$", parts.get(0));
    }

    @Test
    public void testDoBlockWithDollarTagInsideString() {
        String sql = "DO $$ BEGIN RAISE NOTICE 'this has $DO$ inside'; END $$;";
        List<String> parts = processor.splitAndRemoveComment(sql);
        Assertions.assertEquals(1, parts.size());
        Assertions.assertEquals("DO $$ BEGIN RAISE NOTICE 'this has $DO$ inside'; END $$", parts.get(0));
    }

    @Test
    public void testMultipleStatementsWithDoBlock() {
        String sql = "DO $$ BEGIN RAISE NOTICE 'msg'; END $$; INSERT INTO test_table VALUES (1);";
        List<String> parts = processor.splitAndRemoveComment(sql);
        Assertions.assertEquals(2, parts.size());
        Assertions.assertEquals("DO $$ BEGIN RAISE NOTICE 'msg'; END $$", parts.get(0));
        Assertions.assertEquals("INSERT INTO test_table VALUES (1)", parts.get(1));
    }

    @Test
    public void testCustomDollarTagBlock() {
        String sql = "DO $func$ BEGIN RAISE NOTICE 'custom tag'; END $func$;";
        List<String> parts = processor.splitAndRemoveComment(sql);
        Assertions.assertEquals(1, parts.size());
        Assertions.assertEquals("DO $func$ BEGIN RAISE NOTICE 'custom tag'; END $func$", parts.get(0));
    }

    @Test
    public void testDoBlockWithEscapedSingleQuote() {
        String sql = "DO $$ BEGIN RAISE NOTICE 'don''t split here'; END $$;";
        List<String> parts = processor.splitAndRemoveComment(sql);
        Assertions.assertEquals(1, parts.size());
        Assertions.assertEquals("DO $$ BEGIN RAISE NOTICE 'don''t split here'; END $$", parts.get(0));
    }
}
