package dev.lourencogabriel.inatead.mail;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateRendererTest {

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setClassLoaderForTemplateLoading(
                EmailTemplateRendererTest.class.getClassLoader(),
                "templates/"
        );
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        this.renderer = new EmailTemplateRenderer(configuration);
    }

    @Test
    void shouldRenderBrandedAuthenticationEmailAndEscapeDynamicContent() {
        String html = this.renderer.render(
                "auth-challenge",
                Map.of(
                        "subject", "Código de acesso",
                        "preheader", "Use seu código de acesso.",
                        "heading", "Confirme seu acesso",
                        "recipientName", "Gabriel <script>alert('x')</script>",
                        "action", "concluir seu login",
                        "code", "123456",
                        "expirationMinutes", 10
                )
        );

        assertTrue(html.contains("INAT"));
        assertTrue(html.contains("123456"));
        assertTrue(html.contains("Gabriel &lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
        assertFalse(html.contains("<script>alert('x')</script>"));
    }

    @Test
    void shouldRenderNotificationWithEscapedMessageAndActionLink() {
        Map<String, Object> model = new HashMap<>();
        model.put("subject", "Nova atividade");
        model.put("preheader", "Confira a nova atividade.");
        model.put("recipientName", "Aprendiz");
        model.put("message", "Leia <img src=x onerror=alert(1)>\nSegunda linha");
        model.put("actionUrl", "https://portal.example.com/atividades/1?a=1&b=2");

        String html = this.renderer.render("notification", model);

        assertTrue(html.contains("Acessar o Portal INAT"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertTrue(html.contains("a=1&amp;b=2"));
        assertFalse(html.contains("<img src=x onerror=alert(1)>"));
    }

    @Test
    void shouldRenderNotificationWithoutActionButtonWhenLinkIsAbsent() {
        Map<String, Object> model = new HashMap<>();
        model.put("subject", "Aviso geral");
        model.put("preheader", "Mensagem informativa.");
        model.put("recipientName", "Aprendiz");
        model.put("message", "Nenhuma ação é necessária.");
        model.put("actionUrl", null);

        String html = this.renderer.render("notification", model);

        assertTrue(html.contains("Nenhuma ação é necessária."));
        assertFalse(html.contains("Acessar o Portal INAT"));
    }
}
