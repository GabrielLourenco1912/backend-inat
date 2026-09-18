package dev.lourencogabriel.inatead.contact.repository;

import dev.lourencogabriel.inatead.contact.model.ContactMessage;
import dev.lourencogabriel.inatead.contact.model.ContactMessageStatus;
import dev.lourencogabriel.inatead.contact.model.ContactMessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, String> {

    @Query("""
            select contactMessage
            from ContactMessage contactMessage
            where (:status is null or contactMessage.status = :status)
              and (:contactType is null or contactMessage.contactType = :contactType)
              and (
                    :searchPattern is null
                    or lower(contactMessage.name) like :searchPattern
                    or lower(contactMessage.email) like :searchPattern
                    or lower(contactMessage.message) like :searchPattern
              )
            """)
    Page<ContactMessage> findAllFiltered(
            @Param("searchPattern") String searchPattern,
            @Param("status") ContactMessageStatus status,
            @Param("contactType") ContactMessageType contactType,
            Pageable pageable
    );
}
