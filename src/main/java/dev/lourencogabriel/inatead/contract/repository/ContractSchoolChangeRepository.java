package dev.lourencogabriel.inatead.contract.repository;

import dev.lourencogabriel.inatead.contract.model.ContractSchoolChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractSchoolChangeRepository
        extends JpaRepository<ContractSchoolChange, String> {

    List<ContractSchoolChange> findAllByContract_IdOrderByChangedAtAscIdAsc(String contractId);

    void deleteAllByContract_Id(String contractId);
}
