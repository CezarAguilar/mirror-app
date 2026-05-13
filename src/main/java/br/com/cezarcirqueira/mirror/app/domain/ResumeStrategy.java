package br.com.cezarcirqueira.mirror.app.domain;

/**
 * Strategy applied when resuming synchronization after a pause.
 */
public enum ResumeStrategy {
    OVERWRITE_ALL,
    MERGE_REQUIRED
}
