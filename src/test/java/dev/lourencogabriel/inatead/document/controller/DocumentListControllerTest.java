package dev.lourencogabriel.inatead.document.controller;

import dev.lourencogabriel.inatead.auth.authorization.DomainAuthorization;
import dev.lourencogabriel.inatead.contract.controller.ContractDocumentController;
import dev.lourencogabriel.inatead.contract.service.ContractDocumentService;
import dev.lourencogabriel.inatead.document.service.PersonDocumentService;
import dev.lourencogabriel.inatead.learner.controller.LearnerController;
import dev.lourencogabriel.inatead.learner.service.LearnerService;
import dev.lourencogabriel.inatead.shared.model.DocumentVerificationStatus;
import dev.lourencogabriel.inatead.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentListControllerTest {
    private final PersonDocumentService people = mock(PersonDocumentService.class);
    private final ContractDocumentService contracts = mock(ContractDocumentService.class);
    private final LearnerService learners = mock(LearnerService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        DomainAuthorization authorization = mock(DomainAuthorization.class);
        mvc = MockMvcBuilders.standaloneSetup(validated(new PersonDocumentController(people, authorization)),
                        validated(new ContractDocumentController(contracts, authorization)), validated(new LearnerController(learners)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Object validated(Object controller) {
        ProxyFactory factory = new ProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new MethodValidationInterceptor());
        return factory.getProxy();
    }

    @Test
    void shouldPassCombinedFiltersAndPaginationToService() throws Exception {
        mvc.perform(get("/api/person-documents").param("personId", "person-1")
                .param("verificationStatus", "EXPIRED").param("page", "2").param("size", "10"))
                .andExpect(status().isOk());
        verify(people).findAll("person-1", DocumentVerificationStatus.EXPIRED,
                PageRequest.of(2, 10, Sort.by("expiresOn").and(Sort.by("id"))));
    }

    @Test
    void shouldKeepUnfilteredListingCompatible() throws Exception {
        mvc.perform(get("/api/person-documents")).andExpect(status().isOk());
        verify(people).findAll(null, null, PageRequest.of(0, 20, Sort.by("createdAt").descending().and(Sort.by("id"))));
    }

    @Test
    void shouldRejectUnknownVerificationStatus() throws Exception {
        mvc.perform(get("/api/person-documents").param("verificationStatus", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(people);
    }

    @Test
    void shouldRejectInvalidPagination() throws Exception {
        mvc.perform(get("/api/person-documents").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/contract-documents").param("size", "101")).andExpect(status().isBadRequest());
        verifyNoInteractions(people, contracts);
    }

    @Test
    void shouldPassContractFilterToService() throws Exception {
        mvc.perform(get("/api/contract-documents").param("contractId", "contract-1"))
                .andExpect(status().isOk());
        verify(contracts).findAll("contract-1", PageRequest.of(0, 20, Sort.by("createdAt").descending().and(Sort.by("id"))));
    }

    @Test
    void shouldResolveLearnerForDocumentOwner() throws Exception {
        mvc.perform(get("/api/learners").param("personId", "person-1").param("size", "1"))
                .andExpect(status().isOk());
        verify(learners).findByPerson("person-1", PageRequest.of(0, 1));
    }
}
