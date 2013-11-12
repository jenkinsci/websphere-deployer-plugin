package org.jenkinsci.plugins.websphere_deployer.services;


/**
 * Represents a JVM running under WebSphere
 *
 * @author Greg Peters
 */
public class JVM {

    private String vendor;
    private String version;
    private String node;
    private String heapSize;
    private String freeMemory;
    private String maxHeapDumpsOnDisk;
    private String maxMemory;

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getHeapSize() {
        return heapSize;
    }

    public void setHeapSize(String heapSize) {
        this.heapSize = heapSize;
    }

    public String getFreeMemory() {
        return freeMemory;
    }

    public void setFreeMemory(String freeMemory) {
        this.freeMemory = freeMemory;
    }

    public String getMaxHeapDumpsOnDisk() {
        return maxHeapDumpsOnDisk;
    }

    public void setMaxHeapDumpsOnDisk(String maxHeapDumpsOnDisk) {
        this.maxHeapDumpsOnDisk = maxHeapDumpsOnDisk;
    }

    public String getMaxMemory() {
        return maxMemory;
    }

    public void setMaxMemory(String maxMemory) {
        this.maxMemory = maxMemory;
    }
}
