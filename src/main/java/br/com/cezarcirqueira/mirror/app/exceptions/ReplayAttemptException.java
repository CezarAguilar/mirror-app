package br.com.cezarcirqueira.mirror.app.exceptions;

public class ReplayAttemptException extends SecurityException {

    public ReplayAttemptException(String message) {
        super(message);
    }
}
