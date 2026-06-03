package br.com.cezarcirqueira.mirror.app.services;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface CryptoService {

    String getPublicKeyPem();

    SecretKey unwrapSessionKey(String base64EncryptedSessionKey);

    String decryptToString(String base64Ciphertext, SecretKey sessionKey);

    <T> T decryptToObject(String base64Ciphertext, SecretKey sessionKey, Class<T> type);

    void encryptStream(InputStream input, OutputStream output, SecretKey sessionKey) throws IOException;

    SecretKey generateSessionKey();

    String wrapSessionKey(SecretKey sessionKey);

    String encryptToBase64(String plaintext, SecretKey sessionKey);

    void decryptStream(InputStream input, OutputStream output, SecretKey sessionKey) throws IOException;
}
