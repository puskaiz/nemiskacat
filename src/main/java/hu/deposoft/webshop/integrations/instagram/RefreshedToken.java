package hu.deposoft.webshop.integrations.instagram;

import java.time.Instant;

public record RefreshedToken(String accessToken, Instant expiresAt) {}
