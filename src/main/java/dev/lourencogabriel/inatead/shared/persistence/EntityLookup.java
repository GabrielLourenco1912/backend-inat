package dev.lourencogabriel.inatead.shared.persistence;

import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

public final class EntityLookup {

    private EntityLookup() {
    }

    public static <T, ID> T require(
            JpaRepository<T, ID> repository,
            ID id,
            String resourceName
    ) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(resourceName, String.valueOf(id)));
    }
}
