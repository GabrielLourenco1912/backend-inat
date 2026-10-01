package dev.lourencogabriel.inatead.lesson.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class LessonFileId implements Serializable {

    @Column(name = "lesson_id", length = 26)
    private String lessonId;

    @Column(name = "file_id", length = 26)
    private String fileId;
}
