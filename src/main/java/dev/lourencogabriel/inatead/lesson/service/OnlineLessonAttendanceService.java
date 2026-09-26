package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.repository.ActivitySubmissionRepository;
import dev.lourencogabriel.inatead.lesson.model.AttendanceRecord;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.AttendanceRecordRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.project.OnlineAttendanceClosingPolicy;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.AttendanceStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Set;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OnlineLessonAttendanceService {

    static final Set<ActivityStatus> REQUIRED_ACTIVITY_STATUSES = Set.of(
            ActivityStatus.PUBLISHED,
            ActivityStatus.CLOSED
    );
    static final BigDecimal MINIMUM_PASSING_SCORE = new BigDecimal("6.00");
    private static final String AUTOMATIC_ATTENDANCE_NOTE =
            "Automatically recorded after all online lesson activities were graded above 6";
    static final String AUTOMATIC_ABSENCE_NOTE =
            "Automatically recorded when the online lesson ended without qualifying attendance";
    static final Set<String> AUTOMATIC_ABSENCE_NOTES = Set.of(
            AUTOMATIC_ABSENCE_NOTE,
            "Automatically recorded when the online lesson ended without all activities submitted"
    );

    private final ActivityRepository activityRepository;
    private final ActivitySubmissionRepository submissionRepository;
    private final LessonParticipantRepository participantRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final OnlineAttendanceClosingPolicy attendanceClosingPolicy;

    @Transactional
    public boolean recordQualifiedAttendance(String participantId, Instant recordedAt) {
        LessonParticipant participant = participantRepository
                .findByIdForOnlinePresence(participantId)
                .orElseThrow(() -> new BusinessRuleException("Online lesson participant not found"));
        Lesson lesson = participant.getLesson();
        if (participant.getStatus() != ParticipationStatus.EXPECTED
                || lesson.getDeliveryMode() != DeliveryMode.ONLINE
                || lesson.getStatus() == LessonStatus.SCHEDULED
                || lesson.getStatus() == LessonStatus.CANCELLED) {
            return false;
        }
        AttendanceRecord existing = attendanceRepository
                .findByLessonParticipant_Id(participantId).orElse(null);
        if (existing != null && (existing.getStatus() != AttendanceStatus.ABSENT
                || !AUTOMATIC_ABSENCE_NOTES.contains(existing.getNotes()))) {
            return false;
        }

        long requiredActivities = activityRepository.countByLesson_IdAndStatusIn(
                lesson.getId(),
                REQUIRED_ACTIVITY_STATUSES
        );
        if (requiredActivities == 0) {
            return false;
        }

        String learnerId = participant.getLearner().getId();
        Instant cutoff = attendanceClosingPolicy.submissionCutoffFor(participant).orElse(null);
        List<ActivitySubmission> submissions = submissionRepository
                .findAllByActivity_Lesson_IdAndLearner_IdAndActivity_StatusIn(
                        lesson.getId(), learnerId, REQUIRED_ACTIVITY_STATUSES
                );
        if (submissions.size() != requiredActivities
                || submissions.stream().anyMatch(submission -> !qualifies(submission, cutoff))) {
            return false;
        }

        User instructor = userRepository.findByPerson_Id(lesson.getInstructorPerson().getId())
                .orElseThrow(() -> new BusinessRuleException(
                        "The online lesson instructor has no user account"
                ));
        AttendanceRecord attendance = existing == null ? new AttendanceRecord() : existing;
        attendance.setLessonParticipant(participant);
        attendance.setRecordedByUser(instructor);
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setCheckInAt(lesson.getStartsAt());
        attendance.setCheckOutAt(lesson.getEndsAt());
        attendance.setRecordedAt(recordedAt);
        attendance.setNotes(AUTOMATIC_ATTENDANCE_NOTE);
        attendanceRepository.save(attendance);
        return true;
    }

    private boolean qualifies(ActivitySubmission submission, Instant cutoff) {
        return submission.getStatus() == SubmissionStatus.GRADED
                && submission.getGradedAt() != null
                && submission.getScore() != null
                && submission.getScore().compareTo(MINIMUM_PASSING_SCORE) > 0
                && submission.getSubmittedAt() != null
                && (cutoff == null || submission.getSubmittedAt().isBefore(cutoff));
    }

    @Transactional
    public int recordMissingExpectedAttendance(String lessonId, Instant recordedAt) {
        Lesson lesson = lessonRepository.findByIdForOnlineAttendance(lessonId)
                .orElseThrow(() -> new BusinessRuleException("Online lesson not found"));
        if (lesson.getDeliveryMode() != DeliveryMode.ONLINE
                || lesson.getStatus() == LessonStatus.CANCELLED
                || lesson.getEndsAt().isAfter(recordedAt)) {
            return 0;
        }

        User instructor = userRepository.findByPerson_Id(lesson.getInstructorPerson().getId())
                .orElseThrow(() -> new BusinessRuleException(
                        "The online lesson instructor has no user account"
                ));
        List<AttendanceRecord> missing = participantRepository.findAllForOnlineAttendance(
                        lessonId,
                        ParticipationStatus.EXPECTED
                )
                .stream()
                .filter(participant -> attendanceRepository
                        .findByLessonParticipant_Id(participant.getId())
                        .isEmpty())
                .map(participant -> absentRecord(participant, instructor, recordedAt))
                .toList();
        attendanceRepository.saveAll(missing);
        return missing.size();
    }

    private AttendanceRecord absentRecord(
            LessonParticipant participant,
            User instructor,
            Instant recordedAt
    ) {
        AttendanceRecord attendance = new AttendanceRecord();
        attendance.setLessonParticipant(participant);
        attendance.setRecordedByUser(instructor);
        attendance.setStatus(AttendanceStatus.ABSENT);
        attendance.setRecordedAt(recordedAt);
        attendance.setNotes(AUTOMATIC_ABSENCE_NOTE);
        return attendance;
    }
}
