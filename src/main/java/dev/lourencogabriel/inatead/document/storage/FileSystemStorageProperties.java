package dev.lourencogabriel.inatead.document.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "api.storage.filesystem")
@Getter
@Setter
public class FileSystemStorageProperties {

    private Path root = Path.of("./data/files");
    private DataSize maxSize = DataSize.ofMegabytes(25);
}
