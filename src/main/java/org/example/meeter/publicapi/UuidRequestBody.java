package org.example.meeter.publicapi;

import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.databind.json.JsonMapper;

/** Translate UUID references before existing typed request validation and domain authorization. */
@ControllerAdvice
public class UuidRequestBody extends RequestBodyAdviceAdapter {
    private final HttpServletRequest request;
    private final PublicIdentifiers identifiers;
    private final JsonMapper json = JsonMapper.builder().build();
    public UuidRequestBody(HttpServletRequest request, PublicIdentifiers identifiers) {
        this.request = request; this.identifiers = identifiers;
    }
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return Boolean.TRUE.equals(request.getAttribute(UuidApiFilter.ATTRIBUTE));
    }
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter,
            Type type, Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        Object body;
        try { body = json.readValue(input.getBody(), Object.class); }
        catch (tools.jackson.core.JacksonException failure) {
            throw new org.springframework.http.converter.HttpMessageNotReadableException("Nieprawidłowy JSON.", failure, input);
        }
        byte[] translated = json.writeValueAsBytes(translate(body));
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(input.getHeaders()); headers.setContentLength(translated.length);
        return new HttpInputMessage() {
            public InputStream getBody() { return new ByteArrayInputStream(translated); }
            public HttpHeaders getHeaders() { return headers; }
        };
    }
    private Object translate(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = (String) key;
                var kind = PublicIdentifiers.field(name);
                result.put(name, kind == null ? translate(item) : reference(name, kind, item));
            });
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(this::translate).toList();
        return value;
    }
    private Object reference(String field, PublicIdentifiers.Kind kind, Object value) {
        if (value == null) return null;
        if (value instanceof List<?> list) {
            int limit = field.equals("cliqueIds") ? 10 : field.equals("placeIds") ? 100 : 200;
            if (list.size() > limit) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Zbyt wiele identyfikatorów.");
            return list.stream().map(item -> item == null ? null : identifiers.internal(kind, item)).toList();
        }
        return identifiers.internal(kind, value);
    }
}
