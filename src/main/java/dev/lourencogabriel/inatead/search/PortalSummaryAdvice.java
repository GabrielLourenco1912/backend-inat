package dev.lourencogabriel.inatead.search;

import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

/** Opt-in compatibility mode for command responses; existing API representations stay unchanged. */
@RestControllerAdvice
@RequiredArgsConstructor
public class PortalSummaryAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper mapper;
    @Override public boolean supports(MethodParameter type,Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public Object beforeBodyWrite(Object body,MethodParameter type,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if (!(body instanceof ApiResponse<?> envelope) || request.getMethod().name().equals("GET") || !"summary".equals(UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("view"))) return body;
        Object data=mapper.convertValue(envelope.data(),Object.class);
        trim(data);
        return new ApiResponse<>(envelope.timestamp(),envelope.status(),envelope.message(),data);
    }
    private void trim(Object value) {
        if(value instanceof Map<?,?> map) {
            map.remove("statusHistory");map.remove("schoolHistory");map.remove("participants");
            map.values().forEach(this::trim);
        } else if(value instanceof List<?> list) list.forEach(this::trim);
    }
}
