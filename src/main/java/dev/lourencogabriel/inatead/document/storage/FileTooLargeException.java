package dev.lourencogabriel.inatead.document.storage;

public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(long maximumSizeBytes) {
        super("File exceeds the maximum allowed size of " + maximumSizeBytes + " bytes");
    }
}
