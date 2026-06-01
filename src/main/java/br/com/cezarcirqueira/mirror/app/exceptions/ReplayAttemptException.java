package br.com.cezarcirqueira.mirror.app.exceptions;

/**
 * Thrown when a request appears to be a replay (duplicated nonce or
 * timestamp outside the acceptable window). Extends {@link SecurityException}
 * so it is uniformly mapped to HTTP 403 by the global advice.
 */
public class ReplayAttemptException extends SecurityException {

    public ReplayAttemptException(String message) {
        super(message);
    }
}
