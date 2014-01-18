package org.jenkinsci.plugins.websphere.services.deployment;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.AdminClientFactory;
import com.ibm.websphere.management.application.AppConstants;
import com.ibm.websphere.management.application.AppManagementProxy;
import com.ibm.websphere.management.application.AppNotification;
import com.ibm.websphere.management.application.client.AppDeploymentController;
import com.ibm.websphere.management.application.client.AppDeploymentTask;
import com.ibm.websphere.management.exception.ConnectorException;
import org.apache.commons.lang.StringUtils;

import javax.management.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Greg Peters
 */
public class WebSphereDeploymentService extends AbstractDeploymentService {

    public static final String CONNECTOR_TYPE_SOAP = "SOAP";

    private AdminClient client;
    private String connectorType;
    private String targetCluster;
    private String targetServer;
    private String targetNode;
    private String targetCell;

    public List<Server> listServers() {
        try {
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
        } catch(Exception e) {
            e.printStackTrace();
            throw new DeploymentServiceException(e.getMessage(),e);
        }
    }

    public void generateEAR(Artifact artifact, File destination,String earLevel) {

            byte[] buf = new byte[1024];
            try {
                String warName = artifact.getSourcePath().getName();
                String context = warName.substring(0,warName.lastIndexOf("."));
                ZipOutputStream out = new ZipOutputStream(new FileOutputStream(destination));
                FileInputStream in = new FileInputStream(artifact.getSourcePath());
                out.putNextEntry(new ZipEntry(artifact.getSourcePath().getName()));
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.closeEntry();
                in.close();
                out.putNextEntry(new ZipEntry("META-INF/"));
                out.closeEntry();
                out.putNextEntry(new ZipEntry("META-INF/application.xml"));
                out.write(getApplicationXML(context,earLevel).getBytes());
                out.closeEntry();
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    private String getApplicationXML(String warName,String earLevel) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<application xmlns=\"http://java.sun.com/xml/ns/javaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_"+earLevel+".xsd\" version=\""+earLevel+"\">\n" +
                                "  <description>"+warName+"</description>\n" +
                                "  <display-name>"+warName+"</display-name>\n" +
                                "  <module>\n" +
                                "    <web>\n" +
                                "      <web-uri>"+warName+".war</web-uri>\n" +
                                "      <context-root>/"+warName+"</context-root>\n" +
                                "    </web>\n" +
                                "  </module>\n" +
                                "</application>";
    }

    public String getAppName(String path) {
        return getAppName(new File(path));
    }

    public String getAppName(File file) {
        try {
            Hashtable<String,Object> preferences = new Hashtable<String,Object>();
            preferences.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());

            preferences.put(AppConstants.APPDEPL_DFLTBNDG, new Properties());

            AppDeploymentController controller = AppDeploymentController.readArchive(file.getAbsolutePath(),preferences);

            AppDeploymentTask task = controller.getFirstTask();
            while (task != null) {
                String[][] data = task.getTaskData();
                task.setTaskData(data);
                task = controller.getNextTask();
            }
            controller.saveAndClose();

            Hashtable<String,Object> config = controller.getAppDeploymentSavedResults();
            return (String)config.get(AppConstants.APPDEPL_APPNAME);
        } catch(Exception e) {
            e.printStackTrace();
            throw new DeploymentServiceException(e.getMessage(),e);
        }
    }

    public void installArtifact(Artifact artifact,HashMap<String,Object> options) {
        if(!isConnected()) {
            throw new DeploymentServiceException("Cannot install artifact, no connection to WebSphere Application Server exists");
        }
        try {
            Hashtable<String,Object> preferences = new Hashtable<String,Object>();
            preferences.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());

            Properties defaultBinding = new Properties();
            preferences.put(AppConstants.APPDEPL_DFLTBNDG, defaultBinding);
            if(options.containsKey(AppConstants.APPDEPL_DFLTBNDG_VHOST)) {
                defaultBinding.put(AppConstants.APPDEPL_DFLTBNDG_VHOST, options.get(AppConstants.APPDEPL_DFLTBNDG_VHOST));
            }

            AppDeploymentController controller = AppDeploymentController.readArchive(artifact.getSourcePath().getAbsolutePath(), preferences);

            AppDeploymentTask task = controller.getFirstTask();
            while (task != null) {
                String[][] data = task.getTaskData();
                task.setTaskData(data);
                task = controller.getNextTask();
            }
            controller.saveAndClose();

            Hashtable<String,Object> config = controller.getAppDeploymentSavedResults();

            artifact.setAppName((String)config.get(AppConstants.APPDEPL_APPNAME));
            config.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());
            config.put(AppConstants.APPDEPL_ARCHIVE_UPLOAD, Boolean.TRUE);
            config.put(AppConstants.APPDEPL_PRECOMPILE_JSP, artifact.isPrecompile());

            Hashtable<String,Object> module2server = new Hashtable<String,Object>();
            module2server.put("*", getTarget());
            config.put(AppConstants.APPDEPL_MODULE_TO_SERVER, module2server);

            InstallationListener listener = createInstallationListener();
            getAdminClient().addNotificationListener(listener.getAppManagement(), listener, listener.getFilter(), "");
            AppManagementProxy.getJMXProxyForClient(client).installApplication(artifact.getSourcePath().getAbsolutePath(), config, null);
            waitForInstallThread();
        } catch (Exception e) {
            e.printStackTrace();
            throw new DeploymentServiceException("Failed to install artifact: "+e.getMessage());
        }
    }

    public void uninstallArtifact(String name) throws Exception {
        InstallationListener listener = createInstallationListener();

        getAdminClient().addNotificationListener(listener.getAppManagement(), listener, listener.getFilter(), "");

        AppManagementProxy.getJMXProxyForClient(getAdminClient()).uninstallApplication(name, new Hashtable(), null);

        waitForInstallThread();
    }

    public void startArtifact(String name) throws Exception {
        try {
            AppManagementProxy.getJMXProxyForClient(getAdminClient()).startApplication(name, new Hashtable(), null);
        } catch(Exception e) {
            e.printStackTrace();
            throw new DeploymentServiceException("Could not start artifact '"+name+"': "+e.getMessage());
        }
    }

    public void stopArtifact(String name) throws Exception {
        try {
            AppManagementProxy.getJMXProxyForClient(getAdminClient()).stopApplication(name, new Hashtable(), null);
        } catch(Exception e) {
            e.printStackTrace();
            throw new DeploymentServiceException("Could not stop artifact '"+name+"': "+e.getMessage());
        }
    }

    public boolean isArtifactInstalled(String name) {
        try {
            return AppManagementProxy.getJMXProxyForClient(getAdminClient()).checkIfAppExists(name, new Hashtable(), null);
        } catch(Exception e) {
            e.printStackTrace();
            throw new DeploymentServiceException("Could not determine if artifact '"+name+"' is installed: "+e.getMessage());
        }
    }

    private InstallationListener createInstallationListener() throws Exception {
        NotificationFilterSupport filter = new NotificationFilterSupport();
        filter.enableType(AppConstants.NotificationType);
        return new InstallationListener(getAdminClient(),getAppManagementObject(),filter);
    }

    public boolean isConnected() {
        try {
            return client != null && client.isAlive() != null;
        } catch(Exception e) {
            return false;
        }
    }

    public void connect() throws Exception {
        if(isConnected()) {
            System.out.println("WARNING: Already connected to WebSphere Application Server");
        }
        Properties config = new Properties();
        config.put (AdminClient.CONNECTOR_HOST, getHost());
        config.put (AdminClient.CONNECTOR_PORT, getPort());
        if(StringUtils.trimToNull(getUsername()) != null) {
            injectSecurityConfiguration(config);
        }
        config.put(AdminClient.AUTH_TARGET, getTarget());
        config.put (AdminClient.CONNECTOR_TYPE, getConnectorType());
        client = AdminClientFactory.createAdminClient(config);
        if(client == null) {
            throw new DeploymentServiceException("Unable to connect to IBM WebSphere Application Server @ "+getHost()+":"+getPort());
        }
    }

    public void disconnect() {
        client = null;
    }

    public boolean isAvailable() {
        try {
            Class.forName("com.ibm.websphere.management.AdminClientFactory", false, getClass().getClassLoader());
            return true;
        } catch(Throwable e) {
            return false;
        }
    }

    private ObjectName getAppManagementObject() throws MalformedObjectNameException, ConnectorException {
        //only one app management object exists for WebSphere so return the first one
        Iterator iterator = getAdminClient().queryNames(new ObjectName("WebSphere:type=AppManagement,*"), null).iterator();
        return (ObjectName)iterator.next();
    }

    private AdminClient getAdminClient() throws ConnectorException {
        if(client == null) {
            throw new DeploymentServiceException("No connection to WebSphere exists");
        }
        return client;
    }

    private void injectSecurityConfiguration(Properties config) {
        config.put(AdminClient.CACHE_DISABLED, "false");
        config.put(AdminClient.CONNECTOR_SECURITY_ENABLED, "true");
        config.put(AdminClient.USERNAME, getUsername());
        config.put(AdminClient.PASSWORD, getPassword());

        config.put("com.ibm.ssl.trustStore", getTrustStoreLocation().getAbsolutePath());
        config.put("javax.net.ssl.trustStore", getTrustStoreLocation().getAbsolutePath());

        config.put("com.ibm.ssl.keyStore", getKeyStoreLocation().getAbsolutePath());
        config.put("javax.net.ssl.keyStore", getKeyStoreLocation().getAbsolutePath());

        config.put("com.ibm.ssl.trustStorePassword", getTrustStorePassword());
        config.put("javax.net.ssl.trustStorePassword",getTrustStorePassword());

        config.put("com.ibm.ssl.keyStorePassword", getKeyStorePassword());
        config.put("javax.net.ssl.keyStorePassword", getKeyStorePassword());
    }

    private String getTarget() {
        StringBuilder builder = new StringBuilder();
        builder.append("WebSphere:");
        appendTarget(builder,"cluster=",getTargetCluster());
        appendTarget(builder,"cell=",getTargetCell());
        appendTarget(builder,"node=",getTargetNode());
        appendTarget(builder,"server=",getTargetServer());
        return builder.toString();
    }

    private void appendTarget(StringBuilder builder,String target,String value) {
        if(StringUtils.trimToNull(value) != null) {
            if(!isFirstTarget(builder)) {
                builder.append(",");
            }
            builder.append(target);
            builder.append(value);
        }
    }

    private boolean isFirstTarget(StringBuilder builder) {
        return builder.toString().endsWith(":");
    }

    public void setConnectorType(String type) {
        this.connectorType = type;
    }

    public String getConnectorType() {
        return this.connectorType;
    }

    public String getTargetCluster() {
        return targetCluster;
    }

    public void setTargetCluster(String targetCluster) {
        this.targetCluster = targetCluster;
    }

    public String getTargetServer() {
        return targetServer;
    }

    public void setTargetServer(String targetServer) {
        this.targetServer = targetServer;
    }

    public String getTargetNode() {
        return targetNode;
    }

    public void setTargetNode(String targetNode) {
        this.targetNode = targetNode;
    }

    public String getTargetCell() {
        return targetCell;
    }

    public void setTargetCell(String targetCell) {
        this.targetCell = targetCell;
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
        private NotificationFilter filter;

        public InstallationListener(AdminClient adminClient,ObjectName appManagement,NotificationFilter filter) {
            this.client = adminClient;
            this.appManagement = appManagement;
            this.filter = filter;
        }

        public synchronized void handleNotification(Notification notification, Object handback) {
            AppNotification ev = (AppNotification) notification.getUserData();

            if (ev.taskName.equals (AppNotification.INSTALL) && (ev.taskStatus.equals (AppNotification.STATUS_COMPLETED) || ev.taskStatus.equals (AppNotification.STATUS_FAILED))) {
                try {
                        client.removeNotificationListener(appManagement, this);
                } catch (Throwable th) {
                    System.err.println ("Error removing install listener: " + th);
                }
                notifyInstallThread();
            }

            if (ev.taskName.equals (AppNotification.UNINSTALL) && (ev.taskStatus.equals (AppNotification.STATUS_COMPLETED) || ev.taskStatus.equals (AppNotification.STATUS_FAILED))) {
                try {
                        client.removeNotificationListener(appManagement, this);
                } catch (Throwable th) {
                    System.err.println ("Error removing uninstall listener: " + th);
                }
                notifyInstallThread();
            }
        }

        public NotificationFilter getFilter() {
            return filter;
        }

        public AdminClient getClient() {
            return client;
        }

        public ObjectName getAppManagement() {
            return appManagement;
        }
    }
}
