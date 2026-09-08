package dev.lourencogabriel.inatead.activity.repository;

import dev.lourencogabriel.inatead.activity.model.SubmissionFile;
import dev.lourencogabriel.inatead.activity.model.SubmissionFileId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, SubmissionFileId> {

    @EntityGraph(attributePaths = "file")
    List<SubmissionFile> findAllBySubmission_Id(String submissionId);
}
