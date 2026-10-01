package dev.lourencogabriel.inatead.activity.repository;

import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActivitySubmissionRepository extends JpaRepository<ActivitySubmission, String> {

    @Override
    @EntityGraph(attributePaths = {"learner.person", "activity", "gradedByUser"})
    Page<ActivitySubmission> findAll(Pageable pageable);

    boolean existsByActivity_IdAndLearner_Id(String activityId, String learnerId);

    Optional<ActivitySubmission> findByActivity_IdAndLearner_Id(
            String activityId,
            String learnerId
    );

    @EntityGraph(attributePaths = "learner.person")
    List<ActivitySubmission> findAllByLearner_IdOrderByCreatedAtDesc(String learnerId);

    @EntityGraph(attributePaths = "learner.person")
    List<ActivitySubmission> findAllByActivity_IdOrderByCreatedAtAsc(String activityId);

    List<ActivitySubmission> findAllByActivity_Lesson_IdAndLearner_IdAndActivity_StatusIn(
            String lessonId,
            String learnerId,
            Collection<ActivityStatus> activityStatuses
    );
}
