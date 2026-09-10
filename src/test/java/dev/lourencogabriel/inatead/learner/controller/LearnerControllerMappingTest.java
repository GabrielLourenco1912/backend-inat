package dev.lourencogabriel.inatead.learner.controller;

import dev.lourencogabriel.inatead.learner.service.LearnerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LearnerControllerMappingTest {

    @Test
    void shouldNotExposeDirectLearnerCreation() throws Exception {
        LearnerService service = mock(LearnerService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LearnerController(service)).build();

        mockMvc.perform(post("/api/learners"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(service);
    }
}
