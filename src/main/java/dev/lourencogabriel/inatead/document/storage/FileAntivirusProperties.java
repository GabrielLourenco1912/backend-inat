package dev.lourencogabriel.inatead.document.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "api.storage.antivirus")
@Getter
@Setter
public class FileAntivirusProperties {

    private boolean enabled;
    private String host = "localhost";
    private int port = 3310;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(30);
}
