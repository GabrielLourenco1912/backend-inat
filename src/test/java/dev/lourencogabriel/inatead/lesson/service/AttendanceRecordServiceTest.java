package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.learner.model.Learner;
import dev.lourencogabriel.inatead.lesson.dto.AttendanceRecordDtos;
import dev.lourencogabriel.inatead.lesson.model.AttendanceRecord;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.AttendanceRecordRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.shared.model.AttendanceStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceRecordServiceTest {

    private static final Instant STARTS_AT = Instant.parse("2026-09-10T13:00:00Z");

    @Mock
    private AttendanceRecordRepository attendanceRepository;
    @Mock
    private LessonParticipantRepository participantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LessonRepository lessonRepository;

    private AttendanceRecordService service;
    private Lesson lesson;

    @BeforeEach
    void setUp() {
        service = new AttendanceRecordService(
                attendanceRepository,
                participantRepository,
                userRepository,
                lessonRepository
        );

        lesson = new Lesson();
        lesson.setId("lesson");
        lesson.setStartsAt(STARTS_AT);
        lesson.setEndsAt(STARTS_AT.plusSeconds(7200));
    }

    @Test
    void shouldRejectManualAttendanceForAScheduledLesson() {
        lesson.setStatus(LessonStatus.SCHEDULED);
        stubReferences();

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> service.create(request(), "actor")
        );

        assertEquals(
                "Attendance cannot be recorded for a scheduled lesson",
                exception.getMessage()
        );
        verify(attendanceRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = LessonStatus.class,
            names = {"IN_PROGRESS", "COMPLETED"}
    )
    void shouldAllowManualAttendanceForCurrentAndCompletedLessons(LessonStatus status) {
        lesson.setStatus(status);
        stubReferences();
        when(attendanceRepository.save(any(AttendanceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceRecordDtos.Response response = service.create(request(), "actor");

        assertEquals(AttendanceStatus.PRESENT, response.status());
        assertEquals("lesson", response.lessonId());
    }

    private void stubReferences() {
        Learner learner = new Learner();
        learner.setId("learner");
        LessonParticipant participant = new LessonParticipant();
        participant.setId("participant");
        participant.setLesson(lesson);
        participant.setLearner(learner);
        participant.setStatus(ParticipationStatus.EXPECTED);

        User actor = new User("Instructor", "instructor@example.com", "hash");
        actor.setUlid("actor");

        when(participantRepository.findById("participant")).thenReturn(Optional.of(participant));
        when(userRepository.findById("actor")).thenReturn(Optional.of(actor));
    }

    private AttendanceRecordDtos.Request request() {
        return new AttendanceRecordDtos.Request(
                "participant",
                AttendanceStatus.PRESENT,
                STARTS_AT,
                null,
                null
        );
    }
}
