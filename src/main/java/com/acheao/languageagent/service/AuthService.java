package com.acheao.languageagent.service;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.dto.req.LoginReq;
import com.acheao.languageagent.dto.req.RegisterReq;
import com.acheao.languageagent.dto.req.UpdateProfileReq;
import com.acheao.languageagent.dto.res.AuthRes;
import com.acheao.languageagent.dto.res.UserProfileRes;
import com.acheao.languageagent.exception.BusinessException;
import com.acheao.languageagent.exception.ErrorCode;
import com.acheao.languageagent.repository.UserRepository;
import com.acheao.languageagent.v2.repository.UserLlmConfigRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserLlmConfigRepository userLlmConfigRepository;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            UserLlmConfigRepository userLlmConfigRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userLlmConfigRepository = userLlmConfigRepository;
    }

    public AuthRes register(RegisterReq request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Email already exists");
        }

        User user = new User(email, request.getDisplayName(), passwordEncoder.encode(request.getPassword()));
        if (request.getDailyGoalMinutes() != null) {
            user.setDailyGoalMinutes(request.getDailyGoalMinutes());
        }
        user.setTargetIeltsScore(request.getTargetIeltsScore());

        userRepository.save(user);
        String jwtToken = jwtService.generateToken(user);
        return new AuthRes(jwtToken, UserProfileRes.from(user, false));
    }

    public AuthRes login(LoginReq request) {
        String email = request.getEmail().trim().toLowerCase();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

        String jwtToken = jwtService.generateToken(user);
        return new AuthRes(jwtToken, UserProfileRes.from(user, userLlmConfigRepository.existsByUser(user)));
    }

    public UserProfileRes me(User user) {
        return UserProfileRes.from(user, userLlmConfigRepository.existsByUser(user));
    }

    public UserProfileRes updateProfile(User authenticatedUser, UpdateProfileReq request) {
        User user = userRepository.findById(authenticatedUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

        if (request.getDisplayName() != null) {
            String displayName = request.getDisplayName().trim();
            user.setDisplayName(displayName.isBlank() ? null : displayName);
        }
        if (request.getDailyGoalMinutes() != null) {
            user.setDailyGoalMinutes(request.getDailyGoalMinutes());
        }
        if (request.getTargetIeltsScore() != null) {
            user.setTargetIeltsScore(request.getTargetIeltsScore());
        }

        User saved = userRepository.save(user);
        return UserProfileRes.from(saved, userLlmConfigRepository.existsByUser(saved));
    }
}
