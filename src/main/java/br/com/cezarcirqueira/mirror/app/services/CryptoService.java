package br.com.cezarcirqueira.mirror.app.services;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface CryptoService {

    /**
     * Returns the server's RSA public key in PEM (X.509 SubjectPublicKeyInfo) format.
     */
    String getPublicKeyPem();

    /**
     * Decrypts a Base64-encoded AES-256 session key that was wrapped with the
     * server's RSA public key using {@code RSA/ECB/OAEPWithSHA-256AndMGF1Padding}.
     */
    SecretKey unwrapSessionKey(String base64EncryptedSessionKey);

    /**
     * Decrypts a Base64-encoded payload using {@code AES/GCM/NoPadding}.
     * Expected layout of the decoded bytes: {@code IV(12) || ciphertext || tag(16)}.
     */
    String decryptToString(String base64Ciphertext, SecretKey sessionKey);

    /**
     * Decrypts a Base64-encoded JSON payload and deserialises it into {@code type}.
     */
    <T> T decryptToObject(String base64Ciphertext, SecretKey sessionKey, Class<T> type);

    /**
     * Streams the data from {@code input}, encrypting each chunk on-the-fly with
     * {@code AES/GCM/NoPadding} using {@code sessionKey} and writing the ciphertext —
     * preceded by a fresh 12-byte IV — directly into {@code output}.
     *
     * <p>The implementation does not buffer the full payload in memory and flushes
     * {@code output}, leaving its closure to the caller.</p>
     */
    void encryptStream(InputStream input, OutputStream output, SecretKey sessionKey) throws IOException;

    /**
     * Generates a fresh random AES-256 key suitable for use as an envelope
     * session key.
     */
    SecretKey generateSessionKey();

    /**
     * Wraps {@code sessionKey} with the server's RSA public key using
     * {@code RSA/ECB/OAEPWithSHA-256AndMGF1Padding} and returns the resulting
     * ciphertext Base64-encoded. Counterpart of {@link #unwrapSessionKey(String)}.
     */
    String wrapSessionKey(SecretKey sessionKey);

    /**
     * Encrypts {@code plaintext} with {@code AES/GCM/NoPadding} using
     * {@code sessionKey} and returns Base64 of {@code IV(12) || ciphertext || tag(16)}.
     * Counterpart of {@link #decryptToString(String, SecretKey)}.
     */
    String encryptToBase64(String plaintext, SecretKey sessionKey);

    /**
     * Reads ciphertext from {@code input} in the {@code IV(12) || ciphertext || tag}
     * layout and writes the decrypted bytes to {@code output} on-the-fly.
     * Counterpart of {@link #encryptStream(InputStream, OutputStream, SecretKey)}.
     */
    void decryptStream(InputStream input, OutputStream output, SecretKey sessionKey) throws IOException;
}
