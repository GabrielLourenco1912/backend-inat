package dev.lourencogabriel.inatead.lesson.repository;

import dev.lourencogabriel.inatead.lesson.model.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, String> {

    boolean existsByLessonParticipant_Id(String participantId);

    boolean existsByLessonParticipant_IdAndIdNot(String participantId, String id);

    List<AttendanceRecord> findAllByLessonParticipant_Lesson_IdOrderByRecordedAtAsc(
            String lessonId
    );
}
