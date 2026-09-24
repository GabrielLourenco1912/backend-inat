package dev.lourencogabriel.inatead.web.dto;

import com.jayway.jsonpath.JsonPath;
import dev.lourencogabriel.inatead.auth.exception.InvalidAuthChallengeException;
import dev.lourencogabriel.inatead.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiResponseTimestampTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TimestampController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void successAndErrorResponsesIncludeUnambiguousUtcTimestamps() throws Exception {
        String success = mvc.perform(get("/timestamp/success"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String error = mvc.perform(get("/timestamp/error"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertUtcTimestamp(success);
        assertUtcTimestamp(error);
    }

    private void assertUtcTimestamp(String json) {
        String timestamp = JsonPath.read(json, "$.timestamp");
        assertTrue(timestamp.endsWith("Z"));
        Instant.parse(timestamp);
    }

    @RestController
    static class TimestampController {

        @GetMapping("/timestamp/success")
        ApiResponse<String> success() {
            return new ApiResponse<>("ok", "Success");
        }

        @GetMapping("/timestamp/error")
        ApiResponse<Void> error() {
            throw new InvalidAuthChallengeException();
        }
    }
}
