package com.cloudresilience.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** Fails closed with a safe response when the shared session store cannot be used. */
public class RedisAvailabilityFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    public RedisAvailabilityFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (RedisConnectionFailureException | RedisSystemException unavailable) {
            if (!response.isCommitted()) {
                ApiErrorWriter.write(request, response, mapper, 503, "Service Unavailable",
                        "Shared session storage is temporarily unavailable");
            } else {
                throw unavailable;
            }
        }
    }
}
