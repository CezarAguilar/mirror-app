package br.com.cezarcirqueira.mirror.app.adapter.in.web.dto;

public record ResolveMergeConflictRequest(Resolution resolution, String customContent) {

    public enum Resolution {
        LOCAL,
        REMOTE,
        CUSTOM
    }
}
