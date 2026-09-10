package dev.lourencogabriel.inatead.document.storage;

import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileContentValidatorTest {

    @TempDir
    Path directory;

    private FileContentValidator validator;

    @BeforeEach
    void setUp() {
        FileAntivirusProperties antivirusProperties = new FileAntivirusProperties();
        antivirusProperties.setEnabled(false);
        validator = new FileContentValidator(
                new FileValidationProperties(),
                new FileMalwareScanner(antivirusProperties)
        );
    }

    @Test
    void shouldAcceptContentThatMatchesExtensionAndMimeType() throws IOException {
        Path file = write("valid.pdf", "%PDF-1.7\nbody\n%%EOF");

        String detected = validator.validate(
                file,
                "identity.pdf",
                "application/pdf",
                FileUploadPurpose.PERSON_DOCUMENT
        );

        assertEquals("application/pdf", detected);
    }

    @Test
    void shouldRejectDisallowedExtensionForEveryUploadPurpose() throws IOException {
        Path file = write("malware.exe", "MZ executable");

        for (FileUploadPurpose purpose : FileUploadPurpose.values()) {
            assertThrows(BusinessRuleException.class, () -> validator.validate(
                    file,
                    "malware.exe",
                    "application/octet-stream",
                    purpose
            ));
        }
    }

    @Test
    void shouldRejectSpoofedPdfContent() throws IOException {
        Path file = write("spoofed.pdf", "this is not a PDF");

        assertThrows(BusinessRuleException.class, () -> validator.validate(
                file,
                "spoofed.pdf",
                "application/pdf",
                FileUploadPurpose.PERSON_DOCUMENT
        ));
    }

    @Test
    void shouldRejectDeclaredMimeTypeThatDoesNotMatchExtension() throws IOException {
        Path file = write("valid.pdf", "%PDF-1.7\nbody\n%%EOF");

        assertThrows(BusinessRuleException.class, () -> validator.validate(
                file,
                "valid.pdf",
                "image/png",
                FileUploadPurpose.PERSON_DOCUMENT
        ));
    }

    @Test
    void shouldAllowTextForActivityButNotForIdentityDocument() throws IOException {
        Path file = write("answer.txt", "valid UTF-8 answer");

        assertEquals("text/plain", validator.validate(
                file,
                "answer.txt",
                "text/plain",
                FileUploadPurpose.SUBMISSION_ATTACHMENT
        ));
        assertThrows(BusinessRuleException.class, () -> validator.validate(
                file,
                "answer.txt",
                "text/plain",
                FileUploadPurpose.PERSON_DOCUMENT
        ));
    }

    @Test
    void shouldValidateOfficeContainerAndRejectMacros() throws IOException {
        Path valid = directory.resolve("valid.docx");
        writeZip(valid, false);
        assertEquals(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                validator.validate(
                        valid,
                        "lesson.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        FileUploadPurpose.ACTIVITY_MATERIAL
                )
        );

        Path macro = directory.resolve("macro.docx");
        writeZip(macro, true);
        assertThrows(BusinessRuleException.class, () -> validator.validate(
                macro,
                "macro.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                FileUploadPurpose.ACTIVITY_MATERIAL
        ));
    }

    private Path write(String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private void writeZip(Path file, boolean macro) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            addEntry(output, "[Content_Types].xml", "<Types />");
            addEntry(output, "_rels/.rels", "<Relationships />");
            addEntry(output, "word/document.xml", "<document />");
            if (macro) {
                addEntry(output, "word/vbaProject.bin", "macro");
            }
        }
    }

    private void addEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
