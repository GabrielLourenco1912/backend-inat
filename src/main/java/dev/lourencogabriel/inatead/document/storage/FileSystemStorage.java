package dev.lourencogabriel.inatead.document.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Consumer;

@Component
public class FileSystemStorage {

    private static final int BUFFER_SIZE = 16 * 1024;

    private final Path root;
    private final Path temporaryDirectory;
    private final long maximumSizeBytes;

    public FileSystemStorage(FileSystemStorageProperties properties) {
        if (properties.getRoot() == null) {
            throw new IllegalStateException("File storage root must be configured");
        }
        if (properties.getMaxSize() == null || properties.getMaxSize().toBytes() <= 0) {
            throw new IllegalStateException("File storage maximum size must be greater than zero");
        }

        root = properties.getRoot().toAbsolutePath().normalize();
        temporaryDirectory = root.resolve(".tmp");
        maximumSizeBytes = properties.getMaxSize().toBytes();
        initializeDirectories();
    }

    public StoredBinary store(InputStream input) {
        return store(input, ignored -> {
        });
    }

    public StoredBinary store(InputStream input, Consumer<Path> contentValidator) {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(temporaryDirectory, "upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = copy(input, temporaryFile, digest);
            String checksum = HexFormat.of().formatHex(digest.digest());
            contentValidator.accept(temporaryFile);
            String storageKey = newStorageKey(checksum);
            Path destination = resolve(storageKey);
            Files.createDirectories(destination.getParent());
            boolean newlyCreated = installDeduplicated(
                    temporaryFile,
                    destination,
                    size,
                    checksum
            );
            if (newlyCreated) {
                temporaryFile = null;
            }
            return new StoredBinary(
                    storageKey,
                    size,
                    checksum,
                    newlyCreated
            );
        } catch (FileTooLargeException ex) {
            throw ex;
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new FileStorageException("Could not store file content", ex);
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    public Resource load(String storageKey, long expectedSizeBytes) {
        Path path = resolve(storageKey);
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileStorageException("Stored file content was not found");
            }
            if (Files.size(path) != expectedSizeBytes) {
                throw new FileStorageException("Stored file content has an unexpected size");
            }
            return new FileSystemResource(path);
        } catch (IOException ex) {
            throw new FileStorageException("Could not read stored file content", ex);
        }
    }

    public void delete(String storageKey) {
        Path path = resolve(storageKey);
        try {
            Files.deleteIfExists(path);
            deleteEmptyParents(path.getParent());
        } catch (IOException ex) {
            throw new FileStorageException("Could not delete stored file content", ex);
        }
    }

    public long maximumSizeBytes() {
        return maximumSizeBytes;
    }

    private void initializeDirectories() {
        try {
            Files.createDirectories(temporaryDirectory);
            if (!Files.isDirectory(root) || !Files.isWritable(root)) {
                throw new IllegalStateException("File storage root is not a writable directory");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not initialize file storage", ex);
        }
    }

    private long copy(InputStream input, Path destination, MessageDigest digest) throws IOException {
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream source = input;
             OutputStream output = Files.newOutputStream(
                     destination,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING
             )) {
            int bytesRead;
            while ((bytesRead = source.read(buffer)) != -1) {
                total += bytesRead;
                if (total > maximumSizeBytes) {
                    throw new FileTooLargeException(maximumSizeBytes);
                }
                digest.update(buffer, 0, bytesRead);
                output.write(buffer, 0, bytesRead);
            }
        }
        return total;
    }

    private String newStorageKey(String checksum) {
        return "sha256/%s/%s/%s".formatted(
                checksum.substring(0, 2),
                checksum.substring(2, 4),
                checksum
        );
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("\\")) {
            throw new FileStorageException("Invalid file storage key");
        }
        try {
            Path relativePath = Path.of(storageKey);
            if (relativePath.isAbsolute()) {
                throw new FileStorageException("Invalid file storage key");
            }
            Path resolved = root.resolve(relativePath).normalize();
            if (!resolved.startsWith(root) || resolved.equals(root)) {
                throw new FileStorageException("Invalid file storage key");
            }
            return resolved;
        } catch (java.nio.file.InvalidPathException ex) {
            throw new FileStorageException("Invalid file storage key", ex);
        }
    }

    private boolean installDeduplicated(
            Path temporaryFile,
            Path destination,
            long expectedSize,
            String expectedChecksum
    ) throws IOException {
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            validateExistingBinary(destination, expectedSize, expectedChecksum);
            return false;
        }
        try {
            Files.move(temporaryFile, destination);
            return true;
        } catch (FileAlreadyExistsException exception) {
            validateExistingBinary(destination, expectedSize, expectedChecksum);
            return false;
        }
    }

    private void validateExistingBinary(
            Path destination,
            long expectedSize,
            String expectedChecksum
    ) throws IOException {
        if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
                || Files.size(destination) != expectedSize
                || !checksum(destination).equals(expectedChecksum)) {
            throw new FileStorageException(
                    "Deduplicated file content conflicts with the existing checksum path"
            );
        }
    }

    private String checksum(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream input = Files.newInputStream(file)) {
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new FileStorageException("SHA-256 is not available", exception);
        }
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
            // A failed temporary-file cleanup must not hide the original storage error.
        }
    }

    private void deleteEmptyParents(Path directory) throws IOException {
        Path current = directory;
        while (current != null && !current.equals(root) && !current.equals(temporaryDirectory)) {
            try {
                Files.delete(current);
            } catch (java.nio.file.DirectoryNotEmptyException ex) {
                return;
            }
            current = current.getParent();
        }
    }
}
