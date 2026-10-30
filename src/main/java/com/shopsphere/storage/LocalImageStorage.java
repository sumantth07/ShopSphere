package com.shopsphere.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Saves files to a local folder (UPLOAD_DIR, default ./uploads) under random UUID names, so a client can never choose a
 * file name or path. NOTE: many hosts wipe local disk on redeploy. For production use a persistent volume or cloud storage.
 */
@Service
public class LocalImageStorage implements ImageStorage {
    private final Path dir;

    public LocalImageStorage(@Value("${shopsphere.upload.dir}") String dir) throws IOException {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.dir);
    }

    public Path directory() { return dir; }

    @Override
    public String store(byte[] data, String extension) throws IOException {
        String name = UUID.randomUUID() + "." + extension;      // extension comes from content sniffing, never from the client
        Files.write(dir.resolve(name), data, StandardOpenOption.CREATE_NEW);
        return "/uploads/" + name;
    }
}
