package dev.lourencogabriel.inatead.lesson.service;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.lesson.dto.AttendanceRecordDtos;
import dev.lourencogabriel.inatead.lesson.repository.*;
import dev.lourencogabriel.inatead.search.PortalReadService;
import dev.lourencogabriel.inatead.shared.model.AttendanceStatus;
import dev.lourencogabriel.inatead.web.exception.ResourceConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortalAttendanceService {
    private final AttendanceRecordService attendance;
    private final AttendanceRecordRepository records;
    private final LessonParticipantRepository participants;
    private final LessonRepository lessons;
    private final DomainAuthorization authorization;
    private final PortalReadService reads;
    private final PlatformTransactionManager transactions;
    private final jakarta.persistence.EntityManager entityManager;

    public record Edit(@NotNull @Valid AttendanceRecordDtos.Request values,String recordId,Instant expectedUpdatedAt) {}
    public record Batch(@NotEmpty @Size(max=100) List<@Valid Edit> items) {}
    public record Outcome(String participantId,AttendanceRecordDtos.Response record,String error) {}

    private TransactionTemplate transaction() {
        var template=new TransactionTemplate(transactions);template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);return template;
    }
    private void access(String lessonId,Authentication auth) { if(!authorization.canManageLesson(auth,lessonId)) throw new AccessDeniedException("Lesson management required"); }

    private AttendanceRecordDtos.Response confirmed(AttendanceRecordDtos.Response response) {
        // Audit timestamps change on flush. Return the database precision so a
        // subsequent edit can use the acknowledgement as its concurrency token.
        records.flush();
        entityManager.refresh(records.findById(response.id()).orElseThrow());
        return attendance.findById(response.id());
    }

    public List<Outcome> save(String lessonId,List<Edit> edits,Authentication auth) {
        access(lessonId,auth);String userId=authorization.userId(auth);List<Outcome> result=new ArrayList<>();
        for(var edit:edits) {
            String participantId=edit.values().lessonParticipantId();
            try {
                var saved=transaction().execute(status->{
                    var participant=participants.findByIdForUpdate(participantId).orElseThrow(()->new ResourceConflictException("Participante não encontrado"));
                    if(!participant.getLesson().getId().equals(lessonId)) throw new AccessDeniedException("Participant belongs to another lesson");
                    var current=records.findByLessonParticipant_Id(participantId).orElse(null);
                    if(current==null) {
                        if(edit.recordId()!=null) throw new ResourceConflictException("A presença foi removida. Atualize a página.");
                        return confirmed(attendance.create(edit.values(),userId));
                    }
                    var values=edit.values();
                    boolean same=current.getStatus()==values.status()&&Objects.equals(current.getCheckInAt(),values.checkInAt())&&Objects.equals(current.getCheckOutAt(),values.checkOutAt())&&Objects.equals(Objects.requireNonNullElse(current.getNotes(),"").trim(),Objects.requireNonNullElse(values.notes(),"").trim());
                    if(same) return attendance.findById(current.getId());
                    if(edit.recordId()==null||!edit.recordId().equals(current.getId())||(edit.expectedUpdatedAt()!=null&&!edit.expectedUpdatedAt().equals(current.getUpdatedAt()))) throw new ResourceConflictException("A presença foi alterada por outra operação. Atualize a página antes de salvar.");
                    return confirmed(attendance.update(current.getId(),values,userId));
                });
                result.add(new Outcome(participantId,saved,null));
            } catch(Exception ex) {
                String message=ex instanceof dev.lourencogabriel.inatead.web.exception.BusinessRuleException||ex instanceof ResourceConflictException ? ex.getMessage():"Não foi possível salvar esta presença.";
                result.add(new Outcome(participantId,null,message));
            }
        }
        return result;
    }

    public Map<String,Object> fill(String lessonId,Map<String,String> selection,Authentication auth) {
        access(lessonId,auth);
        var lesson=lessons.findById(lessonId).orElseThrow();
        if(lesson.getStatus()==dev.lourencogabriel.inatead.shared.model.LessonStatus.CANCELLED||lesson.getStatus()==dev.lourencogabriel.inatead.shared.model.LessonStatus.SCHEDULED) throw new ResourceConflictException("Esta aula não permite registrar presenças no estado atual.");
        var filters=PortalReadService.attendanceFilters(lessonId,selection);filters.put("size","100");
        var page=reads.page("lesson-attendance",filters,auth);
        List<Edit> edits=new ArrayList<>();
        for(var row:page.content()) {
            var participant=(Map<?,?>)row.get("participant");
            edits.add(new Edit(new AttendanceRecordDtos.Request(participant.get("id").toString(),AttendanceStatus.PRESENT,lesson.getStartsAt(),lesson.getEndsAt(),null),null,null));
        }
        var outcomes=save(lessonId,edits,auth);
        long saved=outcomes.stream().filter(r->r.error()==null).count(),failed=outcomes.size()-saved;
        long remaining=reads.count("lesson-attendance",filters,auth);
        return Map.of("saved",saved,"failed",failed,"remaining",remaining);
    }
}
