package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.dto.AttachmentDtos;
import dev.lourencogabriel.inatead.activity.model.ActivitySubmission;
import dev.lourencogabriel.inatead.activity.model.SubmissionFile;
import dev.lourencogabriel.inatead.activity.model.SubmissionFileId;
import dev.lourencogabriel.inatead.activity.repository.ActivitySubmissionRepository;
import dev.lourencogabriel.inatead.activity.repository.SubmissionFileRepository;
import dev.lourencogabriel.inatead.document.service.StoredFileDownload;
import dev.lourencogabriel.inatead.document.service.StoredFileService;
import dev.lourencogabriel.inatead.document.storage.FileUploadPurpose;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.BusinessRuleException;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubmissionFileService {

    private final SubmissionFileRepository repository;
    private final ActivitySubmissionRepository submissionRepository;
    private final StoredFileService storedFileService;

    @Transactional
    public AttachmentDtos.SubmissionFileResponse attach(
            String submissionId,
            MultipartFile upload,
            String actorUserId
    ) {
        var submission = EntityLookup.require(
                submissionRepository,
                submissionId,
                "Activity submission"
        );
        ensureLessonAcceptsSubmissionChanges(submission);
        var file = storedFileService.store(
                upload,
                actorUserId,
                FileUploadPurpose.SUBMISSION_ATTACHMENT
        );
        SubmissionFileId id = new SubmissionFileId(submissionId, file.getId());
        SubmissionFile link = new SubmissionFile();
        link.setId(id);
        link.setSubmission(submission);
        link.setFile(file);
        return toResponse(repository.save(link));
    }

    @Transactional(readOnly = true)
    public List<AttachmentDtos.SubmissionFileResponse> findBySubmission(String submissionId) {
        if (!submissionRepository.existsById(submissionId)) {
            throw new ResourceNotFoundException("Activity submission", submissionId);
        }
        return repository.findAllBySubmission_Id(submissionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoredFileDownload download(String submissionId, String fileId) {
        return storedFileService.download(requireLink(submissionId, fileId).getFile());
    }

    @Transactional
    public void detach(String submissionId, String fileId) {
        SubmissionFile link = requireLink(submissionId, fileId);
        ensureLessonAcceptsSubmissionChanges(link.getSubmission());
        var file = link.getFile();
        repository.delete(link);
        repository.flush();
        storedFileService.delete(file);
    }

    private AttachmentDtos.SubmissionFileResponse toResponse(SubmissionFile link) {
        return new AttachmentDtos.SubmissionFileResponse(
                link.getSubmission().getId(),
                storedFileService.toResponse(link.getFile()),
                link.getCreatedAt()
        );
    }

    private void ensureLessonAcceptsSubmissionChanges(ActivitySubmission submission) {
        LessonStatus lessonStatus = submission.getActivity().getLesson().getStatus();
        if (lessonStatus == LessonStatus.SCHEDULED) {
            throw new BusinessRuleException(
                    "Activities from scheduled lessons cannot receive submissions"
            );
        }
        if (lessonStatus == LessonStatus.CANCELLED) {
            throw new BusinessRuleException("Submission files are blocked for a cancelled lesson");
        }
    }

    private SubmissionFile requireLink(String submissionId, String fileId) {
        return repository.findById(new SubmissionFileId(submissionId, fileId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Submission file",
                        submissionId + "/" + fileId
                ));
    }
}
