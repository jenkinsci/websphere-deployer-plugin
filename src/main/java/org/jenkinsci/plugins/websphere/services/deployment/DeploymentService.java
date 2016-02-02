package org.jenkinsci.plugins.websphere.services.deployment;

import hudson.model.BuildListener;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;

public interface DeploymentService {

    void installArtifact(Artifact artifact, HashMap<String, Object> options, int deploymentTimeout, BuildListener listener);

    void updateArtifact(Artifact artifact, HashMap<String, Object> options, int deploymentTimeout, BuildListener listener);

    void uninstallArtifact(String appName, BuildListener listener, int deploymentTimeout);

    /**
     *
     * @param appName
     * @param listener
     * @param deploymentTimeout
     * @return true if distribution is successesfully and false otherwise
     * @throws InterruptedException if the current thread was interrupted while
     * waiting
     * @throws TimeoutException if the wait timed out
     */
    boolean waitForDistribution(String appName, BuildListener listener, int deploymentTimeout) throws InterruptedException, TimeoutException;

    void startArtifact(String name, int deploymentTimeout, BuildListener listener);

    void stopArtifact(String name, BuildListener listener, boolean verbose) throws Exception;

    boolean isArtifactInstalled(String name);

    void additionalAttributes(Artifact app) throws Exception;    
    
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
