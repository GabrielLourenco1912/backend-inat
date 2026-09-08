package dev.lourencogabriel.inatead.shared.api;

import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
public abstract class AbstractCrudController<Q, R> {

    private final CrudService<Q, R> service;

    protected AbstractCrudController(CrudService<Q, R> service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<R>> create(@Valid @RequestBody Q request) {
        R response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(response, "Resource created", HttpStatus.CREATED));
    }

    @GetMapping("/{id}")
    public ApiResponse<R> findById(@PathVariable String id) {
        return new ApiResponse<>(service.findById(id), "Resource found");
    }

    @GetMapping
    public ApiResponse<PageResponse<R>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return new ApiResponse<>(
                service.findAll(PageRequest.of(page, size)),
                "Resources found"
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<R> update(@PathVariable String id, @Valid @RequestBody Q request) {
        return new ApiResponse<>(service.update(id, request), "Resource updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return new ApiResponse<>(null, "Resource deleted");
    }
}
