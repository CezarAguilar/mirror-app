package br.com.cezarcirqueira.mirror.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "mirror-app.sync-folder")
public class SyncFolderProperties {

    private List<String> ignore = new ArrayList<>();
}
