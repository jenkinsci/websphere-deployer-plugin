package org.jenkinsci.plugins.websphere_deployer.services;

/**
 * Represents a target server to connect to.
 *
 * @author Greg Peters
 */
public class Endpoint {

    private String host;
    private String port;
    private String targetNode;
    private String targetCell;
    private String targetServer;
    private String connectionType; //i.e. SOAP, RMI, etc...
    private String username;
    private String password;
    private String clientKeyFile;
    private String clientKeyPassword;
    private String clientTrustFile;
    private String clientTrustPassword;

    public Endpoint() {
        this.port = "8880";
        this.connectionType = "SOAP";
    }

    public String getClientKeyPassword() {
        return clientKeyPassword;
    }

    public void setClientKeyPassword(String clientKeyPassword) {
        this.clientKeyPassword = clientKeyPassword;
    }

    public String getClientTrustPassword() {
        return clientTrustPassword;
    }

    public void setClientTrustPassword(String clientTrustPassword) {
        this.clientTrustPassword = clientTrustPassword;
    }

    public String getClientKeyFile() {
        return clientKeyFile;
    }

    public void setClientKeyFile(String clientKeyFile) {
        this.clientKeyFile = clientKeyFile;
    }

    public String getClientTrustFile() {
        return clientTrustFile;
    }

    public void setClientTrustFile(String clientTrustFile) {
        this.clientTrustFile = clientTrustFile;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTarget() {
        StringBuilder builder = new StringBuilder();
        builder.append("WebSphere:");
        if(targetCell != null) {
            builder.append("cell=");
            builder.append(getTargetCell());
        }
        if(targetNode != null) {
            if(!builder.toString().endsWith(":")) {
                builder.append(",");
            }
            builder.append("node=");
            builder.append(getTargetNode());
        }
        if(targetServer != null) {
            if(!builder.toString().endsWith(":")) {
                builder.append(",");
            }
            builder.append("server=");
            builder.append(getTargetServer());
        }
        return builder.toString();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(String targetNode) {
        this.targetNode = targetNode;
    }

    public String getTargetCell() {
        return targetCell;
    }

    public void setTargetCell(String targetCell) {
        this.targetCell = targetCell;
    }

    public String getTargetServer() {
        return targetServer;
    }

    public void setTargetServer(String targetServer) {
        this.targetServer = targetServer;
    }

    public String getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(String connectionType) {
        this.connectionType = connectionType;
    }
}
