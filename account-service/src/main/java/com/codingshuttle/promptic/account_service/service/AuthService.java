package com.codingshuttle.promptic.account_service.service;


import com.codingshuttle.promptic.account_service.dto.auth.AuthResponse;
import com.codingshuttle.promptic.account_service.dto.auth.LoginRequest;
import com.codingshuttle.promptic.account_service.dto.auth.SignupRequest;

public interface AuthService {
    AuthResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);
}

