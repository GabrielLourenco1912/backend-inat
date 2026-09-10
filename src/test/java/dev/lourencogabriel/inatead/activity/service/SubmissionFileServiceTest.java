package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.activity.repository.ActivitySubmissionRepository;
import dev.lourencogabriel.inatead.activity.repository.SubmissionFileRepository;
import dev.lourencogabriel.inatead.document.service.StoredFileService;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionFileServiceTest {

    @Mock
    private SubmissionFileRepository repository;
    @Mock
    private ActivitySubmissionRepository submissionRepository;
    @Mock
    private StoredFileService storedFileService;
    @Mock
    private MultipartFile upload;

    private SubmissionFileService service;

    @BeforeEach
    void setUp() {
        this.service = new SubmissionFileService(
                this.repository,
                this.submissionRepository,
                this.storedFileService
        );
    }

    @Test
    void shouldRejectFileAttachmentWhenTheLessonIsScheduled() {
        Lesson lesson = new Lesson();
        lesson.setStatus(LessonStatus.SCHEDULED);
        Activity activity = new Activity();
        activity.setLesson(lesson);
        ActivitySubmission submission = new ActivitySubmission();
        submission.setActivity(activity);
        when(this.submissionRepository.findById("submission-1"))
                .thenReturn(Optional.of(submission));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> this.service.attach("submission-1", this.upload, "actor-user")
        );

        assertEquals(
                "Activities from scheduled lessons cannot receive submissions",
                exception.getMessage()
        );
        verifyNoInteractions(this.repository, this.storedFileService);
    }
}
