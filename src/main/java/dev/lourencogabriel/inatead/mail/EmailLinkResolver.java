package dev.lourencogabriel.inatead.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

@Component
public class EmailLinkResolver {

    private final URI portalBaseUri;

    public EmailLinkResolver(@Value("${api.mail.portal-base-url}") String portalBaseUrl) {
        this.portalBaseUri = parseBaseUri(portalBaseUrl);
    }

    public String resolve(String actionUrl) {
        if (!StringUtils.hasText(actionUrl)) {
            return null;
        }

        try {
            URI resolved = this.portalBaseUri.resolve(actionUrl.trim()).normalize();
            if (!isSafeHttpUri(resolved) || !hasSameOrigin(this.portalBaseUri, resolved)) {
                return null;
            }
            return resolved.toASCIIString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static URI parseBaseUri(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("The portal base URL is required");
        }

        URI uri;
        try {
            uri = URI.create(ensureTrailingSlash(value.trim())).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("The portal base URL is invalid", exception);
        }
        if (!isSafeHttpUri(uri) || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("The portal base URL must be an HTTP(S) URL");
        }
        return uri;
    }

    private static boolean isSafeHttpUri(URI uri) {
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return scheme.equals("http") || scheme.equals("https");
    }

    private static boolean hasSameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + '/';
    }
}
