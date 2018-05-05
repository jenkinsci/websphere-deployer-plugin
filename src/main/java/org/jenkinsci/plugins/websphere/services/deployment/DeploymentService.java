package org.jenkinsci.plugins.websphere.services.deployment;

import java.io.File;

public interface DeploymentService {

    void installArtifact(Artifact artifact);
    void updateArtifact(Artifact artifact);
    void uninstallArtifact(Artifact artifact) throws Exception;
    void startArtifact(Artifact artifact) throws Exception;
    void stopArtifact(Artifact artifact) throws Exception;
    boolean isArtifactInstalled(Artifact artifact);
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
