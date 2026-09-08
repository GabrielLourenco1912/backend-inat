package dev.lourencogabriel.inatead.document.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class FileEndpointMappingIntegrationTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void shouldExposeFilesOnlyThroughTheirOwningResources() {
        Set<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .collect(Collectors.toSet());

        assertFalse(patterns.stream().anyMatch(pattern ->
                pattern.equals("/api/files") || pattern.startsWith("/api/files/")
        ));
        assertTrue(patterns.contains("/api/person-documents/{id}/content"));
        assertTrue(patterns.contains("/api/contract-documents/{id}/content"));
        assertTrue(patterns.contains("/api/activities/{activityId}/files/{fileId}/content"));
        assertTrue(patterns.contains(
                "/api/activity-submissions/{submissionId}/files/{fileId}/content"
        ));
    }

    @Test
    void shouldRequireMultipartForEveryUploadEndpoint() {
        assertMultipartPost("/api/person-documents");
        assertMultipartPost("/api/contract-documents");
        assertMultipartPost("/api/activities/{activityId}/files");
        assertMultipartPost("/api/activity-submissions/{submissionId}/files");
    }

    private void assertMultipartPost(String pattern) {
        boolean found = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getKey().getPatternValues().contains(pattern))
                .filter(entry -> isPost(entry.getKey()))
                .map(Map.Entry::getKey)
                .anyMatch(mapping -> mapping.getConsumesCondition()
                        .getConsumableMediaTypes()
                        .contains(MediaType.MULTIPART_FORM_DATA));

        assertTrue(found, () -> "Expected a multipart POST mapping for " + pattern);
    }

    private boolean isPost(RequestMappingInfo mapping) {
        return mapping.getMethodsCondition().getMethods().contains(RequestMethod.POST);
    }
}
