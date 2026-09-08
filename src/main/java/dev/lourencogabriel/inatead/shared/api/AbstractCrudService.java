package dev.lourencogabriel.inatead.shared.api;

import dev.lourencogabriel.inatead.shared.persistence.BaseEntity;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public abstract class AbstractCrudService<E extends BaseEntity, Q, R> implements CrudService<Q, R> {

    private final JpaRepository<E, String> repository;
    private final String resourceName;

    protected AbstractCrudService(JpaRepository<E, String> repository, String resourceName) {
        this.repository = repository;
        this.resourceName = resourceName;
    }

    @Override
    @Transactional
    public R create(Q request) {
        E entity = newEntity();
        apply(entity, request);
        validate(entity, true);
        return toResponse(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public R findById(String id) {
        return toResponse(requireEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<R> findAll(Pageable pageable) {
        return PageResponse.from(repository.findAll(pageable).map(this::toResponse));
    }

    @Override
    @Transactional
    public R update(String id, Q request) {
        E entity = requireEntity(id);
        apply(entity, request);
        validate(entity, false);
        return toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(String id) {
        E entity = requireEntity(id);
        validateDelete(entity);
        repository.delete(entity);
    }

    protected abstract E newEntity();

    protected abstract void apply(E entity, Q request);

    protected abstract R toResponse(E entity);

    protected void validate(E entity, boolean creating) {
    }

    protected void validateDelete(E entity) {
    }

    protected final E requireEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(resourceName, id));
    }

    protected final JpaRepository<E, String> repository() {
        return repository;
    }
}
