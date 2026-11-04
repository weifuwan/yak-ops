package io.yak.ops.tools.datasource.utils;

import io.yak.ops.common.utils.FileUtils;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;

public final class SchemaUtils {

    private SchemaUtils() {
        throw new UnsupportedOperationException("Construct SchemaUtils");
    }

    public static String getSoftVersion() throws IOException {
        ClassPathResource resource = new ClassPathResource("sql/soft_version");
        try (InputStream inputStream = resource.getInputStream()) {
            String version = FileUtils.readFile2Str(inputStream).trim();
            if (version.isEmpty()) {
                throw new IllegalStateException("Database schema baseline version must not be empty");
            }
            return version;
        }
    }
}
