package net.onelitefeather.menura.server.audit;

/** The client a write came from. The value is the one stored in the audit log. */
public enum Channel {
    WEB("web"),
    DESKTOP("desktop"),
    OBS("obs"),
    MCP("mcp");

    private final String value;

    Channel(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
