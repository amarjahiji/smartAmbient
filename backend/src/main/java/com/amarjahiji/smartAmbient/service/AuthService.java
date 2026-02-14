package com.amarjahiji.smartAmbient.service;

import com.amarjahiji.smartAmbient.dto.AccessTokenResponse;
import com.amarjahiji.smartAmbient.dto.SignIn;
import com.amarjahiji.smartAmbient.dto.SignUp;

public interface AuthService {
    AccessTokenResponse signUp(SignUp signUp) throws Exception;
    AccessTokenResponse signIn(SignIn signIn) throws Exception;
    String extractUsernameFromToken(String token);
    boolean isTokenValid(String token, String username);
}
