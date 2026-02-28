package com.acheao.languageagent.service;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.dto.req.LoginReq;
import com.acheao.languageagent.dto.req.RegisterReq;
import com.acheao.languageagent.dto.res.AuthRes;
import com.acheao.languageagent.exception.BusinessException;
import com.acheao.languageagent.exception.ErrorCode;
import com.acheao.languageagent.repository.UserRepository;
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

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthRes register(RegisterReq request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Username already exists");
        }

        User user = new User(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()));

        userRepository.save(user);
        String jwtToken = jwtService.generateToken(user);

        return new AuthRes(jwtToken, user.getUsername());
    }

    public AuthRes login(LoginReq request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

        String jwtToken = jwtService.generateToken(user);

        return new AuthRes(jwtToken, user.getUsername());
    }
}
