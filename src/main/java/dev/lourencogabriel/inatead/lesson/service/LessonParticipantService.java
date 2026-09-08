package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.cohort.model.CohortEnrollment;
import dev.lourencogabriel.inatead.cohort.repository.CohortEnrollmentRepository;
import dev.lourencogabriel.inatead.learner.repository.LearnerRepository;
import dev.lourencogabriel.inatead.lesson.dto.LessonParticipantDtos;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.AttendanceRecordRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.model.EnrollmentStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationType;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LessonParticipantService {

    private final LessonParticipantRepository participantRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final LessonRepository lessonRepository;
    private final LearnerRepository learnerRepository;
    private final CohortEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public LessonParticipantDtos.Response create(
            LessonParticipantDtos.Request request,
            String actorUserId
    ) {
        LessonParticipant participant = build(request, actorUserId);
        validate(participant, null);
        return toResponse(participantRepository.save(participant));
    }

    @Transactional(readOnly = true)
    public LessonParticipantDtos.Response findById(String id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonParticipantDtos.Response> findAll(Pageable pageable) {
        return PageResponse.from(participantRepository.findAll(pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<LessonParticipantDtos.Response> findByLesson(String lessonId) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", lessonId);
        }
        return participantRepository.findAllByLesson_Id(lessonId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LessonParticipantDtos.Response update(
            String id,
            LessonParticipantDtos.Request request,
            String actorUserId
    ) {
        LessonParticipant participant = require(id);
        if (!participant.getLesson().getId().equals(request.lessonId())
                || !participant.getLearner().getId().equals(request.learnerId())) {
            throw new BusinessRuleException("lessonId and learnerId cannot be changed");
        }
        apply(participant, request, actorUserId);
        validate(participant, id);
        return toResponse(participantRepository.save(participant));
    }

    @Transactional
    public void delete(String id) {
        LessonParticipant participant = require(id);
        if (attendanceRepository.existsByLessonParticipant_Id(id)) {
            throw new BusinessRuleException("A participant with attendance cannot be deleted");
        }
        participantRepository.delete(participant);
    }

    @Transactional
    public LessonParticipantDtos.RosterGenerationResponse generateRegularRoster(String lessonId) {
        Lesson lesson = EntityLookup.require(lessonRepository, lessonId, "Lesson");
        ensureLessonAcceptsAssignments(lesson);

        LocalDate lessonDate = lessonDate(lesson);
        List<CohortEnrollment> enrollments = enrollmentRepository.findActiveRoster(
                lesson.getCohort().getId(),
                lessonDate
        );
        List<LessonParticipant> created = new ArrayList<>();
        int existing = 0;

        for (CohortEnrollment enrollment : enrollments) {
            String learnerId = enrollment.getContract().getLearner().getId();
            if (participantRepository.existsByLesson_IdAndLearner_Id(lessonId, learnerId)) {
                existing++;
                continue;
            }

            LessonParticipant participant = new LessonParticipant();
            participant.setLesson(lesson);
            participant.setLearner(enrollment.getContract().getLearner());
            participant.setSourceEnrollment(enrollment);
            participant.setParticipationType(ParticipationType.REGULAR);
            participant.setStatus(ParticipationStatus.EXPECTED);
            validate(participant, null);
            created.add(participant);
        }

        participantRepository.saveAll(created);
        List<LessonParticipantDtos.Response> roster =
                participantRepository.findAllByLesson_Id(lessonId).stream()
                        .map(this::toResponse)
                        .toList();

        return new LessonParticipantDtos.RosterGenerationResponse(
                lessonId,
                created.size(),
                existing,
                roster
        );
    }

    @Transactional
    public LessonParticipantDtos.ReassignmentResponse reassign(
            LessonParticipantDtos.ReassignRequest request,
            String actorUserId
    ) {
        LessonParticipant source = require(request.sourceParticipantId());
        ensureLessonAcceptsAssignments(source.getLesson());
        if (source.getStatus() == ParticipationStatus.CANCELLED) {
            throw new BusinessRuleException("The source participation is already cancelled");
        }
        if (attendanceRepository.existsByLessonParticipant_Id(source.getId())) {
            throw new BusinessRuleException(
                    "A participant with recorded attendance cannot be reassigned"
            );
        }
        if (source.getSourceEnrollment() == null) {
            throw new BusinessRuleException("Reassignment requires a source cohort enrollment");
        }

        Lesson targetLesson = EntityLookup.require(
                lessonRepository,
                request.targetLessonId(),
                "Target lesson"
        );
        ensureLessonAcceptsAssignments(targetLesson);
        if (!lessonDate(source.getLesson()).equals(lessonDate(targetLesson))) {
            throw new BusinessRuleException("Day-specific reassignment requires lessons on the same date");
        }
        if (participantRepository.existsByLesson_IdAndLearner_Id(
                targetLesson.getId(),
                source.getLearner().getId()
        )) {
            throw new ResourceConflictException("The learner is already listed in the target lesson");
        }

        User actor = EntityLookup.require(userRepository, actorUserId, "User");
        source.setStatus(ParticipationStatus.CANCELLED);
        source.setAssignedByUser(actor);
        source.setAssignmentReason(
                "Reassigned to lesson " + targetLesson.getId() + ": " + request.reason().trim()
        );

        LessonParticipant target = new LessonParticipant();
        target.setLesson(targetLesson);
        target.setLearner(source.getLearner());
        target.setSourceEnrollment(source.getSourceEnrollment());
        target.setAssignedByUser(actor);
        target.setParticipationType(ParticipationType.TRANSFERRED);
        target.setStatus(ParticipationStatus.EXPECTED);
        target.setAssignmentReason(request.reason().trim());
        validate(target, null);

        participantRepository.save(source);
        participantRepository.save(target);
        return new LessonParticipantDtos.ReassignmentResponse(
                toResponse(source),
                toResponse(target)
        );
    }

    private LessonParticipant build(LessonParticipantDtos.Request request, String actorUserId) {
        LessonParticipant participant = new LessonParticipant();
        apply(participant, request, actorUserId);
        return participant;
    }

    private void apply(
            LessonParticipant participant,
            LessonParticipantDtos.Request request,
            String actorUserId
    ) {
        participant.setLesson(EntityLookup.require(lessonRepository, request.lessonId(), "Lesson"));
        participant.setLearner(EntityLookup.require(learnerRepository, request.learnerId(), "Learner"));
        participant.setSourceEnrollment(
                request.sourceEnrollmentId() == null || request.sourceEnrollmentId().isBlank()
                        ? null
                        : EntityLookup.require(
                                enrollmentRepository,
                                request.sourceEnrollmentId(),
                                "Cohort enrollment"
                        )
        );
        participant.setAssignedByUser(
                actorUserId == null
                        ? null
                        : EntityLookup.require(userRepository, actorUserId, "User")
        );
        participant.setParticipationType(request.participationType());
        participant.setStatus(request.status());
        participant.setAssignmentReason(normalize(request.assignmentReason()));
    }

    private void validate(LessonParticipant participant, String currentId) {
        ensureLessonAcceptsAssignments(participant.getLesson());

        if (participantRepository.existsByLesson_IdAndLearner_Id(
                participant.getLesson().getId(),
                participant.getLearner().getId()
        ) && (currentId == null || !participantRepository.findByLesson_IdAndLearner_Id(
                participant.getLesson().getId(),
                participant.getLearner().getId()
        ).map(existing -> existing.getId().equals(currentId)).orElse(false))) {
            throw new ResourceConflictException("The learner is already listed in this lesson");
        }

        if (participant.getStatus() != ParticipationStatus.CANCELLED
                && participantRepository.existsLearnerScheduleConflict(
                        participant.getLearner().getId(),
                        participant.getLesson().getId(),
                        participant.getLesson().getStartsAt(),
                        participant.getLesson().getEndsAt(),
                        ParticipationStatus.CANCELLED,
                        LessonStatus.CANCELLED
                )) {
            throw new BusinessRuleException("The learner already has an overlapping lesson");
        }

        CohortEnrollment source = participant.getSourceEnrollment();
        if (source != null
                && !source.getContract().getLearner().getId().equals(participant.getLearner().getId())) {
            throw new BusinessRuleException("The source enrollment belongs to another learner");
        }

        ParticipationType type = participant.getParticipationType();
        if ((type == ParticipationType.REGULAR || type == ParticipationType.TRANSFERRED)
                && source == null) {
            throw new BusinessRuleException(type + " participation requires a source enrollment");
        }

        if (source != null) {
            LocalDate date = lessonDate(participant.getLesson());
            boolean enrollmentCoversLesson = source.getStatus() == EnrollmentStatus.ACTIVE
                    && !source.getStartDate().isAfter(date)
                    && (source.getEndDate() == null || !source.getEndDate().isBefore(date));
            if (!enrollmentCoversLesson) {
                throw new BusinessRuleException("The source enrollment is not active on the lesson date");
            }

            boolean sameCohort = source.getCohort().getId()
                    .equals(participant.getLesson().getCohort().getId());
            if (type == ParticipationType.REGULAR && !sameCohort) {
                throw new BusinessRuleException("A regular participant must belong to the host cohort");
            }
            if (type == ParticipationType.TRANSFERRED && sameCohort) {
                throw new BusinessRuleException("A transferred participant must come from another cohort");
            }
        }

        if (type != ParticipationType.REGULAR && participant.getAssignmentReason() == null) {
            throw new BusinessRuleException("Non-regular participation requires a reason");
        }
    }

    private void ensureLessonAcceptsAssignments(Lesson lesson) {
        if (lesson.getStatus() == LessonStatus.CANCELLED
                || lesson.getStatus() == LessonStatus.COMPLETED) {
            throw new BusinessRuleException("Participants cannot be changed in a closed lesson");
        }
    }

    private LocalDate lessonDate(Lesson lesson) {
        return lesson.getStartsAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private LessonParticipant require(String id) {
        return participantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson participant", id));
    }

    private LessonParticipantDtos.Response toResponse(LessonParticipant participant) {
        return new LessonParticipantDtos.Response(
                participant.getId(),
                participant.getLesson().getId(),
                participant.getLearner().getId(),
                participant.getSourceEnrollment() == null
                        ? null
                        : participant.getSourceEnrollment().getId(),
                participant.getAssignedByUser() == null
                        ? null
                        : participant.getAssignedByUser().getUlid(),
                participant.getParticipationType(),
                participant.getStatus(),
                participant.getAssignmentReason(),
                participant.getAssignedAt(),
                participant.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
