package dev.lourencogabriel.inatead.document.storage;

import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

@Component
@RequiredArgsConstructor
public class FileContentValidator {

    private static final long MAX_ARCHIVE_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 5_000;
    private static final Set<String> DANGEROUS_ARCHIVE_EXTENSIONS = Set.of(
            "exe", "dll", "com", "bat", "cmd", "ps1", "js", "jar", "msi", "scr"
    );

    private final FileValidationProperties properties;
    private final FileMalwareScanner malwareScanner;

    public String validate(
            Path file,
            String originalName,
            String declaredMimeType,
            FileUploadPurpose purpose
    ) {
        String extension = extensionOf(originalName);
        Set<String> allowedExtensions = properties.allowedExtensions(purpose).stream()
                .map(value -> value.toLowerCase(Locale.ROOT).replaceFirst("^\\.", ""))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessRuleException(
                    "File extension ." + extension + " is not allowed for this upload"
            );
        }

        String detectedMimeType = validateContent(file, extension);
        validateDeclaredMimeType(declaredMimeType, extension);
        malwareScanner.scan(file);
        return detectedMimeType;
    }

    private String extensionOf(String originalName) {
        int separator = originalName.lastIndexOf('.');
        if (separator < 1 || separator == originalName.length() - 1) {
            throw new BusinessRuleException("File must have an allowed extension");
        }
        return originalName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String validateContent(Path file, String extension) {
        try {
            return switch (extension) {
                case "pdf" -> requireSignature(file, extension, this::isPdf, "application/pdf");
                case "png" -> requireSignature(file, extension, this::isPng, "image/png");
                case "jpg", "jpeg" -> requireSignature(file, extension, this::isJpeg, "image/jpeg");
                case "webp" -> requireSignature(file, extension, this::isWebp, "image/webp");
                case "gif" -> requireSignature(file, extension, this::isGif, "image/gif");
                case "txt" -> requireText(file, "text/plain");
                case "csv" -> requireText(file, "text/csv");
                case "rtf" -> requireSignature(file, extension, this::isRtf, "application/rtf");
                case "docx" -> requireOfficeOpenXml(
                        file,
                        "word/document.xml",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                );
                case "xlsx" -> requireOfficeOpenXml(
                        file,
                        "xl/workbook.xml",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                );
                case "pptx" -> requireOfficeOpenXml(
                        file,
                        "ppt/presentation.xml",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                );
                case "odt" -> requireOpenDocument(
                        file,
                        "application/vnd.oasis.opendocument.text"
                );
                case "ods" -> requireOpenDocument(
                        file,
                        "application/vnd.oasis.opendocument.spreadsheet"
                );
                case "odp" -> requireOpenDocument(
                        file,
                        "application/vnd.oasis.opendocument.presentation"
                );
                case "mp3" -> requireSignature(file, extension, this::isMp3, "audio/mpeg");
                case "mp4" -> requireSignature(file, extension, this::isMp4, "video/mp4");
                case "webm" -> requireSignature(file, extension, this::isWebm, "video/webm");
                case "wav" -> requireSignature(file, extension, this::isWav, "audio/wav");
                default -> throw new BusinessRuleException(
                        "File extension ." + extension + " is not supported by content validation"
                );
            };
        } catch (BusinessRuleException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw contentMismatch(extension);
        } catch (IOException exception) {
            throw new FileStorageException("Could not inspect uploaded file content", exception);
        }
    }

    private String requireSignature(
            Path file,
            String extension,
            Predicate<Path> signatureValidator,
            String mimeType
    ) {
        if (!signatureValidator.test(file)) {
            throw contentMismatch(extension);
        }
        return mimeType;
    }

    private String requireText(Path file, String mimeType) throws IOException {
        byte[] content = Files.readAllBytes(file);
        try {
            var decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            while (decoded.hasRemaining()) {
                char character = decoded.get();
                if (character == '\0'
                        || Character.isISOControl(character)
                        && character != '\n'
                        && character != '\r'
                        && character != '\t'
                        && character != '\f') {
                    throw new BusinessRuleException("Text file contains binary content");
                }
            }
            return mimeType;
        } catch (CharacterCodingException exception) {
            throw new BusinessRuleException("Text file must contain valid UTF-8 content");
        }
    }

    private String requireOfficeOpenXml(
            Path file,
            String requiredEntry,
            String mimeType
    ) throws IOException {
        try (ZipFile archive = validatedArchive(file)) {
            if (archive.getEntry("[Content_Types].xml") == null
                    || archive.getEntry("_rels/.rels") == null
                    || archive.getEntry(requiredEntry) == null) {
                throw new BusinessRuleException(
                        "Office document content does not match its file extension"
                );
            }
            return mimeType;
        }
    }

    private String requireOpenDocument(Path file, String expectedMimeType) throws IOException {
        try (ZipFile archive = validatedArchive(file)) {
            ZipEntry mimeEntry = archive.getEntry("mimetype");
            if (mimeEntry == null || mimeEntry.getSize() > 128) {
                throw new BusinessRuleException(
                        "OpenDocument content does not match its file extension"
                );
            }
            try (InputStream input = archive.getInputStream(mimeEntry)) {
                String actualMimeType = new String(input.readNBytes(129), StandardCharsets.US_ASCII);
                if (!expectedMimeType.equals(actualMimeType)) {
                    throw new BusinessRuleException(
                            "OpenDocument content does not match its file extension"
                    );
                }
            }
            return expectedMimeType;
        }
    }

    private ZipFile validatedArchive(Path file) throws IOException {
        ZipFile archive = new ZipFile(file.toFile());
        try {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            int count = 0;
            long totalUncompressedSize = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                count++;
                if (count > MAX_ARCHIVE_ENTRIES) {
                    throw new BusinessRuleException("Archive contains too many entries");
                }
                long size = entry.getSize();
                if (size < 0) {
                    throw new BusinessRuleException("Archive contains an entry with unknown size");
                }
                try {
                    totalUncompressedSize = Math.addExact(totalUncompressedSize, size);
                } catch (ArithmeticException exception) {
                    throw new BusinessRuleException("Archive expands beyond the allowed size");
                }
                if (totalUncompressedSize > MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                    throw new BusinessRuleException("Archive expands beyond the allowed size");
                }
                validateArchiveEntryName(entry.getName());
            }
            return archive;
        } catch (RuntimeException exception) {
            try {
                archive.close();
            } catch (IOException ignored) {
                // The validation error is more relevant than a close failure.
            }
            throw exception;
        }
    }

    private void validateArchiveEntryName(String entryName) {
        String normalized = entryName.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")
                || Arrays.asList(normalized.split("/")).contains("..")) {
            throw new BusinessRuleException("Archive contains an invalid entry path");
        }
        if (normalized.endsWith("/vbaproject.bin")) {
            throw new BusinessRuleException("Macro-enabled office documents are not allowed");
        }
        int separator = normalized.lastIndexOf('.');
        if (separator >= 0
                && DANGEROUS_ARCHIVE_EXTENSIONS.contains(normalized.substring(separator + 1))) {
            throw new BusinessRuleException("Archive contains a disallowed executable entry");
        }
    }

    private void validateDeclaredMimeType(String declaredMimeType, String extension) {
        String baseType = declaredMimeType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if ("application/octet-stream".equals(baseType)) {
            return;
        }
        if (!declaredMimeTypes(extension).contains(baseType)) {
            throw new BusinessRuleException(
                    "Declared file content type does not match the file extension"
            );
        }
    }

    private Set<String> declaredMimeTypes(String extension) {
        return switch (extension) {
            case "pdf" -> Set.of("application/pdf");
            case "png" -> Set.of("image/png");
            case "jpg", "jpeg" -> Set.of("image/jpeg", "image/jpg", "image/pjpeg");
            case "webp" -> Set.of("image/webp");
            case "gif" -> Set.of("image/gif");
            case "txt" -> Set.of("text/plain");
            case "csv" -> Set.of("text/csv", "application/csv", "text/plain");
            case "rtf" -> Set.of("application/rtf", "text/rtf");
            case "docx" -> Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            );
            case "xlsx" -> Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );
            case "pptx" -> Set.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            );
            case "odt" -> Set.of("application/vnd.oasis.opendocument.text");
            case "ods" -> Set.of("application/vnd.oasis.opendocument.spreadsheet");
            case "odp" -> Set.of("application/vnd.oasis.opendocument.presentation");
            case "mp3" -> Set.of("audio/mpeg", "audio/mp3");
            case "mp4" -> Set.of("video/mp4", "application/mp4");
            case "webm" -> Set.of("video/webm", "audio/webm");
            case "wav" -> Set.of("audio/wav", "audio/x-wav", "audio/wave");
            default -> Set.of();
        };
    }

    private boolean isPdf(Path file) {
        try {
            byte[] head = readHead(file, 1024);
            byte[] tail = readTail(file, 2048);
            return contains(head, "%PDF-".getBytes(StandardCharsets.US_ASCII))
                    && contains(tail, "%%EOF".getBytes(StandardCharsets.US_ASCII));
        } catch (IOException exception) {
            throw inspectionFailure(exception);
        }
    }

    private boolean isPng(Path file) {
        return startsWith(file, new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        });
    }

    private boolean isJpeg(Path file) {
        try {
            byte[] head = readHead(file, 3);
            byte[] tail = readTail(file, 2);
            return Arrays.equals(head, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})
                    && Arrays.equals(tail, new byte[]{(byte) 0xff, (byte) 0xd9});
        } catch (IOException exception) {
            throw inspectionFailure(exception);
        }
    }

    private boolean isWebp(Path file) {
        try {
            byte[] head = readHead(file, 12);
            return head.length == 12
                    && matchesAt(head, 0, "RIFF")
                    && matchesAt(head, 8, "WEBP");
        } catch (IOException exception) {
            throw inspectionFailure(exception);
        }
    }

    private boolean isGif(Path file) {
        try {
            byte[] head = readHead(file, 6);
            byte[] tail = readTail(file, 1);
            return (Arrays.equals(head, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || Arrays.equals(head, "GIF89a".getBytes(StandardCharsets.US_ASCII)))
                    && tail.length == 1
                    && tail[0] == 0x3b;
        } catch (IOException exception) {
            throw inspectionFailure(exception);
        }
    }

    private boolean isRtf(Path file) {
        return startsWith(file, "{\\rtf".getBytes(StandardCharsets.US_ASCII));
    }

    private boolean isMp3(Path file) {
        try {
            byte[] head = readHead(file, 3);
            return head.length >= 2
                    && (head.length == 3 && matchesAt(head, 0, "ID3")
                    || (head[0] & 0xff) == 0xff && (head[1] & 0xe0) == 0xe0);
        } catch (IOException exception) {
            throw inspectionFailure(exception);
        }
    }

    private boolean isMp4(Path file) {
        try {
            byte[] head = readHead(file, 12);
            return head.length == 12 && matchesAt(head, 4, "ftyp");
        } catch (IOException exception) {
            throw inspectionFailure(exception);
        }
    }

    private boolean isWebm(Path file) {
        return startsWith(file, new byte[]{0x1a, 0x45, (byte) 0xdf, (byte) 0xa3});
    }

    private boolean isWav(Path file) {
        try {
            byte[] head = readHead(file, 12);
            return head.length == 12
                    && matchesAt(head, 0, "RIFF")
                    && matchesAt(head, 8, "WAVE");
        } catch (IOException exception) {
            throw inspectionFailure(exception);
        }
    }

    private boolean startsWith(Path file, byte[] signature) {
        try {
            return Arrays.equals(readHead(file, signature.length), signature);
        } catch (IOException exception) {
            throw inspectionFailure(exception);
        }
    }

    private byte[] readHead(Path file, int length) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return input.readNBytes(length);
        }
    }

    private byte[] readTail(Path file, int length) throws IOException {
        long size = Files.size(file);
        int actualLength = Math.toIntExact(Math.min(size, length));
        byte[] result = new byte[actualLength];
        try (var channel = Files.newByteChannel(file)) {
            channel.position(size - actualLength);
            ByteBuffer buffer = ByteBuffer.wrap(result);
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // Continue until the requested tail is read.
            }
        }
        return result;
    }

    private boolean matchesAt(byte[] bytes, int offset, String expected) {
        byte[] signature = expected.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < offset + signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (bytes[offset + index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean contains(byte[] content, byte[] expected) {
        outer:
        for (int offset = 0; offset <= content.length - expected.length; offset++) {
            for (int index = 0; index < expected.length; index++) {
                if (content[offset + index] != expected[index]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private BusinessRuleException contentMismatch(String extension) {
        return new BusinessRuleException(
                "File content does not match the ." + extension + " extension"
        );
    }

    private FileStorageException inspectionFailure(IOException exception) {
        return new FileStorageException("Could not inspect uploaded file content", exception);
    }
}
