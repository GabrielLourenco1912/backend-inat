package dev.lourencogabriel.inatead.document.storage;

public class FileScanUnavailableException extends RuntimeException {

    public FileScanUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileScanUnavailableException(String message) {
        super(message);
    }
}
