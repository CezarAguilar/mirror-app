package br.com.cezarcirqueira.mirror.app.adapter.in.web.dto;

public record LocalReplicaResponse(
        String mirrorGuid,
        String rootPath,
        boolean mirrorPaused,
        boolean replicaPaused,
        int openConflictCount) {}
