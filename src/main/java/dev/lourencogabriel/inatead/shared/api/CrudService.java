package dev.lourencogabriel.inatead.shared.api;

import org.springframework.data.domain.Pageable;

public interface CrudService<Q, R> {

    R create(Q request);

    R findById(String id);

    PageResponse<R> findAll(Pageable pageable);

    R update(String id, Q request);

    void delete(String id);
}
