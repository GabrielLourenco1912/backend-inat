package dev.lourencogabriel.inatead.document.service;

import dev.lourencogabriel.inatead.address.model.Address;
import dev.lourencogabriel.inatead.activity.dto.AttachmentDtos;
import dev.lourencogabriel.inatead.activity.model.Activity;
import dev.lourencogabriel.inatead.activity.model.ActivityFileId;
import dev.lourencogabriel.inatead.activity.repository.ActivityFileRepository;
import dev.lourencogabriel.inatead.activity.repository.ActivityRepository;
import dev.lourencogabriel.inatead.activity.service.ActivityFileService;
import dev.lourencogabriel.inatead.cohort.model.Cohort;
import dev.lourencogabriel.inatead.cohort.repository.CohortRepository;
import dev.lourencogabriel.inatead.document.model.StoredFile;
import dev.lourencogabriel.inatead.document.repository.StoredFileRepository;
import dev.lourencogabriel.inatead.document.storage.FileUploadPurpose;
import dev.lourencogabriel.inatead.lesson.model.Lesson;
import dev.lourencogabriel.inatead.lesson.dto.LessonFileDtos;
import dev.lourencogabriel.inatead.lesson.model.LessonFileId;
import dev.lourencogabriel.inatead.lesson.repository.LessonFileRepository;
import dev.lourencogabriel.inatead.lesson.service.LessonFileService;
import dev.lourencogabriel.inatead.lesson.repository.LessonRepository;
import dev.lourencogabriel.inatead.person.model.Person;
import dev.lourencogabriel.inatead.person.repository.PersonRepository;
import dev.lourencogabriel.inatead.shared.model.ActivityStatus;
import dev.lourencogabriel.inatead.shared.model.CohortStatus;
import dev.lourencogabriel.inatead.shared.model.DeliveryMode;
import dev.lourencogabriel.inatead.shared.model.LessonStatus;
import dev.lourencogabriel.inatead.user.model.User;
import dev.lourencogabriel.inatead.user.repository.UserRepository;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class StoredFileLifecycleIntegrationTest {

    private static final Path STORAGE_ROOT = createStorageRoot();

    @Autowired
    private StoredFileService service;
    @Autowired
    private ActivityFileService activityFileService;
    @Autowired
    private LessonFileService lessonFileService;
    @Autowired
    private LessonFileRepository lessonFileRepository;
    @Autowired
    private StoredFileRepository fileRepository;
    @Autowired
    private ActivityFileRepository activityFileRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private CohortRepository cohortRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("api.storage.filesystem.root", STORAGE_ROOT::toString);
    }

    @AfterAll
    static void cleanStorageRoot() throws IOException {
        FileSystemUtils.deleteRecursively(STORAGE_ROOT);
    }

    @Test
    void shouldPersistAndDeleteMetadataAndBinaryTogether() throws Exception {
        String userId = createUser();
        byte[] content = "document content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "document.txt",
                MediaType.TEXT_PLAIN_VALUE,
                content
        );

        String fileId = transactionTemplate.execute(status -> service.store(
                upload,
                userId,
                FileUploadPurpose.ACTIVITY_MATERIAL
        ).getId());
        StoredFile stored = fileRepository.findById(fileId).orElseThrow();
        Path storedPath = STORAGE_ROOT.resolve(stored.getStorageKey());

        assertTrue(Files.isRegularFile(storedPath));
        assertEquals(content.length, stored.getSizeBytes());
        assertArrayEquals(
                content,
                transactionTemplate.execute(status -> service.download(
                        fileRepository.findById(fileId).orElseThrow()
                ).resource()).getInputStream().readAllBytes()
        );

        transactionTemplate.executeWithoutResult(status -> service.delete(
                fileRepository.findById(fileId).orElseThrow()
        ));

        assertFalse(fileRepository.existsById(fileId));
        assertFalse(Files.exists(storedPath));
    }

    @Test
    void shouldDeleteBinaryWhenDatabaseTransactionRollsBack() throws Exception {
        String userId = createUser();
        long fileCountBefore = fileRepository.count();
        long binaryCountBefore = binaryCount();
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "rollback.txt",
                "text/plain",
                "rollback".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(IntentionalRollback.class, () -> transactionTemplate.executeWithoutResult(status -> {
            service.store(upload, userId, FileUploadPurpose.ACTIVITY_MATERIAL);
            throw new IntentionalRollback();
        }));

        assertEquals(fileCountBefore, fileRepository.count());
        assertEquals(binaryCountBefore, binaryCount());
    }

    @Test
    void shouldManageActivityMaterialAndBinaryAsOneResource() throws Exception {
        String userId = createUser();
        String activityId = createActivity(userId);
        byte[] content = "class material".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "material.txt",
                MediaType.TEXT_PLAIN_VALUE,
                content
        );

        AttachmentDtos.ActivityFileResponse attached = transactionTemplate.execute(status ->
                activityFileService.attach(
                        activityId,
                        new AttachmentDtos.ActivityFileRequest(2),
                        upload,
                        userId
                )
        );
        String fileId = attached.file().id();
        StoredFile stored = fileRepository.findById(fileId).orElseThrow();
        Path storedPath = STORAGE_ROOT.resolve(stored.getStorageKey());

        assertTrue(activityFileRepository.existsById(
                new ActivityFileId(activityId, fileId)
        ));
        assertArrayEquals(
                content,
                transactionTemplate.execute(status -> activityFileService.download(
                        activityId,
                        fileId
                ).resource()).getInputStream().readAllBytes()
        );

        transactionTemplate.executeWithoutResult(status ->
                activityFileService.detach(activityId, fileId)
        );

        assertFalse(fileRepository.existsById(fileId));
        assertFalse(Files.exists(storedPath));
    }

    @Test
    void shouldDeduplicateBinaryAndKeepItUntilLastMetadataReferenceIsDeleted() throws Exception {
        String userId = createUser();
        byte[] content = "deduplicated content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile firstUpload = new MockMultipartFile(
                "file",
                "first.txt",
                MediaType.TEXT_PLAIN_VALUE,
                content
        );
        MockMultipartFile secondUpload = new MockMultipartFile(
                "file",
                "second.txt",
                MediaType.TEXT_PLAIN_VALUE,
                content
        );

        long binaryCountBefore = binaryCount();
        String firstId = transactionTemplate.execute(status -> service.store(
                firstUpload,
                userId,
                FileUploadPurpose.ACTIVITY_MATERIAL
        ).getId());
        String secondId = transactionTemplate.execute(status -> service.store(
                secondUpload,
                userId,
                FileUploadPurpose.ACTIVITY_MATERIAL
        ).getId());

        StoredFile first = fileRepository.findById(firstId).orElseThrow();
        StoredFile second = fileRepository.findById(secondId).orElseThrow();
        Path sharedPath = STORAGE_ROOT.resolve(first.getStorageKey());
        assertEquals(first.getStorageKey(), second.getStorageKey());
        assertEquals(binaryCountBefore + 1, binaryCount());

        transactionTemplate.executeWithoutResult(status -> service.delete(
                fileRepository.findById(firstId).orElseThrow()
        ));
        assertTrue(Files.exists(sharedPath));

        transactionTemplate.executeWithoutResult(status -> service.delete(
                fileRepository.findById(secondId).orElseThrow()
        ));
        assertFalse(Files.exists(sharedPath));
    }

    private String createUser() {
        return transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            Address address = new Address();
            address.setPostalCode("80000000");
            address.setStreet("Storage Test Street");
            address.setStreetNumber("100");
            address.setDistrict("Center");
            address.setCity("Curitiba");
            address.setStateCode("PR");
            address.setCountryCode("BR");

            Person person = new Person();
            person.setAddress(address);
            person.setFullName("Storage Test User");
            person.setTaxId(suffix.substring(0, 11).replaceAll("[a-f]", "1"));
            person.setPhoneNumber("41999999999");
            person.setBirthDate(java.time.LocalDate.of(2000, 1, 1));
            personRepository.save(person);

            User user = new User("Storage Test User", suffix + "@example.com", "hash");
            user.setPerson(person);
            return userRepository.save(user).getUlid();
        });
    }

    private String createLesson(String userId) {
        return transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "");
            User user = userRepository.findById(userId).orElseThrow();

            Cohort cohort = new Cohort();
            cohort.setCode("STORAGE-" + suffix.substring(0, 8));
            cohort.setName("Storage Test Cohort");
            cohort.setDefaultWeekday(1);
            cohort.setShiftCode("MORNING");
            cohort.setStartDate(LocalDate.of(2026, 1, 1));
            cohort.setEndDate(LocalDate.of(2026, 12, 31));
            cohort.setStatus(CohortStatus.ACTIVE);
            cohortRepository.save(cohort);

            Lesson lesson = new Lesson();
            lesson.setCohort(cohort);
            lesson.setInstructorPerson(user.getPerson());
            lesson.setTitle("Storage Test Lesson");
            lesson.setDescription("Descrição da aula de teste");
            lesson.setStartsAt(Instant.parse("2026-09-04T12:00:00Z"));
            lesson.setEndsAt(Instant.parse("2026-09-04T13:00:00Z"));
            lesson.setDeliveryMode(DeliveryMode.ONLINE);
            lesson.setMeetingUrl("https://example.com/lesson");
            lesson.setStatus(LessonStatus.SCHEDULED);
            return lessonRepository.save(lesson).getId();
        });
    }

    private String createActivity(String userId) {
        String lessonId = createLesson(userId);
        return transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            Lesson lesson = lessonRepository.findById(lessonId).orElseThrow();
            Activity activity = new Activity();
            activity.setLesson(lesson);
            activity.setCreatedByUser(user);
            activity.setTitle("Storage Test Activity");
            activity.setDescription("Activity used by the filesystem integration test");
            activity.setAvailableAt(Instant.parse("2026-09-04T12:00:00Z"));
            activity.setDueAt(Instant.parse("2026-09-05T12:00:00Z"));
            activity.setStatus(ActivityStatus.PUBLISHED);
            return activityRepository.save(activity).getId();
        });
    }

    @Test
    void shouldOrderLessonMaterialsAndScopeTheirDownloadsAndDeletion() throws Exception {
        String userId = createUser();
        String lessonId = createLesson(userId);
        String otherLessonId = createLesson(userId);
        byte[] content = "lesson material".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile upload = new MockMultipartFile("file", "material.txt", MediaType.TEXT_PLAIN_VALUE, content);

        var second = lessonFileService.attach(lessonId, new LessonFileDtos.Request(2), upload, userId);
        var first = lessonFileService.attach(lessonId, new LessonFileDtos.Request(0), upload, userId);
        String fileId = second.file().id();
        Path storedPath = STORAGE_ROOT.resolve(fileRepository.findById(fileId).orElseThrow().getStorageKey());

        assertTrue(lessonFileRepository.existsById(new LessonFileId(lessonId, fileId)));
        assertEquals(java.util.List.of(first.file().id(), fileId), lessonFileService.findByLesson(lessonId)
                .stream().map(link -> link.file().id()).toList());
        assertTrue(lessonFileService.findByLesson(otherLessonId).isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> lessonFileService.download(otherLessonId, fileId));
        assertThrows(ResourceNotFoundException.class, () -> lessonFileService.detach(otherLessonId, fileId));
        try (var stream = lessonFileService.download(lessonId, fileId).resource().getInputStream()) {
            assertArrayEquals(content, stream.readAllBytes());
        }

        lessonFileService.detach(lessonId, fileId);
        assertFalse(fileRepository.existsById(fileId));
        assertFalse(lessonFileRepository.existsById(new LessonFileId(lessonId, fileId)));
        assertTrue(Files.exists(storedPath));
        lessonFileService.detach(lessonId, first.file().id());
        assertFalse(Files.exists(storedPath));
    }

    @Test
    void shouldRollBackLessonMaterialAndBinaryTogether() throws Exception {
        String userId = createUser();
        String lessonId = createLesson(userId);
        long fileCountBefore = fileRepository.count();
        long binaryCountBefore = binaryCount();
        transactionTemplate.executeWithoutResult(status -> {
            lessonFileService.attach(lessonId, new LessonFileDtos.Request(0),
                    new MockMultipartFile("file", "rollback.txt", MediaType.TEXT_PLAIN_VALUE,
                            "lesson upload rollback".getBytes(StandardCharsets.UTF_8)), userId);
            status.setRollbackOnly();
        });
        assertTrue(lessonFileService.findByLesson(lessonId).isEmpty());
        assertEquals(fileCountBefore, fileRepository.count());
        assertEquals(binaryCountBefore, binaryCount());
    }

    private long binaryCount() throws IOException {
        try (var paths = Files.walk(STORAGE_ROOT)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("inat-ead-file-storage-");
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static final class IntentionalRollback extends RuntimeException {
    }
}
