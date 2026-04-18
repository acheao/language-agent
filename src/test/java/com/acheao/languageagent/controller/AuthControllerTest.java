package com.acheao.languageagent.controller;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.dto.req.LoginReq;
import com.acheao.languageagent.dto.req.RegisterReq;
import com.acheao.languageagent.dto.req.UpdateProfileReq;
import com.acheao.languageagent.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        RegisterReq req = new RegisterReq();
        req.setEmail("test@example.com");
        req.setDisplayName("Test User");
        req.setPassword("password123");
        req.setDailyGoalMinutes(30);
        req.setTargetIeltsScore(6.5);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.user.displayName").value("Test User"))
                .andExpect(jsonPath("$.data.user.dailyGoalMinutes").value(30));

        User saved = userRepository.findByEmail("test@example.com").orElseThrow();
        assertNotNull(saved.getId());
        assertEquals("test@example.com", ReflectionTestUtils.getField(saved, "username"));
    }

    @Test
    void register_duplicateEmail() throws Exception {
        User user = new User("test@example.com", "Existing User", passwordEncoder.encode("password123"));
        userRepository.save(user);

        RegisterReq req = new RegisterReq();
        req.setEmail("test@example.com");
        req.setPassword("newpassword");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001))
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    void login_success() throws Exception {
        User user = new User("test@example.com", "Existing User", passwordEncoder.encode("password123"));
        userRepository.save(user);

        LoginReq req = new LoginReq();
        req.setEmail("test@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"));
    }

    @Test
    void me_and_updateProfile_success() throws Exception {
        User user = new User("test@example.com", "Existing User", passwordEncoder.encode("password123"));
        user.setDailyGoalMinutes(25);
        userRepository.save(user);

        String token = loginAndGetToken("test@example.com", "password123");

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.dailyGoalMinutes").value(25));

        UpdateProfileReq update = new UpdateProfileReq();
        update.setDisplayName("Updated Name");
        update.setDailyGoalMinutes(45);
        update.setTargetIeltsScore(7.0);

        mockMvc.perform(patch("/api/auth/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.displayName").value("Updated Name"))
                .andExpect(jsonPath("$.data.dailyGoalMinutes").value(45))
                .andExpect(jsonPath("$.data.targetIeltsScore").value(7.0));
    }

    @Test
    void login_badCredentials_returnsServerErrorEnvelope() throws Exception {
        User user = new User("test@example.com", "Existing User", passwordEncoder.encode("password123"));
        userRepository.save(user);

        LoginReq req = new LoginReq();
        req.setEmail("test@example.com");
        req.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.code").value(5000));
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        LoginReq req = new LoginReq();
        req.setEmail(email);
        req.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }
}
