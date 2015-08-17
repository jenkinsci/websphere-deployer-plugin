package org.jenkinsci.plugins.websphere.services.deployment;

import java.io.File;
import java.util.HashMap;

public interface DeploymentService {

    void installArtifact(Artifact artifact, HashMap<String, Object> options);
    void uninstallArtifact(String name) throws Exception;
    void startArtifact(String name) throws Exception;
    void stopArtifact(String name) throws Exception;
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
