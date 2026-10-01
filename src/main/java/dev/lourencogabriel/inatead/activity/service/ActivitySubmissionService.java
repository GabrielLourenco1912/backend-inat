package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.dto.ActivitySubmissionDtos;
import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.repository.ActivitySubmissionRepository;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.RecordStatus;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivitySubmissionService {

    private final ActivitySubmissionRepository submissionRepository;
    private final ActivityRepository activityRepository;
    private final LearnerRepository learnerRepository;
    private final LessonParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ActivityNotificationService notificationService;

    @Transactional
    public ActivitySubmissionDtos.Response create(ActivitySubmissionDtos.SaveRequest request) {
        if (submissionRepository.existsByActivity_IdAndLearner_Id(
                request.activityId(),
                request.learnerId()
        )) {
            throw new ResourceConflictException("The learner already has a submission for this activity");
        }
        ActivitySubmission submission = new ActivitySubmission();
        applyLearnerSave(submission, request);
        validateLearnerSave(submission);
        ActivitySubmission saved = submissionRepository.save(submission);
        notifySubmitted(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ActivitySubmissionDtos.Response findById(String id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivitySubmissionDtos.Response> findAll(Pageable pageable) {
        return PageResponse.from(submissionRepository.findAll(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<ActivitySubmissionDtos.Response> findByLearner(String learnerId) {
        if (!learnerRepository.existsById(learnerId)) {
            throw new ResourceNotFoundException("Learner", learnerId);
        }
        return submissionRepository.findAllByLearner_IdOrderByCreatedAtDesc(learnerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivitySubmissionDtos.Response> findByActivity(String activityId) {
        if (!activityRepository.existsById(activityId)) {
            throw new ResourceNotFoundException("Activity", activityId);
        }
        return submissionRepository.findAllByActivity_IdOrderByCreatedAtAsc(activityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ActivitySubmissionDtos.Response update(
            String id,
            ActivitySubmissionDtos.SaveRequest request
    ) {
        ActivitySubmission submission = require(id);
        if (!submission.getActivity().getId().equals(request.activityId())
                || !submission.getLearner().getId().equals(request.learnerId())) {
            throw new BusinessRuleException("activityId and learnerId cannot be changed");
        }
        if (submission.getStatus() != SubmissionStatus.DRAFT
                && submission.getStatus() != SubmissionStatus.RETURNED) {
            throw new BusinessRuleException(
                    "Only draft or returned submissions can be edited"
            );
        }
        applyLearnerSave(submission, request);
        validateLearnerSave(submission);
        ActivitySubmission saved = submissionRepository.save(submission);
        notifySubmitted(saved);
        return toResponse(saved);
    }

    @Transactional
    public ActivitySubmissionDtos.Response grade(
            String id,
            ActivitySubmissionDtos.GradeRequest request,
            String graderUserId
    ) {
        ActivitySubmission submission = require(id);
        if (submission.getStatus() != SubmissionStatus.SUBMITTED
                && submission.getStatus() != SubmissionStatus.LATE) {
            throw new BusinessRuleException("Only submitted activities can be graded or returned");
        }
        if (request.status() != SubmissionStatus.GRADED
                && request.status() != SubmissionStatus.RETURNED) {
            throw new BusinessRuleException("Grade status must be GRADED or RETURNED");
        }
        if (request.status() == SubmissionStatus.GRADED && request.score() == null) {
            throw new BusinessRuleException("A graded submission requires a score");
        }
        if (request.score() != null && submission.getActivity().getMaxScore() == null) {
            throw new BusinessRuleException("This activity is not configured with a maximum score");
        }
        if (request.score() != null
                && request.score().compareTo(submission.getActivity().getMaxScore()) > 0) {
            throw new BusinessRuleException("Score cannot exceed the activity maximum score");
        }

        submission.setScore(request.score());
        submission.setFeedback(normalize(request.feedback()));
        submission.setStatus(request.status());
        submission.setGradedByUser(EntityLookup.require(userRepository, graderUserId, "User"));
        submission.setGradedAt(Instant.now());
        return toResponse(submissionRepository.save(submission));
    }

    @Transactional
    public void delete(String id) {
        ActivitySubmission submission = require(id);
        ensureLessonAcceptsSubmissionChanges(submission.getActivity());
        if (submission.getStatus() != SubmissionStatus.DRAFT
                && submission.getStatus() != SubmissionStatus.RETURNED) {
            throw new BusinessRuleException("Only draft or returned submissions can be deleted");
        }
        submissionRepository.delete(submission);
    }

    private void applyLearnerSave(
            ActivitySubmission submission,
            ActivitySubmissionDtos.SaveRequest request
    ) {
        submission.setActivity(EntityLookup.require(
                activityRepository,
                request.activityId(),
                "Activity"
        ));
        submission.setLearner(EntityLookup.require(
                learnerRepository,
                request.learnerId(),
                "Learner"
        ));
        submission.setTextAnswer(normalize(request.textAnswer()));

        if (request.status() != SubmissionStatus.DRAFT
                && request.status() != SubmissionStatus.SUBMITTED) {
            throw new BusinessRuleException("Learner save status must be DRAFT or SUBMITTED");
        }
        if (request.status() == SubmissionStatus.DRAFT) {
            submission.setStatus(SubmissionStatus.DRAFT);
            submission.setSubmittedAt(null);
        } else {
            Instant now = Instant.now();
            submission.setStatus(
                    now.isAfter(submission.getActivity().getDueAt())
                            ? SubmissionStatus.LATE
                            : SubmissionStatus.SUBMITTED
            );
            submission.setSubmittedAt(now);
        }
        submission.setScore(null);
        submission.setFeedback(null);
        submission.setGradedByUser(null);
        submission.setGradedAt(null);
    }

    private void validateLearnerSave(ActivitySubmission submission) {
        Activity activity = submission.getActivity();
        ensureLessonAcceptsSubmissionChanges(activity);
        if (activity.getStatus() != ActivityStatus.PUBLISHED
                && activity.getStatus() != ActivityStatus.CLOSED) {
            throw new BusinessRuleException(
                    "Submissions require a published or closed activity"
            );
        }
        if (activity.getStatus() == ActivityStatus.CLOSED
                && submission.getStatus() != SubmissionStatus.LATE) {
            throw new BusinessRuleException(
                    "A closed activity only accepts late submissions"
            );
        }
        LessonParticipant participant = participantRepository.findByLesson_IdAndLearner_Id(
                activity.getLesson().getId(),
                submission.getLearner().getId()
        ).orElseThrow(() -> new BusinessRuleException(
                "The learner is not listed in the activity lesson"
        ));
        if (participant.getStatus() == ParticipationStatus.CANCELLED) {
            throw new BusinessRuleException("A cancelled lesson participant cannot submit");
        }
        validateCurrentParticipationContext(participant);
    }

    private void validateCurrentParticipationContext(LessonParticipant participant) {
        if (participant.getLearner().getStatus() != RecordStatus.ACTIVE
                || participant.getLearner().getPerson().getStatus() != RecordStatus.ACTIVE) {
            throw new BusinessRuleException("Submissions require an active learner and person");
        }
        if (participant.getLesson().getCohort().getStatus() != CohortStatus.ACTIVE) {
            throw new BusinessRuleException("Submissions require an active cohort");
        }
        if (participant.getSourceEnrollment() == null) {
            return;
        }
        if (participant.getSourceEnrollment().getStatus() != EnrollmentStatus.ACTIVE) {
            throw new BusinessRuleException("Submissions require an active cohort enrollment");
        }
        if (participant.getSourceEnrollment().getContract().getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessRuleException("Submissions require an active contract");
        }
    }

    private void ensureLessonAcceptsSubmissionChanges(Activity activity) {
        LessonStatus lessonStatus = activity.getLesson().getStatus();
        if (lessonStatus == LessonStatus.SCHEDULED) {
            throw new BusinessRuleException(
                    "Activities from scheduled lessons cannot receive submissions"
            );
        }
        if (lessonStatus == LessonStatus.CANCELLED) {
            throw new BusinessRuleException("Submissions are blocked for a cancelled lesson");
        }
    }

    private ActivitySubmission require(String id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity submission", id));
    }

    private void notifySubmitted(ActivitySubmission submission) {
        if (submission.getStatus() == SubmissionStatus.SUBMITTED
                || submission.getStatus() == SubmissionStatus.LATE) {
            this.notificationService.notifySubmitted(submission);
        }
    }

    private ActivitySubmissionDtos.Response toResponse(ActivitySubmission submission) {
        return new ActivitySubmissionDtos.Response(
                submission.getId(),
                submission.getActivity().getId(),
                submission.getLearner().getId(),
                submission.getLearner().getPerson().getFullName(),
                submission.getLearner().getRegistrationNumber(),
                submission.getGradedByUser() == null
                        ? null
                        : submission.getGradedByUser().getUlid(),
                submission.getTextAnswer(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                submission.getScore(),
                submission.getFeedback(),
                submission.getGradedAt(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
