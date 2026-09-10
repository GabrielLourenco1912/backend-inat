package dev.lourencogabriel.inatead.document.model;

public enum DocumentTypeScope {
    PERSON,
    CONTRACT,
    BOTH;

    public boolean supportsPersonDocument() {
        return this == PERSON || this == BOTH;
    }

    public boolean supportsContractDocument() {
        return this == CONTRACT || this == BOTH;
    }
}
