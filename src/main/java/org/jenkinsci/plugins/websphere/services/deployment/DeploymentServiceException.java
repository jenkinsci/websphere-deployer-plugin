package org.jenkinsci.plugins.websphere.services.deployment;

/**
 * @author Greg Peters
 */
public class DeploymentServiceException extends RuntimeException {

    public DeploymentServiceException(String message,Throwable t) {
        super(message,t);
    }

    public DeploymentServiceException(String message) {
        super(message);
    }
}
