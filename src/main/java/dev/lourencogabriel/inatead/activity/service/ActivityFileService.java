package dev.lourencogabriel.inatead.activity.service;

import dev.lourencogabriel.inatead.activity.dto.AttachmentDtos;
import dev.lourencogabriel.inatead.activity.model.ActivityFile;
import dev.lourencogabriel.inatead.activity.model.ActivityFileId;
import dev.lourencogabriel.inatead.activity.repository.ActivityFileRepository;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.document.service.StoredFileDownload;
import dev.lourencogabriel.inatead.document.service.StoredFileService;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityFileService {

    private final ActivityFileRepository repository;
    private final ActivityRepository activityRepository;
    private final StoredFileService storedFileService;

    @Transactional
    public AttachmentDtos.ActivityFileResponse attach(
            String activityId,
            AttachmentDtos.ActivityFileRequest request,
            MultipartFile upload,
            String actorUserId
    ) {
        var activity = EntityLookup.require(activityRepository, activityId, "Activity");
        var file = storedFileService.store(upload, actorUserId);
        ActivityFileId id = new ActivityFileId(activityId, file.getId());
        ActivityFile link = new ActivityFile();
        link.setId(id);
        link.setActivity(activity);
        link.setFile(file);
        link.setSortOrder(request.sortOrder());
        return toResponse(repository.save(link));
    }

    @Transactional(readOnly = true)
    public List<AttachmentDtos.ActivityFileResponse> findByActivity(String activityId) {
        if (!activityRepository.existsById(activityId)) {
            throw new ResourceNotFoundException("Activity", activityId);
        }
        return repository.findAllByActivity_IdOrderBySortOrderAsc(activityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoredFileDownload download(String activityId, String fileId) {
        return storedFileService.download(requireLink(activityId, fileId).getFile());
    }

    @Transactional
    public void detach(String activityId, String fileId) {
        ActivityFile link = requireLink(activityId, fileId);
        var file = link.getFile();
        repository.delete(link);
        repository.flush();
        storedFileService.delete(file);
    }

    private AttachmentDtos.ActivityFileResponse toResponse(ActivityFile link) {
        return new AttachmentDtos.ActivityFileResponse(
                link.getActivity().getId(),
                storedFileService.toResponse(link.getFile()),
                link.getSortOrder(),
                link.getCreatedAt()
        );
    }

    private ActivityFile requireLink(String activityId, String fileId) {
        return repository.findById(new ActivityFileId(activityId, fileId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Activity file",
                        activityId + "/" + fileId
                ));
    }
}
