package com.acheao.languageagent.v2.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class MediaStorageService {

    @Value("${app.media-storage-root:./data/media}")
    private String mediaStorageRoot;

    private Path storageRoot;

    @PostConstruct
    void init() {
        try {
            storageRoot = Path.of(mediaStorageRoot).toAbsolutePath().normalize();
            Files.createDirectories(storageRoot);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize media storage", e);
        }
    }

    public Path createLessonDirectory(UUID lessonId) {
        try {
            Path lessonDir = storageRoot.resolve(lessonId.toString()).normalize();
            Files.createDirectories(lessonDir);
            return lessonDir;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create lesson media directory", e);
        }
    }

    public Path resolveRelativePath(String relativePath) {
        return storageRoot.resolve(relativePath).normalize();
    }

    public String relativize(Path absolutePath) {
        return storageRoot.relativize(absolutePath.toAbsolutePath().normalize()).toString().replace("\\", "/");
    }
}
