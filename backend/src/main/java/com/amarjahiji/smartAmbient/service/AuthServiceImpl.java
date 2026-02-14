package com.amarjahiji.smartAmbient.service;

import com.amarjahiji.smartAmbient.dto.AccessTokenResponse;
import com.amarjahiji.smartAmbient.dto.SignIn;
import com.amarjahiji.smartAmbient.dto.SignUp;
import com.amarjahiji.smartAmbient.entity.User;
import com.amarjahiji.smartAmbient.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;

@Service
class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${app.security.jwt.private-key:}")
    private String privateKeyStr;

    @org.springframework.beans.factory.annotation.Value("${app.security.jwt.public-key:}")
    private String publicKeyStr;

    private static final String DEFAULT_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqU3LwJmsiDiqIVAD+qFnnN1Ni3gXq/3BCF66oX8i58tjK2X1DQ5QiSld0RffeNfcnoYQ97pD5FvCUx+eYaSaVsCrNbTFgE5yJ7hLTqG4fWkqualc7k5/pkawYdKefHHRfgZ4JlhYqgGw9axtX6UP4oiCMsSbLLCniNt4ZfsTqTDbZdWrn38jOwAr+zrvmcQSAYI1P8hMdj9Nof/yPpKPltS+ly4BzXap5T++0rz9lOCftWpHYHt1rFjwHVcqc/tkZGIswu7Otrh407zo58OENlVnCfznXA7F2rEXPeUL49DuW+PXjXcTguWaW/c3zv3frW+r+Ie55OhglaGd0A4VawIDAQAB";
    private static final String DEFAULT_PRIVATE_KEY = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCpTcvAmayIOKohUAP6oWec3U2LeBer/cEIXrqhfyLny2MrZfUNDlCJKV3RF99419yehhD3ukPkW8JTH55hpJpWwKs1tMWATnInuEtOobh9aSq5qVzuTn+mRrBh0p58cdF+BngmWFiqAbD1rG1fpQ/iiIIyxJsssKeI23hl+xOpMNtl1auffyM7ACv7Ou+ZxBIBgjU/yEx2P02h//I+ko+W1L6XLgHNdqnlP77SvP2U4J+1akdge3WsWPAdVypz+2RkYizC7s62uHjTvOjnw4Q2VWcJ/OdcDsXasRc95Qvj0O5b49eNdxOC5Zpb9zfO/d+tb6v4h7nk6GCVoZ3QDhVrAgMBAAECggEBAIH2WiYq0SpwdQjFZ4iJRgRATTp2oZVBYWCPdyxpb94HXsT9qzKuflwMCRxs+vrEmXKG75d6wIsXdQBiES5bMK7Pj53WepWXokGjUwu1UO3UQRvsSo4UbCCzusoc59QXev0G8kxdHRLD4Zd1GTCGgL8gkvFtwsB3iuOftbhzGBCTVOv8ENm/P1PPf6pstA790kZk/AJOI2wAw+FUnLbUmeVOrvdz3yTm3oe7ywHjf37fP83/ussbrUKJeWPSu9IG+KTwSoTbo1pCO4FzEFxiFXO53304US5bdie6LOr7fjdw5qhSsURr+XeHQZBxWTzwqMJ7wClOx5DrORxjPfSPH+ECgYEA08EZBjlTIzWwnMFVIYQUVWm1//rwTbWt9v2Vx3iyH+7DLaKx+KpDPSGhB+8U94kkrShHd9L+hZRdIY4B8N6ilkCU3y6A6U+EBe5TasSIEoM/e1dEby5+M5FPZdaPUxnJWuuXh/Adz7UU2ZxUQwuiMX+VRLMOAHywLvzOaFg/PyUCgYEAzK3+Q970poGFRyk5sbELLvwb548BSS2bQHHuyY/o8NEzzSH7pVrxIf0HTYZ1zT6Q53jYRKNq8UiJK4h+PcXG1CUZkxXAZG04xn6BpPofsQN4LSnGlVUhfmJLQkgretsChG5bhzUlqAMkDw3n09vA75psDC3ZYvE2VMWQDAwgZU8CgYAD+JIMkNSjS2V1exaqmzx6YZIdK8qH2olZoWXGqNfGS4bzeyKVRDQgmFnZuT0Oa075xFCayaUmQiMA9xXIO5SW9r0T9l5Kgcg7CD4eOXNHzZhKKtfIsfmB5A62HTDw4QHqp5Je5TzZ4U5zyj+2RiiTfw4AjM8NaUkwiGMih80f1QKBgQDG46/2vbkX7zXWP04Lx9DpOBvZeBG0zTdWeR+jB09AatkeVQ5V0LgN4fTttWHVLh3af4gPsohhq612+uxJFF0vmllunq1UKPoJj7Zk3JRdCtUFddm9FHs2d0dQQhbWC+k3TJFuIgvUZjDs3ANQz/J8IZ8qeocJ6QB1gYCG5GW6awKBgHnmXHxEbipPQ3TuBq/ws/kfEhsVbcBROD2/dWJ7nPm2Pvgpu9HM7q9xxLpVLcLv4eLC3sSkc0oDBP+CNHK+cuq7HodFPmj0HdzY2bbkiRXMaulksjybYHSto0UbSbm2q/kdfGrh4QxROJ2cOIAvr7bF71KafWqQq59THRXf+W0G";

    AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AccessTokenResponse signUp(SignUp signUp) throws Exception {
        if (userRepository.existsByEmail(signUp.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        if (userRepository.existsByUsername(signUp.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        
        User savedUser = userRepository.save(mapSignUpToUser(signUp));
        return generateToken(savedUser.getId());
    }

    @Override
    public AccessTokenResponse signIn(SignIn signIn) throws Exception {
        User user = userRepository.findByEmail(signIn.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(signIn.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }
        return generateToken(user.getId());
    }

    private User mapSignUpToUser(SignUp signUp) {
        return User.builder()
                .firstName(signUp.getFirstName())
                .lastName(signUp.getLastName())
                .username(signUp.getUsername())
                .email(signUp.getEmail())
                .password(passwordEncoder.encode(signUp.getPassword()))
                .birthday(signUp.getBirthday())
                .build();
    }

    private AccessTokenResponse generateToken(String userId) throws Exception {
        return new AccessTokenResponse(Jwts.builder()
                .claims()
                .add(new HashMap<>())
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + 86400000))
                .issuer("smartambient")
                .and()
                .signWith(getPrivateKey(), Jwts.SIG.RS256)
                .compact());
    }

    @Override
    public String extractUsernameFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isTokenValid(String token, String username) {
        final String extractedUsername = extractUsernameFromToken(token);
        return (extractedUsername != null && extractedUsername.equals(username));
    }

    public PublicKey getPublicKey() throws Exception {
        String key = (publicKeyStr != null && !publicKeyStr.isEmpty()) ? publicKeyStr : DEFAULT_PUBLIC_KEY;
        return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder()
                        .decode(key)));
    }

    public PrivateKey getPrivateKey() throws Exception {
        String key = (privateKeyStr != null && !privateKeyStr.isEmpty()) ? privateKeyStr : DEFAULT_PRIVATE_KEY;
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder()
                        .decode(key)));
    }
}
