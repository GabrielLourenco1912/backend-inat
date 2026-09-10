package dev.lourencogabriel.inatead.document.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemStorageTest {

    @TempDir
    Path root;

    @Test
    void shouldStoreLoadAndDeleteContent() throws Exception {
        FileSystemStorage storage = storageWithLimit(1024);
        byte[] content = "filesystem content".getBytes(StandardCharsets.UTF_8);

        StoredBinary stored = storage.store(new ByteArrayInputStream(content));

        assertEquals(content.length, stored.sizeBytes());
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
                stored.checksumSha256()
        );
        assertFalse(Path.of(stored.storageKey()).isAbsolute());
        assertTrue(root.resolve(stored.storageKey()).normalize().startsWith(root));
        assertArrayEquals(
                content,
                storage.load(stored.storageKey(), content.length).getInputStream().readAllBytes()
        );

        storage.delete(stored.storageKey());

        assertFalse(Files.exists(root.resolve(stored.storageKey())));
    }

    @Test
    void shouldRejectContentLargerThanConfiguredLimitAndCleanTemporaryFile() throws Exception {
        FileSystemStorage storage = storageWithLimit(4);

        assertThrows(
                FileTooLargeException.class,
                () -> storage.store(new ByteArrayInputStream(new byte[5]))
        );

        try (var temporaryFiles = Files.list(root.resolve(".tmp"))) {
            assertEquals(0, temporaryFiles.count());
        }
    }

    @Test
    void shouldRejectKeysOutsideStorageRoot() {
        FileSystemStorage storage = storageWithLimit(1024);

        assertThrows(FileStorageException.class, () -> storage.load("../../secret", 1));
        assertThrows(FileStorageException.class, () -> storage.delete("/tmp/secret"));
        assertThrows(FileStorageException.class, () -> storage.delete("..\\secret"));
    }

    @Test
    void shouldDetectUnexpectedStoredSize() throws Exception {
        FileSystemStorage storage = storageWithLimit(1024);
        StoredBinary stored = storage.store(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertThrows(
                FileStorageException.class,
                () -> storage.load(stored.storageKey(), stored.sizeBytes() + 1)
        );
    }

    @Test
    void shouldReuseChecksumAddressedBinary() throws Exception {
        FileSystemStorage storage = storageWithLimit(1024);
        byte[] content = "same content".getBytes(StandardCharsets.UTF_8);

        StoredBinary first = storage.store(new ByteArrayInputStream(content));
        StoredBinary second = storage.store(new ByteArrayInputStream(content));

        assertTrue(first.newlyCreated());
        assertFalse(second.newlyCreated());
        assertEquals(first.storageKey(), second.storageKey());
        assertEquals(first.checksumSha256(), second.checksumSha256());
        try (var files = Files.walk(root)) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void shouldRejectAConflictingBinaryAtTheChecksumPath() throws Exception {
        FileSystemStorage storage = storageWithLimit(1024);
        byte[] content = "same content".getBytes(StandardCharsets.UTF_8);
        StoredBinary first = storage.store(new ByteArrayInputStream(content));
        Files.write(
                root.resolve(first.storageKey()),
                "bad content!".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(
                FileStorageException.class,
                () -> storage.store(new ByteArrayInputStream(content))
        );
    }

    private FileSystemStorage storageWithLimit(long bytes) {
        FileSystemStorageProperties properties = new FileSystemStorageProperties();
        properties.setRoot(root);
        properties.setMaxSize(DataSize.ofBytes(bytes));
        return new FileSystemStorage(properties);
    }
}
