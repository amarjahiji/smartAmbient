package com.amarjahiji.smartAmbient.controller;

import com.amarjahiji.smartAmbient.dto.AccessTokenResponse;
import com.amarjahiji.smartAmbient.dto.SignIn;
import com.amarjahiji.smartAmbient.dto.SignUp;
import com.amarjahiji.smartAmbient.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AccessTokenResponse> signUp(@Valid @RequestBody SignUp signUp) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signUp(signUp));
    }

    @PostMapping("/signin")
    public ResponseEntity<AccessTokenResponse> signIn(@Valid @RequestBody SignIn signIn) throws Exception {
        return ResponseEntity.ok(authService.signIn(signIn));
    }
}
