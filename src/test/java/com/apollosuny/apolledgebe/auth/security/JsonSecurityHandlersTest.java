package com.apollosuny.apolledgebe.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSecurityHandlersTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void entryPoint_shouldReturn401JsonBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAuthenticationEntryPoint(objectMapper).commence(
                new MockHttpServletRequest(), response, new InsufficientAuthenticationException("no token"));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("code").asString()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.has("timestamp")).isTrue();
    }

    @Test
    void accessDeniedHandler_shouldReturn403JsonBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JsonAccessDeniedHandler(objectMapper).handle(
                new MockHttpServletRequest(), response, new AccessDeniedException("nope"));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body.get("code").asString()).isEqualTo("ACCESS_DENIED");
    }
}
