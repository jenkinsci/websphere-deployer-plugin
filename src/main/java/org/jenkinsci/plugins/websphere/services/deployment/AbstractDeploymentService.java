package org.jenkinsci.plugins.websphere.services.deployment;

import java.io.File;

/**
 * @author Greg Peters
 */
public abstract class AbstractDeploymentService implements DeploymentService {

    private File trustStoreLocation;
    private File keyStoreLocation;
    private String trustStorePassword;
    private String keyStorePassword;
    private String username;
    private String password;
    private String host;
    private String port;

    public File getTrustStoreLocation() {
        return trustStoreLocation;
    }

    public File getKeyStoreLocation() {
        return keyStoreLocation;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getHost() {
        return host;
    }

    public String getPort() {
        return port;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public void setTrustStoreLocation(File location) {
        this.trustStoreLocation = location;
    }

    public void setKeyStoreLocation(File location) {
        this.keyStoreLocation = location;
    }

    public void setTrustStorePassword(String password) {
        this.trustStorePassword = password;
    }

    public void setKeyStorePassword(String password) {
        this.keyStorePassword = password;
    }

}
