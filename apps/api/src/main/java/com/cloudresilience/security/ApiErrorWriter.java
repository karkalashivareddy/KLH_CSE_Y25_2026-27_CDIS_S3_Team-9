package com.cloudresilience.security;

import com.cloudresilience.modules.identity.api.ApiError;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;

public final class ApiErrorWriter {
    private ApiErrorWriter() {}

    public static void write(HttpServletRequest request, HttpServletResponse response, ObjectMapper mapper,
            int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), status, error, message,
                request.getRequestURI(), Map.of()));
    }
}
