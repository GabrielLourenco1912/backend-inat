package dev.lourencogabriel.inatead.cohort.service;

import dev.lourencogabriel.inatead.cohort.dto.CohortDtos;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.shared.api.AbstractCrudService;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CohortService extends AbstractCrudService<Cohort, CohortDtos.Request, CohortDtos.Response> {

    private final CohortRepository cohortRepository;

    public CohortService(CohortRepository cohortRepository) {
        super(cohortRepository, "Cohort");
        this.cohortRepository = cohortRepository;
    }

    @Override
    protected Cohort newEntity() {
        return new Cohort();
    }

    @Override
    protected void apply(Cohort entity, CohortDtos.Request request) {
        entity.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        entity.setName(request.name().trim());
        entity.setDefaultWeekday(request.defaultWeekday());
        entity.setShiftCode(request.shiftCode().trim().toUpperCase(Locale.ROOT));
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(request.status());
    }

    @Override
    protected void validate(Cohort entity, boolean creating) {
        if (entity.getEndDate() != null && entity.getEndDate().isBefore(entity.getStartDate())) {
            throw new BusinessRuleException("Cohort end date cannot precede start date");
        }
        boolean duplicate = creating
                ? cohortRepository.existsByCode(entity.getCode())
                : cohortRepository.existsByCodeAndIdNot(entity.getCode(), entity.getId());
        if (duplicate) {
            throw new ResourceConflictException("A cohort with this code already exists");
        }
    }

    @Override
    protected CohortDtos.Response toResponse(Cohort entity) {
        return new CohortDtos.Response(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDefaultWeekday(),
                entity.getShiftCode(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
