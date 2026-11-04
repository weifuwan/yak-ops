package io.yak.ops.plugin.task.api.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import lombok.SneakyThrows;

class TaskOutputParameterParserTest {

    @Test
    void testEmptyLog() {
        List<String> varPools = getLogs("/outputParam/emptyVarPoolLog.txt");
        TaskOutputParameterParser taskOutputParameterParser = new TaskOutputParameterParser();
        varPools.forEach(taskOutputParameterParser::appendParseLog);
        Assertions.assertTrue(taskOutputParameterParser.getTaskOutputParams().isEmpty());
    }

    @Test
    void testOneLineLog() {
        List<String> varPools = getLogs("/outputParam/onelineVarPoolLog.txt");
        TaskOutputParameterParser taskOutputParameterParser = new TaskOutputParameterParser();
        varPools.forEach(taskOutputParameterParser::appendParseLog);
        assertEquals(ImmutableMap.of("name", "name=tom"), taskOutputParameterParser.getTaskOutputParams());
    }

    @Test
    void testOneVarPoolInMultiLineLog() {
        List<String> varPools = getLogs("/outputParam/oneVarPollInMultiLineLog.txt");
        TaskOutputParameterParser taskOutputParameterParser = new TaskOutputParameterParser();
        varPools.forEach(taskOutputParameterParser::appendParseLog);
        assertEquals(ImmutableMap.of("sql",
                "select * from table\n" +
                        "where\n" +
                        "id = 1\n"),
                taskOutputParameterParser.getTaskOutputParams());
    }

    @Test
    void testVarPoolInMultiLineLog() {
        List<String> varPools = getLogs("/outputParam/multipleVarPool.txt");
        TaskOutputParameterParser taskOutputParameterParser = new TaskOutputParameterParser();
        varPools.forEach(taskOutputParameterParser::appendParseLog);
        assertEquals(ImmutableMap.of("name", "tom", "age", "1"), taskOutputParameterParser.getTaskOutputParams());
    }

    @Test
    void textVarPoolExceedMaxRows() {
        List<String> varPools = getLogs("/outputParam/maxRowsVarPool.txt");
        TaskOutputParameterParser taskOutputParameterParser = new TaskOutputParameterParser(2, Integer.MAX_VALUE);
        varPools.forEach(taskOutputParameterParser::appendParseLog);
        assertEquals(Collections.emptyMap(), taskOutputParameterParser.getTaskOutputParams());

        taskOutputParameterParser = new TaskOutputParameterParser();
        varPools.forEach(taskOutputParameterParser::appendParseLog);
        assertEquals(ImmutableMap.of("name", "name=tom\n" +
                "name=name=tom\n" +
                "name=name=tom\n" +
                "name=name=tom\n" +
                "name=name=tom"), taskOutputParameterParser.getTaskOutputParams());

    }

    @Test
    void textVarPoolExceedMaxLength() {
        List<String> varPools = getLogs("/outputParam/maxLengthVarPool.txt");
        TaskOutputParameterParser taskOutputParameterParser = new TaskOutputParameterParser(2, 10);
        varPools.forEach(taskOutputParameterParser::appendParseLog);
        assertEquals(Collections.emptyMap(), taskOutputParameterParser.getTaskOutputParams());

        taskOutputParameterParser = new TaskOutputParameterParser();
        varPools.forEach(taskOutputParameterParser::appendParseLog);
        assertEquals(ImmutableMap.of("name", "123456789\n" +
                "12345\n"), taskOutputParameterParser.getTaskOutputParams());

    }

    @SneakyThrows
    private List<String> getLogs(String file) {
        URI uri = TaskOutputParameterParserTest.class.getResource(file).toURI();
        return Files.lines(Paths.get(uri)).collect(Collectors.toList());
    }
}
