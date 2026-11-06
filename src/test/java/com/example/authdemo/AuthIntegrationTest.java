package com.example.authdemo;

import com.example.authdemo.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ResultActions login(String username, String password) throws Exception {
        LoginRequest body = new LoginRequest(username, password);
        return mockMvc.perform(post("/api/login")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    private MockHttpSession loginAndGetSession() throws Exception {
        return (MockHttpSession) login("test", "123456")
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession();
    }

    @Test
    void helloWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/hello"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        login("test", "wrong-password")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginWithTestCredentialsReturns200() throws Exception {
        login("test", "123456")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.message").value("Login successful"));
    }

    @Test
    void csrfEndpointReturnsTokenAnonymously() throws Exception {
        // Note: spring-security-test decorates the CsrfFilter's repository with its own
        // test repository once csrf() is used anywhere in the suite, so the headerName
        // is not asserted here; at runtime it is X-XSRF-TOKEN (see README).
        mockMvc.perform(get("/api/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token", notNullValue()))
            .andExpect(jsonPath("$.parameterName").value("_csrf"));
    }

    @Test
    void helloWithSessionAfterLoginReturnsHelloWorld() throws Exception {
        MockHttpSession session = loginAndGetSession();

        mockMvc.perform(get("/api/hello").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string("Hello World"));
    }

    @Test
    void logoutAfterLoginReturns200() throws Exception {
        MockHttpSession session = loginAndGetSession();

        mockMvc.perform(post("/api/logout")
                .session(session)
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Logout successful"));
    }

    @Test
    void helloAfterLogoutReturns401() throws Exception {
        MockHttpSession session = loginAndGetSession();

        mockMvc.perform(post("/api/logout").session(session).with(csrf()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/hello").session(session))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPostWithoutCsrfTokenIsRejected() throws Exception {
        MockHttpSession session = loginAndGetSession();

        mockMvc.perform(post("/api/logout").session(session))
            .andExpect(status().isForbidden());
    }
}
