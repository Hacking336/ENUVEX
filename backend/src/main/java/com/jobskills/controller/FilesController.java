package com.jobskills.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/uploads")
public class FilesController {

    private static final Path UPLOAD_ROOT = Paths.get("uploads").toAbsolutePath().normalize();

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        Path target = UPLOAD_ROOT.resolve(filename).normalize();

        // Refuse anything that resolves outside the uploads folder.
        if (!target.startsWith(UPLOAD_ROOT)) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                .contentType(contentTypeOf(target))
                .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private MediaType contentTypeOf(Path path) {
        try {
            String detected = Files.probeContentType(path);
            if (detected != null) {
                return MediaType.parseMediaType(detected);
            }
        } catch (IOException | RuntimeException e) {
            // Fall through to the generic type below.
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
