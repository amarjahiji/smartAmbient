package com.amarjahiji.smartAmbient.controller;

import com.amarjahiji.smartAmbient.dto.AccessTokenResponse;
import com.amarjahiji.smartAmbient.dto.SignUp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AuthControllerTest {

    private final AuthController authController;
    private final com.amarjahiji.smartAmbient.repository.UserRepository userRepository;

    @Autowired
    public AuthControllerTest(AuthController authController, com.amarjahiji.smartAmbient.repository.UserRepository userRepository) {
        this.authController = authController;
        this.userRepository = userRepository;
    }

    @Test
    void signUp() throws Exception {
        String email = "amarjahiji2004@gmail.com";
        String username = "amarjahiji";
        // Clean up before test in case it's already there
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
        userRepository.findByUsername(username).ifPresent(userRepository::delete);

        SignUp signUpRequest = SignUp.builder()
                .firstName("Amar")
                .lastName("Jahiji")
                .username("amarjahiji")
                .email(email)
                .password("flacko1237")
                .birthday(LocalDate.of(2004, 1, 5))
                .build();
        ResponseEntity<AccessTokenResponse> response = authController.signUp(signUpRequest);

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getAccessToken());

        // Verify user is in DB
        java.util.Optional<com.amarjahiji.smartAmbient.entity.User> user = userRepository.findByEmail(email);
        assertTrue(user.isPresent(), "User should be saved in the database");
        assertEquals("Amar", user.get().getFirstName());
    }

    @Test
    void signIn() throws Exception {
        String email = "amarjahiji2004_signin@gmail.com";
        String username = "amarjahiji_signin";
        userRepository.findByEmail(email).ifPresent(userRepository::delete);
        userRepository.findByUsername(username).ifPresent(userRepository::delete);

        SignUp signUpRequest = SignUp.builder()
                .firstName("Amar")
                .lastName("Jahiji")
                .username(username)
                .email(email)
                .password("flacko1237")
                .birthday(LocalDate.of(2004, 1, 5))
                .build();
        authController.signUp(signUpRequest);

        com.amarjahiji.smartAmbient.dto.SignIn signInRequest = com.amarjahiji.smartAmbient.dto.SignIn.builder()
                .email(email)
                .password("flacko1237")
                .build();

        ResponseEntity<AccessTokenResponse> response = authController.signIn(signInRequest);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getAccessToken());
    }
}