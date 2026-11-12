package io.yak.ops.plugin.storage.api.local;

import io.yak.ops.plugin.storage.api.AbstractStorageOperator;
import io.yak.ops.plugin.storage.api.ResourceMetadata;
import io.yak.ops.plugin.storage.api.StorageEntity;
import io.yak.ops.plugin.storage.api.StorageOperator;

import org.apache.commons.io.FileUtils;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalStorageOperator extends AbstractStorageOperator implements Closeable, StorageOperator {

    public LocalStorageOperator(String resourceBaseAbsolutePath) throws IOException {
        super(normalizeBasePath(resourceBaseAbsolutePath));

        final Path path = Paths.get(this.resourceBaseAbsolutePath);
        if (Files.exists(path)) {
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException(
                        "The base path must be a directory: " + this.resourceBaseAbsolutePath);
            }
        } else {
            Files.createDirectories(path);
        }
    }

    /**
     * Resolve the configured storage directory using the current operating system's
     * path rules so every later storage operation uses the same absolute path format.
     */
    private static String normalizeBasePath(String resourceBaseAbsolutePath) {
        if (resourceBaseAbsolutePath == null) {
            throw new NullPointerException("Resource upload path should not be null");
        }
        return Paths.get(resourceBaseAbsolutePath).toAbsolutePath().normalize().toString();
    }

    @SneakyThrows
    @Override
    public void createStorageDir(String directoryAbsolutePath) {
        final Path path = Paths.get(directoryAbsolutePath);
        if (exists(directoryAbsolutePath)) {
            throw new FileAlreadyExistsException("Directory already exists: " + directoryAbsolutePath);
        }
        Files.createDirectories(path);
    }

    @Override
    public boolean exists(String resourceAbsolutePath) {
        return Files.exists(Paths.get(resourceAbsolutePath));
    }

    @SneakyThrows
    @Override
    public void delete(String resourceAbsolutePath, boolean recursive) {
        if (recursive) {
            FileUtils.deleteQuietly(new File(resourceAbsolutePath));
        } else {
            Files.deleteIfExists(Paths.get(resourceAbsolutePath));
        }
    }

    @SneakyThrows
    @Override
    public void copy(String srcAbsolutePath, String dstAbsolutePath, boolean deleteSource, boolean overwrite) {
        if (srcAbsolutePath.equals(dstAbsolutePath)) {
            throw new IllegalArgumentException(
                    "Source path and destination path cannot be the same: " + srcAbsolutePath);
        }

        if (!exists(srcAbsolutePath)) {
            throw new FileNotFoundException("Source path does not exist: " + srcAbsolutePath);
        }

        if (exists(dstAbsolutePath)) {
            if (!overwrite) {
                throw new FileAlreadyExistsException("Destination path already exists: " + dstAbsolutePath);
            }
            delete(dstAbsolutePath, true);
        }

        final File srcFile = new File(srcAbsolutePath);
        final File dstFile = new File(dstAbsolutePath);
        if (FileUtils.isDirectory(srcFile)) {
            FileUtils.copyDirectoryToDirectory(srcFile, dstFile);
        } else {
            FileUtils.copyFile(srcFile, dstFile);
        }
        if (deleteSource) {
            delete(srcAbsolutePath, true);
        }
    }

    @Override
    public void upload(String srcLocalFileAbsolutePath, String dstAbsolutePath, boolean deleteSource,
                       boolean overwrite) {
        copy(srcLocalFileAbsolutePath, dstAbsolutePath, deleteSource, overwrite);
    }

    @Override
    public void download(String srcFileAbsolutePath, String dstAbsolutePath, boolean overwrite) {
        copy(srcFileAbsolutePath, dstAbsolutePath, false, overwrite);
    }

    @SneakyThrows
    @Override
    public List<String> fetchFileContent(String fileAbsolutePath, int skipLineNums, int limit) {
        try (Stream<String> stream = Files.lines(Paths.get(fileAbsolutePath)).skip(skipLineNums).limit(limit)) {
            return stream.collect(Collectors.toList());
        }
    }

    @SneakyThrows
    @Override
    public List<StorageEntity> listStorageEntity(String resourceAbsolutePath) {
        Path path = Paths.get(resourceAbsolutePath);
        if (!Files.exists(path)) {
            return Collections.emptyList();
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.list(path)) {
                return stream.map(this::transformFileStatusToResourceMetadata).collect(Collectors.toList());
            }
        }
        return Lists.newArrayList(transformFileStatusToResourceMetadata(path));
    }

    @SneakyThrows
    @Override
    public List<StorageEntity> listFileStorageEntityRecursively(String resourceAbsolutePath) {
        List<StorageEntity> result = new ArrayList<>();

        LinkedList<Path> foldersToFetch = new LinkedList<>();
        foldersToFetch.addLast(Paths.get(resourceAbsolutePath));

        while (!foldersToFetch.isEmpty()) {
            final Path path1 = foldersToFetch.pollFirst();
            if (!Files.exists(path1)) {
                continue;
            }
            try (Stream<Path> children = Files.list(path1)) {
                children.forEach(child -> {
                    if (child.toFile().isDirectory()) {
                        foldersToFetch.add(child);
                    }
                    result.add(transformFileStatusToResourceMetadata(child));
                });
            }
        }
        return result;
    }

    @Override
    public StorageEntity getStorageEntity(String resourceAbsolutePath) {
        return transformFileStatusToResourceMetadata(Paths.get(resourceAbsolutePath));
    }

    @Override
    public void close() {
        // ignore
    }

    @SneakyThrows
    private StorageEntity transformFileStatusToResourceMetadata(Path path) {
        final BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        final String fileAbsolutePath = path.toAbsolutePath().toString();
        final ResourceMetadata resourceMetaData = getResourceMetaData(fileAbsolutePath);
        return StorageEntity.builder()
                .fileName(path.getFileName().toString())
                .fullName(fileAbsolutePath)
                .pfullName(resourceMetaData.getResourceParentAbsolutePath())
                .type(resourceMetaData.getResourceType())
                .isDirectory(attrs.isDirectory())
                .size(attrs.size())
                .relativePath(resourceMetaData.getResourceRelativePath())
                .createTime(new Date(attrs.creationTime().toMillis()))
                .updateTime(new Date(attrs.lastModifiedTime().toMillis()))
                .build();
    }
}
