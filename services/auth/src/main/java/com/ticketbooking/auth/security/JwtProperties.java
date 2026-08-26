package com.ticketbooking.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String privateKeyPath,
        String publicKeyPath,
        String keyId,
        long accessTokenExpirationSeconds,
        long refreshTokenExpirationSeconds
) {
    public static final SignatureAlgorithm JWT_ALGORITHM = SignatureAlgorithm.RS256;

    public RSAPrivateKey getPrivateKey() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(readKeyFile(privateKeyPath));
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể nạp JWT private key (RS256) từ " + privateKeyPath + ": " + e.getMessage(), e);
        }
    }

    public RSAPublicKey getPublicKey() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(readKeyFile(publicKeyPath));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể nạp JWT public key (RS256) từ " + publicKeyPath + ": " + e.getMessage(), e);
        }
    }

    private static String readKeyFile(String path) throws IOException {
        return Files.readString(Path.of(path)).trim();
    }
}
