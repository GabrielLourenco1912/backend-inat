package dev.lourencogabriel.inatead.activity.repository;

import dev.lourencogabriel.inatead.activity.model.ActivityFile;
import dev.lourencogabriel.inatead.activity.model.ActivityFileId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityFileRepository extends JpaRepository<ActivityFile, ActivityFileId> {

    @EntityGraph(attributePaths = "file")
    List<ActivityFile> findAllByActivity_IdOrderBySortOrderAsc(String activityId);
}
