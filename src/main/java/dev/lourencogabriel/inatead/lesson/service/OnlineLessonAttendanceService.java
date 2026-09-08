package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.repository.ActivitySubmissionRepository;
import dev.lourencogabriel.inatead.lesson.model.AttendanceRecord;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.AttendanceRecordRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.AttendanceStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.shared.model.SubmissionStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OnlineLessonAttendanceService {

    private static final Set<ActivityStatus> REQUIRED_ACTIVITY_STATUSES = Set.of(
            ActivityStatus.PUBLISHED,
            ActivityStatus.CLOSED
    );
    private static final Set<SubmissionStatus> DELIVERED_SUBMISSION_STATUSES = Set.of(
            SubmissionStatus.SUBMITTED,
            SubmissionStatus.LATE,
            SubmissionStatus.GRADED
    );
    private static final String AUTOMATIC_ATTENDANCE_NOTE =
            "Automatically recorded after all online lesson activities were submitted";

    private final ActivityRepository activityRepository;
    private final ActivitySubmissionRepository submissionRepository;
    private final LessonParticipantRepository participantRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recordWhenAllActivitiesAreDelivered(
            ActivitySubmission submission,
            String actorUserId
    ) {
        if (!DELIVERED_SUBMISSION_STATUSES.contains(submission.getStatus())) {
            return;
        }

        Lesson lesson = submission.getActivity().getLesson();
        if (lesson.getDeliveryMode() != DeliveryMode.ONLINE
                || lesson.getStatus() == LessonStatus.CANCELLED) {
            return;
        }

        String learnerId = submission.getLearner().getId();
        LessonParticipant participant = participantRepository
                .findByLessonAndLearnerForUpdate(lesson.getId(), learnerId)
                .orElseThrow(() -> new BusinessRuleException(
                        "The learner is not listed in the online lesson"
                ));
        if (participant.getStatus() == ParticipationStatus.CANCELLED) {
            return;
        }

        long requiredActivities = activityRepository.countByLesson_IdAndStatusIn(
                lesson.getId(),
                REQUIRED_ACTIVITY_STATUSES
        );
        if (requiredActivities == 0) {
            return;
        }

        long deliveredActivities = submissionRepository
                .countByActivity_Lesson_IdAndLearner_IdAndActivity_StatusInAndStatusIn(
                        lesson.getId(),
                        learnerId,
                        REQUIRED_ACTIVITY_STATUSES,
                        DELIVERED_SUBMISSION_STATUSES
                );
        if (deliveredActivities != requiredActivities
                || attendanceRepository.existsByLessonParticipant_Id(participant.getId())) {
            return;
        }

        AttendanceRecord attendance = new AttendanceRecord();
        attendance.setLessonParticipant(participant);
        attendance.setRecordedByUser(EntityLookup.require(
                userRepository,
                actorUserId,
                "Attendance recording user"
        ));
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setCheckInAt(lesson.getStartsAt());
        attendance.setCheckOutAt(lesson.getEndsAt());
        attendance.setRecordedAt(Instant.now());
        attendance.setNotes(AUTOMATIC_ATTENDANCE_NOTE);
        attendanceRepository.save(attendance);
    }
}
