package dev.lourencogabriel.inatead.document.controller;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.document.dto.PersonDocumentDtos;
import dev.lourencogabriel.inatead.document.service.PersonDocumentService;
import dev.lourencogabriel.inatead.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PersonDocumentMultipartControllerTest {

    @Mock
    private PersonDocumentService service;
    @Mock
    private DomainAuthorization authorization;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PersonDocumentController(service, authorization)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldDeserializeJsonMetadataAndBinaryFromMultipartRequest() throws Exception {
        MockMultipartFile metadata = metadata();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "identity.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "pdf-content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/person-documents").file(metadata).file(file))
                .andExpect(status().isCreated());

        ArgumentCaptor<PersonDocumentDtos.Request> requestCaptor = ArgumentCaptor.forClass(
                PersonDocumentDtos.Request.class
        );
        verify(service).create(requestCaptor.capture(), any(), isNull());
        assertEquals("person-1", requestCaptor.getValue().personId());
        assertEquals((short) 1, requestCaptor.getValue().documentTypeId());
    }

    @Test
    void shouldReturnBadRequestWhenFilePartIsMissing() throws Exception {
        mockMvc.perform(multipart("/api/person-documents").file(metadata()))
                .andExpect(status().isBadRequest());
    }

    private MockMultipartFile metadata() {
        String json = """
                {
                  "personId": "person-1",
                  "documentTypeId": 1,
                  "documentNumber": "123",
                  "verificationStatus": "PENDING"
                }
                """;
        return new MockMultipartFile(
                "metadata",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8)
        );
    }
}
