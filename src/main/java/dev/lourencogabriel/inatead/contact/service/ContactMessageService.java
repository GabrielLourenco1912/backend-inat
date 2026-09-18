package dev.lourencogabriel.inatead.contact.service;

import dev.lourencogabriel.inatead.contact.dto.ContactMessageDtos;
import dev.lourencogabriel.inatead.contact.model.ContactMessage;
import dev.lourencogabriel.inatead.contact.model.ContactMessageStatus;
import dev.lourencogabriel.inatead.contact.model.ContactMessageType;
import dev.lourencogabriel.inatead.contact.repository.ContactMessageRepository;
import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ContactMessageService {

    private final ContactMessageRepository repository;

    @Transactional
    public ContactMessageDtos.Response create(ContactMessageDtos.CreateRequest request) {
        ContactMessage contactMessage = new ContactMessage();
        contactMessage.setName(request.name().trim());
        contactMessage.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        contactMessage.setPhone(normalize(request.phone()));
        contactMessage.setContactType(request.contactType());
        contactMessage.setMessage(request.message().trim());
        return toResponse(repository.save(contactMessage));
    }

    @Transactional(readOnly = true)
    public ContactMessageDtos.Response findById(String id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ContactMessageDtos.Response> findAll(
            String search,
            ContactMessageStatus status,
            ContactMessageType contactType,
            Pageable pageable
    ) {
        String searchPattern = normalize(search);
        if (searchPattern != null) {
            searchPattern = "%" + searchPattern.toLowerCase(Locale.ROOT) + "%";
        }
        return PageResponse.from(repository
                .findAllFiltered(searchPattern, status, contactType, pageable)
                .map(this::toResponse));
    }

    @Transactional
    public ContactMessageDtos.Response updateStatus(
            String id,
            ContactMessageDtos.StatusRequest request
    ) {
        ContactMessage contactMessage = require(id);
        contactMessage.setStatus(request.status());
        return toResponse(repository.save(contactMessage));
    }

    @Transactional
    public void delete(String id) {
        ContactMessage contactMessage = require(id);
        repository.delete(contactMessage);
    }

    private ContactMessage require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact message", id));
    }

    private ContactMessageDtos.Response toResponse(ContactMessage contactMessage) {
        return new ContactMessageDtos.Response(
                contactMessage.getId(),
                contactMessage.getName(),
                contactMessage.getEmail(),
                contactMessage.getPhone(),
                contactMessage.getContactType(),
                contactMessage.getMessage(),
                contactMessage.getStatus(),
                contactMessage.getCreatedAt(),
                contactMessage.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
