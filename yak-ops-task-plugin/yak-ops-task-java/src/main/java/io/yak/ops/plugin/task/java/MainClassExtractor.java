package io.yak.ops.plugin.task.java;

import java.io.File;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainClassExtractor {

    private MainClassExtractor() {
    }
    public static String getMainClassName(String jarFilePath) {
        String mainClassName = null;
        try (JarFile jarFile = new JarFile(new File(jarFilePath))) {

            Manifest manifest = jarFile.getManifest();
            mainClassName = manifest.getMainAttributes().getValue("Main-Class");

        } catch (Exception e) {
            throw new RuntimeException("get mainJarName failed:", e);
        }
        return mainClassName;
    }
}
