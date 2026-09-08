package dev.lourencogabriel.inatead.activity.model;

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
public class SubmissionFileId implements Serializable {

    @Column(name = "submission_id", length = 26)
    private String submissionId;

    @Column(name = "file_id", length = 26)
    private String fileId;
}
