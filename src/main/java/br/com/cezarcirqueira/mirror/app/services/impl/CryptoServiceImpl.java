package br.com.cezarcirqueira.mirror.app.services.impl;

import br.com.cezarcirqueira.mirror.app.config.CryptoProperties;
import br.com.cezarcirqueira.mirror.app.services.CryptoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Service
public class CryptoServiceImpl implements CryptoService {

    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String AES_ALGORITHM = "AES";
    private static final int RSA_KEY_SIZE_BITS = 3072;
    private static final int AES_KEY_SIZE_BYTES = 32;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8;
    private static final int STREAM_BUFFER_SIZE = 8192;

    private final CryptoProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    private KeyPair serverKeyPair;
    private String publicKeyPem;

    public CryptoServiceImpl(CryptoProperties properties,
                             ResourceLoader resourceLoader,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        try {
            if (properties.getKeystorePath() != null && !properties.getKeystorePath().isBlank()) {
                this.serverKeyPair = loadFromKeystore();
                log.info("Crypto: loaded RSA key pair from keystore '{}'", properties.getKeystorePath());
            } else {
                this.serverKeyPair = generateEphemeralKeyPair();
                log.warn("Crypto: no keystore configured. Generated EPHEMERAL RSA-{} key pair. "
                        + "Set 'mirror-app.crypto.keystore-path' in production.", RSA_KEY_SIZE_BITS);
            }
            this.publicKeyPem = encodePublicKeyAsPem(serverKeyPair.getPublic());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise crypto material: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    @Override
    public SecretKey unwrapSessionKey(String base64EncryptedSessionKey) {
        if (base64EncryptedSessionKey == null || base64EncryptedSessionKey.isBlank()) {
            throw new IllegalArgumentException("Encrypted session key is required");
        }
        try {
            byte[] encrypted = Base64.getDecoder().decode(base64EncryptedSessionKey);
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, serverKeyPair.getPrivate());
            byte[] keyBytes = cipher.doFinal(encrypted);
            if (keyBytes.length != AES_KEY_SIZE_BYTES) {
                throw new IllegalArgumentException(
                        "Unexpected session key size: " + keyBytes.length
                                + " bytes (expected " + AES_KEY_SIZE_BYTES + ")");
            }
            return new SecretKeySpec(keyBytes, AES_ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Failed to unwrap session key: " + e.getMessage(), e);
        }
    }

    @Override
    public String decryptToString(String base64Ciphertext, SecretKey sessionKey) {
        if (base64Ciphertext == null || base64Ciphertext.isBlank()) {
            throw new IllegalArgumentException("Ciphertext is required");
        }
        try {
            byte[] data = Base64.getDecoder().decode(base64Ciphertext);
            int minLength = GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES;
            if (data.length < minLength) {
                throw new IllegalArgumentException(
                        "Ciphertext too short: " + data.length + " bytes (minimum " + minLength + ")");
            }
            byte[] iv = Arrays.copyOfRange(data, 0, GCM_IV_LENGTH_BYTES);
            byte[] cipherWithTag = Arrays.copyOfRange(data, GCM_IV_LENGTH_BYTES, data.length);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(cipherWithTag);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Failed to decrypt payload: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T decryptToObject(String base64Ciphertext, SecretKey sessionKey, Class<T> type) {
        try {
            return objectMapper.readValue(decryptToString(base64Ciphertext, sessionKey), type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to parse decrypted payload as " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void encryptStream(InputStream input, OutputStream output, SecretKey sessionKey) throws IOException {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            output.write(iv);

            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                byte[] encrypted = cipher.update(buffer, 0, read);
                if (encrypted != null && encrypted.length > 0) {
                    output.write(encrypted);
                }
            }
            byte[] tail = cipher.doFinal();
            if (tail != null && tail.length > 0) {
                output.write(tail);
            }
            output.flush();
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to encrypt stream: " + e.getMessage(), e);
        }
    }

    @Override
    public SecretKey generateSessionKey() {
        byte[] keyBytes = new byte[AES_KEY_SIZE_BYTES];
        secureRandom.nextBytes(keyBytes);
        return new SecretKeySpec(keyBytes, AES_ALGORITHM);
    }

    @Override
    public String wrapSessionKey(SecretKey sessionKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, serverKeyPair.getPublic());
            byte[] wrapped = cipher.doFinal(sessionKey.getEncoded());
            return Base64.getEncoder().encodeToString(wrapped);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to wrap session key: " + e.getMessage(), e);
        }
    }

    @Override
    public String encryptToBase64(String plaintext, SecretKey sessionKey) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext is required");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt payload: " + e.getMessage(), e);
        }
    }

    @Override
    public void decryptStream(InputStream input, OutputStream output, SecretKey sessionKey) throws IOException {
        try {
            byte[] iv = input.readNBytes(GCM_IV_LENGTH_BYTES);
            if (iv.length != GCM_IV_LENGTH_BYTES) {
                throw new IOException("Stream ended before IV could be read");
            }

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                byte[] chunk = cipher.update(buffer, 0, read);
                if (chunk != null && chunk.length > 0) {
                    output.write(chunk);
                }
            }
            byte[] tail = cipher.doFinal();
            if (tail != null && tail.length > 0) {
                output.write(tail);
            }
            output.flush();
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to decrypt stream: " + e.getMessage(), e);
        }
    }

    private KeyPair generateEphemeralKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE_BITS, secureRandom);
        return generator.generateKeyPair();
    }

    private KeyPair loadFromKeystore() throws GeneralSecurityException, IOException {
        Resource resource = resourceLoader.getResource(properties.getKeystorePath());
        if (!resource.exists()) {
            throw new IllegalStateException("Keystore not found: " + properties.getKeystorePath());
        }

        KeyStore keyStore = KeyStore.getInstance(properties.getKeystoreType());
        try (InputStream is = resource.getInputStream()) {
            keyStore.load(is, properties.getKeystorePassword().toCharArray());
        }

        char[] keyPassword = properties.getKeyPassword().toCharArray();
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(properties.getKeyAlias(), keyPassword);
        if (privateKey == null) {
            throw new IllegalStateException("No private key under alias '" + properties.getKeyAlias() + "'");
        }
        Certificate certificate = keyStore.getCertificate(properties.getKeyAlias());
        if (certificate == null) {
            throw new IllegalStateException("No certificate under alias '" + properties.getKeyAlias() + "'");
        }
        return new KeyPair(certificate.getPublicKey(), privateKey);
    }

    private String encodePublicKeyAsPem(PublicKey publicKey) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }
}
