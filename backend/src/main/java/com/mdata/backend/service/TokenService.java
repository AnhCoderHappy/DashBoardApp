package com.mdata.backend.service;

import com.mdata.backend.entity.PlatformConnection;
import com.mdata.backend.entity.PlatformToken;
import com.mdata.backend.repository.PlatformConnectionRepository;
import com.mdata.backend.repository.PlatformTokenRepository;
import com.mdata.backend.util.CryptoUtil;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TokenService {

    private final PlatformTokenRepository tokenRepository;
    private final PlatformConnectionRepository connectionRepository;
    private final CryptoUtil cryptoUtil;

    public TokenService(
            PlatformTokenRepository tokenRepository,
            PlatformConnectionRepository connectionRepository,
            CryptoUtil cryptoUtil
    ) {
        this.tokenRepository = tokenRepository;
        this.connectionRepository = connectionRepository;
        this.cryptoUtil = cryptoUtil;
    }

    @Value
    public static class DecryptedToken {
        String accessToken;
        String refreshToken;
        Instant expiresAt;
    }

    public DecryptedToken getConnectionToken(UUID connectionId) {
        PlatformToken token = tokenRepository.findByConnectionId(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("No token found for connection ID " + connectionId));

        String rawAccessToken = cryptoUtil.decryptSecret(token.getAccessToken());
        String rawRefreshToken = token.getRefreshToken() != null ? cryptoUtil.decryptSecret(token.getRefreshToken()) : null;

        return new DecryptedToken(rawAccessToken, rawRefreshToken, token.getExpiresAt());
    }

    @Transactional
    public void updateConnectionToken(
            UUID connectionId,
            String accessToken,
            String refreshToken,
            Instant expiresAt
    ) {
        String encryptedAccessToken = cryptoUtil.encryptSecret(accessToken);
        String encryptedRefreshToken = refreshToken != null ? cryptoUtil.encryptSecret(refreshToken) : null;

        Optional<PlatformToken> existingOpt = tokenRepository.findByConnectionId(connectionId);
        PlatformToken token = existingOpt.orElseGet(PlatformToken::new);

        token.setConnectionId(connectionId);
        token.setAccessToken(encryptedAccessToken);
        token.setRefreshToken(encryptedRefreshToken);
        token.setExpiresAt(expiresAt);
        token.setUpdatedAt(Instant.now());

        tokenRepository.save(token);

        // Update connection status
        PlatformConnection conn = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("No connection found for ID " + connectionId));
        conn.setStatus("active");
        conn.setLastConnectedAt(Instant.now());
        conn.setUpdatedAt(Instant.now());
        connectionRepository.save(conn);
    }

    @Transactional
    public void saveConnectionToken(UUID connectionId, String accessToken) {
        updateConnectionToken(connectionId, accessToken, null, null);
    }

    @Transactional
    public void deleteConnectionToken(UUID connectionId) {
        tokenRepository.findByConnectionId(connectionId).ifPresent(tokenRepository::delete);
    }
}
