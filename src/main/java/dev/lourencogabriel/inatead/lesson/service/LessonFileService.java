package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.lesson.dto.LessonFileDtos;
import dev.lourencogabriel.inatead.lesson.model.LessonFile;
import dev.lourencogabriel.inatead.lesson.model.LessonFileId;
import dev.lourencogabriel.inatead.lesson.repository.LessonFileRepository;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.document.service.StoredFileDownload;
import dev.lourencogabriel.inatead.document.service.StoredFileService;
import dev.lourencogabriel.inatead.document.storage.FileUploadPurpose;
import dev.lourencogabriel.inatead.shared.persistence.EntityLookup;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LessonFileService {

    private final LessonFileRepository repository;
    private final LessonRepository lessonRepository;
    private final StoredFileService storedFileService;

    @Transactional
    public LessonFileDtos.Response attach(
            String lessonId,
            LessonFileDtos.Request request,
            MultipartFile upload,
            String actorUserId
    ) {
        var lesson = EntityLookup.require(lessonRepository, lessonId, "Lesson");
        var file = storedFileService.store(
                upload,
                actorUserId,
                FileUploadPurpose.LESSON_MATERIAL
        );
        LessonFileId id = new LessonFileId(lessonId, file.getId());
        LessonFile link = new LessonFile();
        link.setId(id);
        link.setLesson(lesson);
        link.setFile(file);
        link.setSortOrder(request.sortOrder());
        return toResponse(repository.save(link));
    }

    @Transactional(readOnly = true)
    public List<LessonFileDtos.Response> findByLesson(String lessonId) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", lessonId);
        }
        return repository.findAllByLesson_IdOrderBySortOrderAsc(lessonId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoredFileDownload download(String lessonId, String fileId) {
        return storedFileService.download(requireLink(lessonId, fileId).getFile());
    }

    @Transactional
    public void detach(String lessonId, String fileId) {
        LessonFile link = requireLink(lessonId, fileId);
        var file = link.getFile();
        repository.delete(link);
        repository.flush();
        storedFileService.delete(file);
    }

    private LessonFileDtos.Response toResponse(LessonFile link) {
        return new LessonFileDtos.Response(
                link.getLesson().getId(),
                storedFileService.toResponse(link.getFile()),
                link.getSortOrder(),
                link.getCreatedAt()
        );
    }

    private LessonFile requireLink(String lessonId, String fileId) {
        return repository.findById(new LessonFileId(lessonId, fileId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lesson file",
                        lessonId + "/" + fileId
                ));
    }
}
