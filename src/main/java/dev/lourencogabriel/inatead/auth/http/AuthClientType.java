package dev.lourencogabriel.inatead.auth.http;

public enum AuthClientType {
    WEB(false),
    MOBILE(true);

    private final boolean returnRefreshTokenInBody;

    AuthClientType(boolean returnRefreshTokenInBody) {
        this.returnRefreshTokenInBody = returnRefreshTokenInBody;
    }

    public boolean shouldReturnRefreshTokenInBody() {
        return this.returnRefreshTokenInBody;
    }
}