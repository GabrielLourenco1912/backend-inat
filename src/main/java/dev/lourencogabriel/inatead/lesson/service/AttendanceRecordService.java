package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.lesson.dto.AttendanceRecordDtos;
import dev.lourencogabriel.inatead.lesson.model.AttendanceRecord;
import dev.lourencogabriel.inatead.lesson.model.LessonParticipant;
import dev.lourencogabriel.inatead.lesson.repository.AttendanceRecordRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonParticipantRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.shared.model.AttendanceStatus;
import dev.lourencogabriel.inatead.shared.model.ContractStatus;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.model.ParticipationStatus;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceRecordService {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AttendanceRecordRepository attendanceRepository;
    private final LessonParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;

    @Transactional
    public AttendanceRecordDtos.Response create(
            AttendanceRecordDtos.Request request,
            String actorUserId
    ) {
        AttendanceRecord record = new AttendanceRecord();
        apply(record, request, actorUserId);
        validate(record, null);
        return toResponse(attendanceRepository.save(record));
    }

    @Transactional(readOnly = true)
    public AttendanceRecordDtos.Response findById(String id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceRecordDtos.Response> findAll(
            LocalDate startDate,
            LocalDate endDate,
            String learnerId,
            String organizationId,
            boolean activeContractsOnly,
            String instructorPersonId,
            Pageable pageable
    ) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessRuleException("endDate cannot precede startDate");
        }
        Instant startsAt = startDate == null
                ? null
                : startDate.atStartOfDay(APPLICATION_ZONE).toInstant();
        Instant endsAtExclusive = endDate == null
                ? null
                : endDate.plusDays(1).atStartOfDay(APPLICATION_ZONE).toInstant();
        return PageResponse.from(attendanceRepository.findAllFiltered(
                startsAt,
                endsAtExclusive,
                normalizeId(learnerId),
                normalizeId(organizationId),
                activeContractsOnly,
                ContractStatus.ACTIVE,
                normalizeId(instructorPersonId),
                pageable
        ).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<AttendanceRecordDtos.Response> findByLesson(String lessonId) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", lessonId);
        }
        return attendanceRepository
                .findAllByLessonParticipant_Lesson_IdOrderByRecordedAtAsc(lessonId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AttendanceRecordDtos.Response update(
            String id,
            AttendanceRecordDtos.Request request,
            String actorUserId
    ) {
        AttendanceRecord record = require(id);
        if (!record.getLessonParticipant().getId().equals(request.lessonParticipantId())) {
            throw new BusinessRuleException("lessonParticipantId cannot be changed");
        }
        apply(record, request, actorUserId);
        validate(record, id);
        return toResponse(attendanceRepository.save(record));
    }

    @Transactional
    public void delete(String id) {
        attendanceRepository.delete(require(id));
    }

    private void apply(
            AttendanceRecord record,
            AttendanceRecordDtos.Request request,
            String actorUserId
    ) {
        record.setLessonParticipant(EntityLookup.require(
                participantRepository,
                request.lessonParticipantId(),
                "Lesson participant"
        ));
        record.setRecordedByUser(EntityLookup.require(userRepository, actorUserId, "User"));
        record.setStatus(request.status());
        record.setCheckInAt(request.checkInAt());
        record.setCheckOutAt(request.checkOutAt());
        record.setNotes(
                request.notes() == null || request.notes().isBlank()
                        ? null
                        : request.notes().trim()
        );
        if (record.getRecordedAt() == null) {
            record.setRecordedAt(Instant.now());
        }
    }

    private void validate(AttendanceRecord record, String currentId) {
        LessonParticipant participant = record.getLessonParticipant();
        if (participant.getStatus() == ParticipationStatus.CANCELLED) {
            throw new BusinessRuleException("Attendance cannot be recorded for a cancelled participant");
        }
        if (participant.getLesson().getStatus() == LessonStatus.CANCELLED) {
            throw new BusinessRuleException("Attendance cannot be recorded for a cancelled lesson");
        }
        if (participant.getLesson().getStatus() == LessonStatus.SCHEDULED) {
            throw new BusinessRuleException("Attendance cannot be recorded for a scheduled lesson");
        }

        boolean duplicate = currentId == null
                ? attendanceRepository.existsByLessonParticipant_Id(participant.getId())
                : attendanceRepository.existsByLessonParticipant_IdAndIdNot(
                        participant.getId(),
                        currentId
                );
        if (duplicate) {
            throw new ResourceConflictException("Attendance has already been recorded for this participant");
        }

        if (record.getCheckInAt() != null
                && record.getCheckOutAt() != null
                && record.getCheckOutAt().isBefore(record.getCheckInAt())) {
            throw new BusinessRuleException("Check-out cannot precede check-in");
        }
        if (record.getCheckOutAt() != null && record.getCheckInAt() == null) {
            throw new BusinessRuleException("Check-out requires a check-in time");
        }
        if ((record.getStatus() == AttendanceStatus.ABSENT
                || record.getStatus() == AttendanceStatus.EXCUSED)
                && (record.getCheckInAt() != null || record.getCheckOutAt() != null)) {
            throw new BusinessRuleException("Absent or excused attendance cannot contain check-in times");
        }
        if ((record.getStatus() == AttendanceStatus.PRESENT
                || record.getStatus() == AttendanceStatus.LATE
                || record.getStatus() == AttendanceStatus.PARTIAL)
                && record.getCheckInAt() == null) {
            throw new BusinessRuleException(
                    "Present, late or partial attendance requires a check-in time"
            );
        }
    }

    private AttendanceRecord require(String id) {
        return attendanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record", id));
    }

    private String normalizeId(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AttendanceRecordDtos.Response toResponse(AttendanceRecord record) {
        LessonParticipant participant = record.getLessonParticipant();
        return new AttendanceRecordDtos.Response(
                record.getId(),
                participant.getId(),
                participant.getLesson().getId(),
                participant.getLearner().getId(),
                record.getRecordedByUser().getUlid(),
                record.getStatus(),
                record.getCheckInAt(),
                record.getCheckOutAt(),
                record.getRecordedAt(),
                record.getNotes(),
                record.getUpdatedAt()
        );
    }
}
