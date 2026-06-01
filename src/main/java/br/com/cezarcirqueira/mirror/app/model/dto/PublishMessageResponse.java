package br.com.cezarcirqueira.mirror.app.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishMessageResponse {
    private String messageId;
    private String queue;
    private List<String> targets;
}
