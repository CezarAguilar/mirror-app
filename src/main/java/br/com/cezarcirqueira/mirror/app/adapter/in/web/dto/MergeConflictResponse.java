package br.com.cezarcirqueira.mirror.app.adapter.in.web.dto;

public record MergeConflictResponse(
        long id,
        String mirrorGuid,
        String relativePath,
        String localHash,
        String remoteHash,
        String localTextSnapshot,
        String remoteTextSnapshot,
        String status) {}
