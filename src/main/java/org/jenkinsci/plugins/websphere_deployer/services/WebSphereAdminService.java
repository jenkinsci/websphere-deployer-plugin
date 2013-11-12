package org.jenkinsci.plugins.websphere_deployer.services;


import com.ibm.ws.management.application.client.ListModules;

import java.util.List;

public interface WebSphereAdminService {

    boolean isConnected();
    void connect(Endpoint endpoint);
    void installApplication(Deployable deployable);
    void startApplication(String appName) throws Exception;
    void stopApplication(String appName) throws Exception;
    boolean isInstalled(String appName) throws Exception;
    List<J2EEApplication> listApplications() throws Exception;
    J2EEApplication getApplication(String appName) throws Exception;
    ListModules listModules(String appName) throws Exception;
    List listURIs(String appName) throws Exception;
    List<Server> listServers() throws Exception;
    List<JVM> listJVMs() throws Exception;
    void uninstallApplication(String appName) throws Exception;
}
