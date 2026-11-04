package io.yak.ops.server.master.integration;

import java.io.InputStream;

import org.yaml.snakeyaml.Yaml;

import lombok.SneakyThrows;

public class YamlFactory {

    @SneakyThrows
    public static WorkflowTestCaseContext load(final String yamlRelativePath) {
        final Yaml yaml = new Yaml();
        try (InputStream fis = YamlFactory.class.getResourceAsStream(yamlRelativePath)) {
            if (fis == null) {
                throw new IllegalArgumentException("Cannot find the file: " + yamlRelativePath + " under classpath");
            }
            return yaml.loadAs(fis, WorkflowTestCaseContext.class);
        }
    }

}
