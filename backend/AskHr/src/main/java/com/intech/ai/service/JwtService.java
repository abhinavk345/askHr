package com.intech.ai.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JwtService {

    private final String SECRET = "mySecretKey123"; // use secure key in prod
    private final long EXPIRATION = 1000 * 60 * 60; // 1 hour

    public String generateToken(String employeeId, String role) {
        return Jwts.builder()
                .setSubject(employeeId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(SignatureAlgorithm.HS256, SECRET)
                .compact();
    }
}
