package org.jenkinsci.plugins.websphere_deployer.services;


/**
 * A deployable WebSphere artifact.
 *
 * @author Greg Peters
 */
public class Deployable extends Endpoint {

    private String earPath;
    private String appName;
    private String virtualHost;
    private boolean precompileJSPs;
    private boolean servletReloadingEnabled;

    public Deployable() {
        this.virtualHost = "default_host";
    }

    public boolean isPrecompileJSPs() {
        return precompileJSPs;
    }

    public void setPrecompileJSPs(boolean precompileJSPs) {
        this.precompileJSPs = precompileJSPs;
    }

    public boolean isServletReloadingEnabled() {
        return servletReloadingEnabled;
    }

    public void setServletReloadingEnabled(boolean servletReloadingEnabled) {
        this.servletReloadingEnabled = servletReloadingEnabled;
    }

    public String getVirtualHost() {
        return virtualHost;
    }

    public void setVirtualHost(String virtualHost) {
        this.virtualHost = virtualHost;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getEarPath() {
        return earPath;
    }

    public void setEarPath(String earPath) {
        this.earPath = earPath;
    }

}
