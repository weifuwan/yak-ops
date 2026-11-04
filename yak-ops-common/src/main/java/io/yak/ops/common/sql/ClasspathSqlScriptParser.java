package io.yak.ops.common.sql;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClasspathSqlScriptParser implements SqlScriptParser {

    private final String sqlScriptPath;

    private final Charset charset;

    public ClasspathSqlScriptParser(String sqlScriptPath) {
        this.sqlScriptPath = sqlScriptPath;
        this.charset = StandardCharsets.UTF_8;
    }

    @Override
    public List<String> getAllSql() throws IOException {
        Resource sqlScriptResource = new ClassPathResource(sqlScriptPath);
        if (!sqlScriptResource.exists()) {
            log.warn("The sql script file {} doesn't exist", sqlScriptPath);
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        try (
                InputStream inputStream = sqlScriptResource.getInputStream();
                Reader sqlScriptReader = new InputStreamReader(inputStream, charset);
                LineNumberReader lineNumberReader = new LineNumberReader(sqlScriptReader)) {
            String sql;
            do {
                sql = parseNextSql(lineNumberReader);
                if (StringUtils.isNotBlank(sql)) {
                    result.add(sql);
                }
            } while (StringUtils.isNotBlank(sql));
        }
        return result;
    }

    private String parseNextSql(LineNumberReader lineNumberReader) throws IOException {
        String line;
        while ((line = lineNumberReader.readLine()) != null) {
            String trimLine = line.trim();
            if (StringUtils.isEmpty(trimLine) || isComment(trimLine)) {
                // Skip the empty line, comment line
                continue;
            }
            if (trimLine.startsWith("/*")) {
                skipLicenseHeader(lineNumberReader);
                continue;
            }
            if (trimLine.startsWith("delimiter")) {
                // begin to parse processor, until delimiter ;
                String[] split = trimLine.split(" ");
                if (split[1].equals(";")) {
                    continue;
                }
                return parseProcedure(lineNumberReader, split[1]);
            }
            // begin to parse sql until;
            List<String> sqlLines = new ArrayList<>();
            sqlLines.add(line);
            while (!line.endsWith(";")) {
                line = lineNumberReader.readLine();
                if (line == null) {
                    break;
                }
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                sqlLines.add(line);
            }
            return String.join("\n", sqlLines);
        }
        return null;
    }

    private void skipLicenseHeader(LineNumberReader lineNumberReader) throws IOException {
        String line;
        while ((line = lineNumberReader.readLine()) != null) {
            String trimLine = line.trim();
            if (StringUtils.isEmpty(trimLine) || isComment(trimLine)) {
                // Skip the empty line, comment line
                continue;
            }
            if (trimLine.startsWith("*/")) {
                break;
            }
        }
    }

    private String parseProcedure(LineNumberReader lineNumberReader, String delimiter) throws IOException {
        List<String> sqlLines = new ArrayList<>();
        // begin to parse processor, until delimiter ;
        String line;
        while (true) {
            line = lineNumberReader.readLine();
            if (line == null) {
                break;
            }
            if (StringUtils.isBlank(line)) {
                continue;
            }
            if (line.trim().startsWith(delimiter)) {
                break;
            }
            sqlLines.add(line);
        }
        return String.join("\n", sqlLines);
    }

    private boolean isComment(String line) {
        return line.startsWith("--") || line.startsWith("//");
    }
}
