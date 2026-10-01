package dev.lourencogabriel.inatead.lesson.repository;

import dev.lourencogabriel.inatead.lesson.model.LessonFile;
import dev.lourencogabriel.inatead.lesson.model.LessonFileId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonFileRepository extends JpaRepository<LessonFile, LessonFileId> {

    boolean existsByLesson_Id(String lessonId);

    @EntityGraph(attributePaths = "file")
    List<LessonFile> findAllByLesson_IdOrderBySortOrderAsc(String lessonId);
}
