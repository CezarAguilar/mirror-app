package br.com.cezarcirqueira.mirror.app.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mirror")
public class MirrorProperties {

    private String dataDir = System.getProperty("user.home") + "/.mirror-app";
    private String nodeDisplayName = "localhost";
    private long watchDebounceMs = 500;
    private long peerConnectTimeoutMs = 5000;
    private long peerReadTimeoutMs = 60_000;

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getNodeDisplayName() {
        return nodeDisplayName;
    }

    public void setNodeDisplayName(String nodeDisplayName) {
        this.nodeDisplayName = nodeDisplayName;
    }

    public long getWatchDebounceMs() {
        return watchDebounceMs;
    }

    public void setWatchDebounceMs(long watchDebounceMs) {
        this.watchDebounceMs = watchDebounceMs;
    }

    public long getPeerConnectTimeoutMs() {
        return peerConnectTimeoutMs;
    }

    public void setPeerConnectTimeoutMs(long peerConnectTimeoutMs) {
        this.peerConnectTimeoutMs = peerConnectTimeoutMs;
    }

    public long getPeerReadTimeoutMs() {
        return peerReadTimeoutMs;
    }

    public void setPeerReadTimeoutMs(long peerReadTimeoutMs) {
        this.peerReadTimeoutMs = peerReadTimeoutMs;
    }
}
