package com.example.authdemo.controller;

import com.example.authdemo.dto.ApiResponse;
import com.example.authdemo.dto.LoginRequest;
import com.example.authdemo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@RequestBody LoginRequest loginRequest,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        try {
            authService.login(loginRequest.username(), loginRequest.password(), request, response);
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).body(ApiResponse.unauthorized("Invalid username or password"));
        }
        return ResponseEntity.ok(ApiResponse.ok("Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.ok("Logout successful"));
    }
}
