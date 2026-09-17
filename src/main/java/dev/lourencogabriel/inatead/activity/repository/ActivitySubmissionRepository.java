package dev.lourencogabriel.inatead.activity.repository;

import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ActivitySubmissionRepository extends JpaRepository<ActivitySubmission, String> {

    boolean existsByActivity_IdAndLearner_Id(String activityId, String learnerId);

    Optional<ActivitySubmission> findByActivity_IdAndLearner_Id(
            String activityId,
            String learnerId
    );

    List<ActivitySubmission> findAllByLearner_IdOrderByCreatedAtDesc(String learnerId);

    List<ActivitySubmission> findAllByActivity_IdOrderByCreatedAtAsc(String activityId);

    long countByActivity_Lesson_IdAndLearner_IdAndActivity_StatusInAndStatusIn(
            String lessonId,
            String learnerId,
            Collection<ActivityStatus> activityStatuses,
            Collection<SubmissionStatus> submissionStatuses
    );

    long countByActivity_Lesson_IdAndLearner_IdAndActivity_StatusInAndStatusInAndSubmittedAtBefore(
            String lessonId,
            String learnerId,
            Collection<ActivityStatus> activityStatuses,
            Collection<SubmissionStatus> submissionStatuses,
            Instant submittedBefore
    );
}
