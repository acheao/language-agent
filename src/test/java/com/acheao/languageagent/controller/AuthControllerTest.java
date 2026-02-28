package com.acheao.languageagent.controller;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.dto.req.LoginReq;
import com.acheao.languageagent.dto.req.RegisterReq;
import com.acheao.languageagent.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void register_success() throws Exception {
        RegisterReq req = new RegisterReq("testuser", "password123");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    void register_duplicateUsername() throws Exception {
        User user = new User("testuser", passwordEncoder.encode("password123"));
        userRepository.save(user);

        RegisterReq req = new RegisterReq("testuser", "newpassword");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("2001")); // VALIDATION_ERROR mapped to GlobalExceptionHandler in
                                                              // existing project? Let's check status.
    }

    @Test
    void login_success() throws Exception {
        User user = new User("testuser", passwordEncoder.encode("password123"));
        userRepository.save(user);

        LoginReq req = new LoginReq("testuser", "password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.token").exists());
    }

    @Test
    void login_badCredentials() throws Exception {
        User user = new User("testuser", passwordEncoder.encode("password123"));
        userRepository.save(user);

        LoginReq req = new LoginReq("testuser", "wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("500")); // Depending on GlobalExceptionHandler
    }
}
