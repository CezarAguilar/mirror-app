package br.com.cezarcirqueira.mirror.app.adapter.in.web.dto;

import br.com.cezarcirqueira.mirror.app.domain.ResumeStrategy;

public record ResumeMirrorRequest(ResumeStrategy strategy, OverwriteAuthority overwriteAuthority) {

    public OverwriteAuthority overwriteAuthorityOrDefault() {
        return overwriteAuthority == null ? OverwriteAuthority.LOCAL : overwriteAuthority;
    }

    public enum OverwriteAuthority {
        LOCAL,
        REMOTE
    }
}
