package dev.lourencogabriel.inatead.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailLinkResolverTest {

    private final EmailLinkResolver resolver = new EmailLinkResolver(
            "https://portal.example.com"
    );

    @Test
    void shouldResolveRelativePortalLink() {
        assertEquals(
                "https://portal.example.com/sistema/avisos",
                this.resolver.resolve("/sistema/avisos")
        );
    }

    @Test
    void shouldKeepAbsoluteLinkFromConfiguredOrigin() {
        assertEquals(
                "https://portal.example.com/atividades/1?tab=envios",
                this.resolver.resolve(
                        "https://portal.example.com/atividades/1?tab=envios"
                )
        );
    }

    @Test
    void shouldRejectExternalAndUnsafeLinks() {
        assertNull(this.resolver.resolve("https://attacker.example/phishing"));
        assertNull(this.resolver.resolve("javascript:alert(1)"));
        assertNull(this.resolver.resolve("//attacker.example/phishing"));
    }

    @Test
    void shouldRejectInvalidPortalBaseUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EmailLinkResolver("javascript:alert(1)")
        );
    }
}
