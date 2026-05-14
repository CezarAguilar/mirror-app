package br.com.cezarcirqueira.mirror.websocket;

public class SyncMessage {

    private String guid;
    private String relativePath;
    private String action;
    private String originIp;

    public SyncMessage() {}

    public SyncMessage(String guid, String relativePath, String action, String originIp) {
        this.guid = guid;
        this.relativePath = relativePath;
        this.action = action;
        this.originIp = originIp;
    }

    public String getGuid() { return guid; }
    public void setGuid(String guid) { this.guid = guid; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOriginIp() { return originIp; }
    public void setOriginIp(String originIp) { this.originIp = originIp; }
}
