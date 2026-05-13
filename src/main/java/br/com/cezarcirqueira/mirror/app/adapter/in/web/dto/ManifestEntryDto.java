package br.com.cezarcirqueira.mirror.app.adapter.in.web.dto;

public record ManifestEntryDto(String relativePath, String sha256Hex, long sizeBytes, long lastModifiedEpochMs) {}
