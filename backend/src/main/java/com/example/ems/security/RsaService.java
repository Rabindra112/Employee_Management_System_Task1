package com.example.ems.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;

import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import java.util.Base64;

@Service
public class RsaService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public RsaService(
            @Value("${app.rsa.private-key-path}") String privatePath,
            @Value("${app.rsa.public-key-path}") String publicPath
    ) throws Exception {

        String privatePem = Files.readString(Path.of(privatePath))
                .replaceAll(
                        "-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\\s",
                        ""
                );

        String publicPem = Files.readString(Path.of(publicPath))
                .replaceAll(
                        "-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\\s",
                        ""
                );

        KeyFactory factory = KeyFactory.getInstance("RSA");

        this.privateKey = factory.generatePrivate(
                new PKCS8EncodedKeySpec(
                        Base64.getDecoder().decode(privatePem)
                )
        );

        this.publicKey = factory.generatePublic(
                new X509EncodedKeySpec(
                        Base64.getDecoder().decode(publicPem)
                )
        );
    }

    public String getPublicKey() {

        return Base64.getEncoder()
                .encodeToString(publicKey.getEncoded());
    }

    public String decrypt(String encryptedPassword) throws Exception {

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");

        OAEPParameterSpec spec = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        );

        cipher.init(
                Cipher.DECRYPT_MODE,
                privateKey,
                spec
        );

        byte[] encryptedBytes =
                Base64.getDecoder().decode(encryptedPassword);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

        return new String(
                decryptedBytes,
                StandardCharsets.UTF_8
        );
    }
}