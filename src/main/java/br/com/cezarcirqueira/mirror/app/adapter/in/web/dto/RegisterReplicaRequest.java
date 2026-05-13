package br.com.cezarcirqueira.mirror.app.adapter.in.web.dto;

public record RegisterReplicaRequest(String mirrorGuid, String sharedSecret, String rootPath) {}
