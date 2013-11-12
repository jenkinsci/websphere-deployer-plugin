package org.jenkinsci.plugins.websphere_deployer.services;

/**
 * Defines a server running under WebSphere.
 *
 * @author Greg Peters
 */
public class Server {

    private String cellName;
    private String nodeName;
    private String serverName;
    private String platformName;
    private String platformVersion;
    private String serverVersion;
    private String serverVendor;
    private String processType;
    private String processId;

    public String getPlatformName() {
        return platformName;
    }

    public void setPlatformName(String platformName) {
        this.platformName = platformName;
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    public void setPlatformVersion(String platformVersion) {
        this.platformVersion = platformVersion;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public String getServerVendor() {
        return serverVendor;
    }

    public void setServerVendor(String serverVendor) {
        this.serverVendor = serverVendor;
    }

    public String getProcessType() {
        return processType;
    }

    public void setProcessType(String processType) {
        this.processType = processType;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getCellName() {
        return cellName;
    }

    public void setCellName(String cellName) {
        this.cellName = cellName;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
}
