package org.jenkinsci.plugins.websphere_deployer.services;


import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.AdminClientFactory;
import com.ibm.websphere.management.application.AppConstants;
import com.ibm.websphere.management.application.AppManagement;
import com.ibm.websphere.management.application.AppManagementProxy;
import com.ibm.websphere.management.application.AppNotification;
import com.ibm.websphere.management.application.client.AppDeploymentController;
import com.ibm.websphere.management.application.client.AppDeploymentTask;
import com.ibm.websphere.management.exception.ConnectorException;
import com.ibm.ws.management.application.client.ListModules;

import javax.management.*;
import java.util.*;

/**
 * A WebSphere admin service implementation for various application level services.
 *
 * @author Greg Peters
 */
public class WebSphereAdminServiceImpl implements WebSphereAdminService {

    private AdminClient client;

    public WebSphereAdminServiceImpl() {
        System.setProperty("was.install.root","/opt/IBM/WebSphere/AppServer");
    }

    private AdminClient getAdminClient(Endpoint endpoint) throws ConnectorException {
        Properties config = new Properties();
        config.put (AdminClient.CONNECTOR_HOST,  endpoint.getHost());
        config.put (AdminClient.CONNECTOR_PORT,  endpoint.getPort());
        if(endpoint.getUsername() != null && !endpoint.getUsername().trim().equals("")) {
            config.put(AdminClient.CACHE_DISABLED, "false");
            config.put(AdminClient.CONNECTOR_SECURITY_ENABLED, "true");
            config.put(AdminClient.USERNAME, endpoint.getUsername());
            config.put(AdminClient.PASSWORD, endpoint.getPassword());

            config.put("com.ibm.ssl.trustStore",endpoint.getClientTrustFile());
            config.put("javax.net.ssl.trustStore", endpoint.getClientTrustFile());

            config.put("com.ibm.ssl.keyStore",endpoint.getClientKeyFile());
            config.put("javax.net.ssl.keyStore", endpoint.getClientKeyFile());

            config.put("com.ibm.ssl.trustStorePassword",endpoint.getClientTrustPassword());
            config.put("javax.net.ssl.trustStorePassword",endpoint.getClientTrustPassword());

            config.put("com.ibm.ssl.keyStorePassword",endpoint.getClientKeyPassword());
            config.put("javax.net.ssl.keyStorePassword",endpoint.getClientKeyPassword());
        }
        config.put (AdminClient.AUTH_TARGET, endpoint.getTarget());
        config.put (AdminClient.CONNECTOR_TYPE, endpoint.getConnectionType());
        return AdminClientFactory.createAdminClient(config);
    }

    public void connect(Endpoint endpoint) {
        if(client != null) {
            throw new WebSphereAdminServiceException("Cannot connect, client is already connected");
        } else {
            try {
                client = getAdminClient(endpoint);
            } catch (ConnectorException e) {
                e.printStackTrace();
                throw new WebSphereAdminServiceException(e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        try {
            return client != null && client.isAlive() != null;
        } catch(Exception e) {
            return false;
        }
    }

    
    public void installApplication(Deployable deployable) {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot install application, client not connected");
        }
        try {
               Hashtable prefs = new Hashtable();
               prefs.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());

               Properties defaultBnd = new Properties();
               prefs.put (AppConstants.APPDEPL_DFLTBNDG, defaultBnd);
               defaultBnd.put (AppConstants.APPDEPL_DFLTBNDG_VHOST, deployable.getVirtualHost());

               AppDeploymentController controller = AppDeploymentController.readArchive(deployable.getEarPath(), prefs);
               AppDeploymentTask task = controller.getFirstTask();
               while (task != null) {
                    String[][] data = task.getTaskData();
                    task.setTaskData(data);
                    task = controller.getNextTask();
               }
               controller.saveAndClose();

               Hashtable options = controller.getAppDeploymentSavedResults();

               AppManagement proxy = AppManagementProxy.getJMXProxyForClient(client);

               options.put (AppConstants.APPDEPL_LOCALE, Locale.getDefault());
               options.put (AppConstants.APPDEPL_ARCHIVE_UPLOAD, Boolean.TRUE);
               options.put (AppConstants.APPDEPL_PRECOMPILE_JSP, deployable.isPrecompileJSPs());
               options.put (AppConstants.APPDEPL_RELOADENABLED, deployable.isServletReloadingEnabled());

               Hashtable module2server = new Hashtable();
               module2server.put ("*", deployable.getTarget());
               options.put (AppConstants.APPDEPL_MODULE_TO_SERVER, module2server);

               NotificationFilterSupport filter = new NotificationFilterSupport();
               filter.enableType (AppConstants.NotificationType);
               filter.enableType (AppConstants.J2EEConfigType);

               InstallationListener installationListener = new InstallationListener(client,getAppManagementObject());

               client.addNotificationListener(installationListener.getAppManagement(),installationListener,filter,deployable);

               proxy.installApplication (deployable.getEarPath(), options, null);

               waitForInstallThread();
         }
         catch (Exception e) {
             e.printStackTrace();

         }
    }

    
    public void startApplication(String appName) throws Exception {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot start application, client is not connected");
        }
        AppManagement proxy = AppManagementProxy.getJMXProxyForClient(client);
        proxy.startApplication(appName, new Hashtable(), null);
    }

    
    public void stopApplication(String appName) throws Exception {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot stop application, client is not connected");
        }
        AppManagement proxy = AppManagementProxy.getJMXProxyForClient(client);
        proxy.stopApplication(appName, new Hashtable(), null);
    }

    
    public boolean isInstalled(String appName) throws Exception {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot determine if application is installed, client is not connected");
        }
        AppManagement proxy = AppManagementProxy.getJMXProxyForClient(client);
        return proxy.checkIfAppExists(appName, new Hashtable(), null);
    }

    
    public List<J2EEApplication> listApplications() throws Exception {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot list applications, client is not connected");
        }
        ObjectName appQuery = new ObjectName("WebSphere:*,type=J2EEApplication");
        Set<ObjectName> response = client.queryNames(appQuery,null);
        List<J2EEApplication> applications = new ArrayList<J2EEApplication>();
        for(ObjectName appObject:response) {
            J2EEApplication app = new J2EEApplication();
            app.setName(appObject.getKeyProperty("J2EEName"));
            app.setState((Integer)client.getAttribute(appObject,"state"));
            app.setStartTime(new Date((Long) client.getAttribute(appObject, "startTime")));
            app.setDeploymentDescriptor(String.valueOf(client.getAttribute(appObject, "deploymentDescriptor")));
            applications.add(app);
        }
        return applications;
    }

    
    public J2EEApplication getApplication(String appName) throws Exception {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot get application, client is not connected");
        }
        ObjectName appQuery = new ObjectName("WebSphere:*,type=J2EEApplication");
        Set<ObjectName> response = client.queryNames(appQuery,null);
        for(ObjectName appObject:response) {
            if(appObject.getKeyProperty("J2EEName").equalsIgnoreCase(appName)) {
                J2EEApplication app = new J2EEApplication();
                app.setName(appObject.getKeyProperty("J2EEName"));
                app.setState((Integer)client.getAttribute(appObject,"state"));
                app.setStartTime(new Date((Long)client.getAttribute(appObject,"startTime")));
                app.setDeploymentDescriptor(String.valueOf(client.getAttribute(appObject,"deploymentDescriptor")));
                return app;
            }
        }
        return null;
    }

    
    public ListModules listModules(String appName) throws Exception {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot start application, client is not connected");
        }
        AppManagement proxy = AppManagementProxy.getJMXProxyForClient(client);
        return (ListModules)proxy.listModules(appName,new Hashtable(), null);
    }

    
    public List listURIs(String appName) throws Exception {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot list application URIs, client is not connected");
        }
        AppManagement proxy = AppManagementProxy.getJMXProxyForClient(client);
        return proxy.listURIs(appName,null,new Hashtable(), null);
    }

    
    public List<Server> listServers() throws Exception {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot list servers, client is not connected");
        }
        ObjectName jvmQuery = new ObjectName("WebSphere:*,type=Server");
        Set<ObjectName> response = client.queryNames(jvmQuery,null);
        List<Server> servers = new ArrayList<Server>();
        for(ObjectName serverObjectName:response) {
            Server server = new Server();
            server.setCellName(String.valueOf(client.getAttribute(serverObjectName,"cellName")));
            server.setNodeName(String.valueOf(client.getAttribute(serverObjectName,"nodeName")));
            server.setServerName(String.valueOf(client.getAttribute(serverObjectName, "name")));
            server.setProcessId(String.valueOf(client.getAttribute(serverObjectName, "pid")));
            server.setServerVendor(String.valueOf(client.getAttribute(serverObjectName, "serverVendor")));
            server.setServerVersion(String.valueOf(client.getAttribute(serverObjectName, "serverVersion")));
            servers.add(server);
        }
        return servers;
    }

    
    public List<JVM> listJVMs() throws Exception {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot list JVMs, client is not connected");
        }
        ObjectName jvmQuery = new ObjectName("WebSphere:*,type=JVM");
        Set<ObjectName> response = client.queryNames(jvmQuery,null);
        List<JVM> results = new ArrayList<JVM>();
        for(ObjectName jvmObjectName:response) {
            JVM jvm = new JVM();
            jvm.setVendor(String.valueOf(client.getAttribute(jvmObjectName,"javaVendor")));
            jvm.setVersion(String.valueOf(client.getAttribute(jvmObjectName,"javaVersion")));
            jvm.setNode(String.valueOf(client.getAttribute(jvmObjectName,"node")));
            jvm.setHeapSize(String.valueOf(client.getAttribute(jvmObjectName,"heapSize")));
            jvm.setFreeMemory(String.valueOf(client.getAttribute(jvmObjectName,"freeMemory")));
            jvm.setMaxHeapDumpsOnDisk(String.valueOf(client.getAttribute(jvmObjectName, "maxHeapDumpsOnDisk")));
            jvm.setMaxMemory(String.valueOf(client.getAttribute(jvmObjectName,"maxMemory")));
            results.add(jvm);
        }
        return results;
    }

    
    public void uninstallApplication(String appName) throws Exception {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot uninstall application, client is not connected");
        }
        NotificationFilterSupport filter = new NotificationFilterSupport();
        filter.enableType (AppConstants.NotificationType);

        InstallationListener installationListener = new InstallationListener(client,getAppManagementObject());

        //Add the listener.
        //NotificationListener listener = new WebSphereInstallationListener(client, myFilter, "Installing", AppNotification.INSTALL);
        client.addNotificationListener(installationListener.getAppManagement(),installationListener,filter,"");

        AppManagement proxy = AppManagementProxy.getJMXProxyForClient(client);
        proxy.uninstallApplication(appName,new Hashtable(),null);

        waitForInstallThread();
    }

    private ObjectName getAppManagementObject() throws MalformedObjectNameException, ConnectorException {
        if(!isConnected()) {
            throw new WebSphereAdminServiceException("Cannot get app management object, client is not connected");
        }
        Iterator iterator = client.queryNames(new ObjectName("WebSphere:type=AppManagement,*"), null).iterator();
        return (ObjectName)iterator.next();
    }

    private void waitForInstallThread() {
        synchronized (this) {
            try {
                wait();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void notifyInstallThread() {
        synchronized (this) {
            notify();
        }
    }

    class InstallationListener implements NotificationListener {

        private AdminClient client;
        private ObjectName appManagement;

        public InstallationListener(AdminClient adminClient,ObjectName appManagement) {
            this.client = adminClient;
            this.appManagement = appManagement;
        }

        
        public synchronized void handleNotification(Notification notification, Object handback) {
            AppNotification ev = (AppNotification) notification.getUserData();

            if (ev.taskName.equals (AppNotification.INSTALL) && (ev.taskStatus.equals (AppNotification.STATUS_COMPLETED) || ev.taskStatus.equals (AppNotification.STATUS_FAILED))) {
                try {
                        client.removeNotificationListener(appManagement, this);
                } catch (Throwable th) {
                    System.out.println ("Error removing install listener: " + th);
                }
                notifyInstallThread();
            }

            if (ev.taskName.equals (AppNotification.UNINSTALL) && (ev.taskStatus.equals (AppNotification.STATUS_COMPLETED) || ev.taskStatus.equals (AppNotification.STATUS_FAILED))) {
                try {
                        client.removeNotificationListener(appManagement, this);
                } catch (Throwable th) {
                    System.out.println ("Error removing uninstall listener: " + th);
                }
                notifyInstallThread();
            }
        }

        public AdminClient getClient() {
            return client;
        }

        public ObjectName getAppManagement() {
            return appManagement;
        }
    }
}
