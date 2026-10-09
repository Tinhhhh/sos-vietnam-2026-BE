package com.sosvietnam.service;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.payload.request.AuthenticationRequest;
import com.sosvietnam.model.payload.request.RegistrationRequest;
import com.sosvietnam.model.payload.response.AuthenticationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthenService {
    AuthenticationResponse authenticate(AuthenticationRequest request);

    AuthenticationResponse refreshToken(HttpServletRequest request, HttpServletResponse response);

    AuthenticationResponse register(RegistrationRequest request);

    Account getCurrentAccount();
}
