package org.jenkinsci.plugins.websphere_deployer.services;

/**
 * A WebSphere administration exception.
 *
 * @author Greg Peters
 */
public class WebSphereAdminServiceException extends RuntimeException {

    public WebSphereAdminServiceException(String s) {
        super(s);
    }
}
