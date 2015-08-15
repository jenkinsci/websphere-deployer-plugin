package org.jenkinsci.plugins.websphere.services.deployment;

import hudson.model.BuildListener;

import java.io.File;
import java.util.HashMap;

public interface DeploymentService {

    void installArtifact(Artifact artifact, HashMap<String, Object> options,BuildListener listener,boolean verbose);
    void uninstallArtifact(String name,BuildListener listener,boolean verbose) throws Exception;
    void startArtifact(String name,BuildListener listener,boolean verbose) throws Exception;
    void stopArtifact(String name,BuildListener listener,boolean verbose) throws Exception;
    boolean isArtifactInstalled(String name);
    void setTrustStoreLocation(File location);
    void setKeyStoreLocation(File location);
    void setTrustStorePassword(String password);
    void setKeyStorePassword(String password);
    void setHost(String host);
    void setPort(String port);
    void setUsername(String username);
    void setPassword(String password);
    void connect() throws Exception;
    void disconnect();
    boolean isAvailable();
}
