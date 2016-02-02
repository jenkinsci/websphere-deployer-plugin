package org.jenkinsci.plugins.websphere.services.deployment;

import hudson.model.BuildListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.management.MalformedObjectNameException;
import javax.management.NotificationFilterSupport;
import javax.management.ObjectName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.AdminClientFactory;
import com.ibm.websphere.management.application.AppConstants;
import com.ibm.websphere.management.application.AppManagement;
import com.ibm.websphere.management.application.AppManagementProxy;
import com.ibm.websphere.management.application.AppNotification;
import com.ibm.websphere.management.application.client.AppDeploymentController;
import com.ibm.websphere.management.application.client.AppDeploymentTask;
import com.ibm.websphere.management.configservice.ConfigService;
import com.ibm.websphere.management.configservice.ConfigServiceProxy;
import com.ibm.websphere.management.exception.ConnectorException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.ibm.websphere.management.Session;
import com.ibm.websphere.management.configservice.ConfigServiceHelper;
import javax.management.Attribute;
import javax.management.AttributeList;

/**
 * @author Greg Peters
 */
public class WebSphereDeploymentService extends AbstractDeploymentService {

    public static final String CONNECTOR_TYPE_SOAP = "SOAP";
    private final static NotificationFilterSupport filterSupport;

    private final boolean isVerbose;
    private AdminClient client;
    private String connectorType;

    static {
        filterSupport = new NotificationFilterSupport();
        filterSupport.enableType(AppConstants.NotificationType);
    }

    private static NotificationFilterSupport createFilterSupport() {
        return filterSupport;
    }

    public WebSphereDeploymentService(boolean isVerbose) {
        this.isVerbose = isVerbose;
    }

    public List<Server> listServers() {
        try {
            if (!isConnected()) {
                throw new DeploymentServiceException("Cannot list servers, please connect to WebSphere first");
            }
            ObjectName jvmQuery = new ObjectName("WebSphere:*,type=Server");
            Set<ObjectName> response = client.queryNames(jvmQuery, null);
            List<Server> servers = new ArrayList<Server>();
            for (ObjectName serverObjectName : response) {
                Server server = new Server();
                server.setCellName(String.valueOf(client.getAttribute(serverObjectName, "cellName")));
                server.setNodeName(String.valueOf(client.getAttribute(serverObjectName, "nodeName")));
                server.setServerName(String.valueOf(client.getAttribute(serverObjectName, "name")));
                server.setProcessId(String.valueOf(client.getAttribute(serverObjectName, "pid")));
                server.setServerVendor(String.valueOf(client.getAttribute(serverObjectName, "serverVendor")));
                server.setServerVersion(String.valueOf(client.getAttribute(serverObjectName, "serverVersion")));
                servers.add(server);
            }
            return servers;
        } catch (Exception e) {
            throw new DeploymentServiceException(e.getMessage(), e);
        }
    }

    public void generateEAR(Artifact artifact, File destination, String earLevel) {

        byte[] buf = new byte[1024];
        try {
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

            if (artifact.getType() == Artifact.Type.TYPE_JAR) {
                out.write(getApplicationXMLForJar(artifact, earLevel).getBytes());
            } else {
                out.write(getApplicationXML(artifact, earLevel).getBytes());
            }

            out.closeEntry();
            out.close();
        } catch (Exception e) {
            throw new DeploymentServiceException(e.getMessage(), e);
        }
    }

    /*
     This method tries to read ibm-web-ext.xml and extract the value of context-root.
     If any exception is thrown, it will fall back to the WAR name.
     */
    private String getContextRoot(Artifact artifact) {
        try {
            // open WAR and find ibm-web-ext.xml
            ZipFile zipFile = new ZipFile(artifact.getSourcePath());
            ZipEntry webExt = zipFile.getEntry("WEB-INF/ibm-web-ext.xml");
            if (webExt != null) { //not an IBM based WAR
                InputStream webExtContent = zipFile.getInputStream(webExt);

                // parse ibm-web-ext.xml
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(webExtContent);

                // find uri attribute in context-root element
                Element contextRoot = (Element) doc.getElementsByTagName("context-root").item(0);
                String uri = contextRoot.getAttribute("uri");
                uri = uri.startsWith("/") ? "" : "/" + uri;
                return uri;
            }
            return getContextRootFromWarName(artifact);
        } catch (Exception e) {
            return getContextRootFromWarName(artifact);
        }
    }

    private String getContextRootFromWarName(Artifact artifact) {
        String warName = artifact.getSourcePath().getName();
        return warName.substring(0, warName.lastIndexOf("."));
    }

    private String getApplicationXML(Artifact artifact, String earLevel) {
        String contextRoot = StringUtils.isNotEmpty(artifact.getWebUri()) ? artifact.getWebUri() : getContextRoot(artifact);
        String warName = artifact.getSourcePath().getName();
        String displayName = warName.replaceAll("\\.", "_");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<application xmlns=\"http://java.sun.com/xml/ns/javaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " + getSchemaVersion(earLevel) + ">\n"
                + "  <description>" + warName + "</description>\n"
                + "  <display-name>" + displayName + "</display-name>\n"
                + "  <module>\n"
                + "    <web>\n"
                + "      <web-uri>" + warName + "</web-uri>\n"
                + "      <context-root>" + contextRoot + "</context-root>\n"
                + "    </web>\n"
                + "  </module>\n"
                + "</application>";
    }

    private String getApplicationXMLForJar(Artifact artifact, String earLevel) {
        String warName = artifact.getSourcePath().getName();
        String displayName = warName.replaceAll("\\.", "_");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<application xmlns=\"http://java.sun.com/xml/ns/javaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " + getSchemaVersion(earLevel) + ">\n"
                + "  <display-name>" + displayName + "</display-name>\n"
                + "  <module>\n"
                + "     <ejb>" + warName + "</ejb>\n"
                + "  </module>\n"
                + "</application>";
    }

    private String getSchemaVersion(String earLevel) {
        if (earLevel.equals("7")) {
            return "xsi:schemaLocation=\"http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/application_" + earLevel + ".xsd\" version=\"" + earLevel + "\"";
        } else { //EAR is EE5 or EE6
            return "xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_" + earLevel + ".xsd\" version=\"" + earLevel + "\"";
        }
    }

    public String getAppName(String path) {
        return getAppName(new File(path));
    }

    public String getAppName(File file) {
        try {
            Hashtable<String, Object> preferences = new Hashtable<String, Object>();
            preferences.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());

            preferences.put(AppConstants.APPDEPL_DFLTBNDG, new Properties());

            AppDeploymentController controller = AppDeploymentController.readArchive(file.getAbsolutePath(), preferences);

            AppDeploymentTask task = controller.getFirstTask();
            while (task != null) {
                String[][] data = task.getTaskData();
                task.setTaskData(data);
                task = controller.getNextTask();
            }
            controller.saveAndClose();

            Hashtable<String, Object> config = controller.getAppDeploymentSavedResults();
            return (String) config.get(AppConstants.APPDEPL_APPNAME);
        } catch (Exception e) {
            throw new DeploymentServiceException(e.getMessage(), e);
        }
    }

    private Hashtable<String, Object> buildDeploymentPreferences(Artifact artifact, HashMap<String, Object> options) throws Exception {
        Hashtable<String, Object> preferences = new Hashtable<String, Object>();
        preferences.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());

        Properties defaultBinding = new Properties();
        preferences.put(AppConstants.APPDEPL_DFLTBNDG, defaultBinding);
        if (options.containsKey(AppConstants.APPDEPL_DFLTBNDG_VHOST)) {
            defaultBinding.put(AppConstants.APPDEPL_DFLTBNDG_VHOST, options.get(AppConstants.APPDEPL_DFLTBNDG_VHOST));
        }

        if (options.containsKey(AppConstants.APPDEPL_CLASSLOADINGMODE)) {
            defaultBinding.put(AppConstants.APPDEPL_CLASSLOADINGMODE, options.get(AppConstants.APPDEPL_CLASSLOADINGMODE));
        }

        AppDeploymentController controller = AppDeploymentController.readArchive(artifact.getSourcePath().getAbsolutePath(), preferences);

        String[] validationResult = controller.validate();
        if (validationResult != null && validationResult.length > 0) {
            throw new DeploymentServiceException("Unable to complete all task data for deployment preparation. Reason: " + Arrays.toString(validationResult));
        }

        controller.saveAndClose();

        preferences.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());
        preferences.put(AppConstants.APPDEPL_ARCHIVE_UPLOAD, Boolean.TRUE);
        preferences.put(AppConstants.APPDEPL_PRECOMPILE_JSP, artifact.isPrecompile());

        Hashtable<String, Object> module2server = new Hashtable<String, Object>();
        module2server.put("*", artifact.getDeployTarget());
        preferences.put(AppConstants.APPDEPL_MODULE_TO_SERVER, module2server);
        return preferences;
    }

    @Override
    public void installArtifact(Artifact artifact, HashMap<String, Object> options, int deploymentTimeout, final BuildListener listener) {
        if (!isConnected()) {
            throw new DeploymentServiceException("Cannot install artifact, no connection to IBM WebSphere Application Server exists");
        }
        try {
            Hashtable<String, Object> preferences = buildDeploymentPreferences(artifact, options);
            AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(getAdminClient());

            final DeploymentNotificationListener notifyListener
                    = DeploymentNotificationListener.createListener(getAdminClient(), createFilterSupport(),
                            "Install " + artifact.getAppName(), AppNotification.INSTALL, listener, isVerbose);

            appManagementProxy.installApplication(artifact.getSourcePath().getAbsolutePath(), artifact.getAppName(), preferences, null);

            try {
                notifyListener.await(TimeUnit.MINUTES, deploymentTimeout);
            } finally {
                notifyListener.unsubscribe();
            }
            if (!notifyListener.isSuccessful()) {
                throw new DeploymentServiceException("Application not successfully deployed: " + notifyListener.getMessage());
            }
        } catch (Exception e) {
            throw new DeploymentServiceException("Failed to install artifact: " + e);
        }
    }

    @Override
    public void updateArtifact(Artifact artifact, HashMap<String, Object> options, int deploymentTimeout, BuildListener listener) {
        if (!isConnected()) {
            throw new DeploymentServiceException("Cannot update artifact, no connection to IBM WebSphere Application Server exists");
        }
        try {
            Hashtable<String, Object> preferences = buildDeploymentPreferences(artifact, options);
            AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(getAdminClient());

            final DeploymentNotificationListener notifyListener
                    = DeploymentNotificationListener.createListener(getAdminClient(), createFilterSupport(), "Update " + artifact.getAppName(),
                            AppNotification.INSTALL, listener, isVerbose);

            appManagementProxy.redeployApplication(artifact.getSourcePath().getAbsolutePath(), artifact.getAppName(), preferences, null);

            try {
                notifyListener.await(TimeUnit.MINUTES, deploymentTimeout);
            } finally {
                notifyListener.unsubscribe();
            }
            if (!notifyListener.isSuccessful()) {
                throw new DeploymentServiceException("Application not successfully updated: " + notifyListener.getMessage());
            }
        } catch (Exception e) {
            throw new DeploymentServiceException("Failed to updated artifact: " + e.toString());
        }
    }

    @Override
    public void uninstallArtifact(String appName, BuildListener listener, int deploymentTimeout) {
        try {
            final DeploymentNotificationListener notifyListener
                    = DeploymentNotificationListener.createListener(getAdminClient(), createFilterSupport(), "Uninstall " + appName,
                            AppNotification.UNINSTALL, listener, isVerbose);

            AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(getAdminClient());

            Hashtable<Object, Object> prefs = new Hashtable<Object, Object>();
            appManagementProxy.uninstallApplication(appName, prefs, null);

            try {
                notifyListener.await(TimeUnit.MINUTES, deploymentTimeout);
            } finally {
                notifyListener.unsubscribe();
            }
            if (!notifyListener.isSuccessful()) {
                throw new DeploymentServiceException("Application not successfully undeployed: " + notifyListener.getMessage());
            }
        } catch (Exception e) {
            throw new DeploymentServiceException("Could not undeploy application " + e.toString());
        }
    }

    @Override
    public boolean waitForDistribution(final String appName, final BuildListener listener, final int deploymentTimeout) throws InterruptedException, TimeoutException {
        final ExecutorService ex = Executors.newSingleThreadExecutor();
        final WebSphereDeploymentService thiz = this;
        try {
            final Future<Boolean> result = ex.submit(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return thiz.waitForDistributionCore(appName, listener, deploymentTimeout);
                }
            });
            try {
                return result.get(deploymentTimeout, TimeUnit.MINUTES);
            } catch (ExecutionException e) {
                result.cancel(true);
                return false;
            }
        } finally {
            ex.shutdown();
        }
    }

    private Boolean waitForDistributionCore(String appName, BuildListener listener, int deploymentTimeout) throws Exception {
        AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(getAdminClient());
        while (!Thread.currentThread().isInterrupted()) {
            final DeploymentNotificationListener notifyListener
                    = DeploymentNotificationListener.createListener(getAdminClient(), createFilterSupport(), null,
                            AppNotification.DISTRIBUTION_STATUS_NODE, listener, isVerbose);

            appManagementProxy.getDistributionStatus(appName, new Hashtable<Object, Object>(), null);

            try {
                notifyListener.await(TimeUnit.MINUTES, deploymentTimeout);
            } finally {
                notifyListener.unsubscribe();
            }
            if (notifyListener.isSuccessful()) {
                if (checkDistributionStatus(notifyListener.getNotificationProps(), listener).equals(AppNotification.DISTRIBUTION_DONE)) {
                    return true;
                }
            }
            Thread.currentThread().sleep(3000);
        }
        return false;
    }

    @Override
    public void startArtifact(String appName, int deploymentTimeout, BuildListener listener) {
        try {
            final AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(getAdminClient());
            final String targetsStarted = appManagementProxy.startApplication(appName, new Hashtable(), null);
            if (targetsStarted == null) {
                throw new DeploymentServiceException("Start of the application was not successful. WAS JVM logs should contain the detailed error message.");
            }
        } catch (Exception e) {
            throw new DeploymentServiceException("Could not start artifact '" + appName + "': " + e.toString());
        }
    }

    @Override
    public void stopArtifact(String name, BuildListener listener, boolean verbose) throws Exception {
        try {
            AppManagementProxy.getJMXProxyForClient(getAdminClient()).stopApplication(name, new Hashtable(), null);
        } catch (Exception e) {
            throw new DeploymentServiceException("Could not stop artifact '" + name + "': " + e.toString());
        }
    }

    @Override
    public boolean isArtifactInstalled(String name) {
        try {
            return AppManagementProxy.getJMXProxyForClient(getAdminClient()).checkIfAppExists(name, new Hashtable(), null);
        } catch (Exception e) {
            throw new DeploymentServiceException("Could not determine if artifact '" + name + "' is installed: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        try {
            return client != null && client.isAlive() != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void connect() throws Exception {
        if (isConnected()) {
            System.out.println("WARNING: Already connected to WebSphere Application Server");
        }
        System.setProperty("com.ibm.ssl.performURLHostNameVerification", "false");
        Properties config = new Properties();
        config.put(AdminClient.CONNECTOR_HOST, getHost());
        config.put(AdminClient.CONNECTOR_PORT, getPort());
        if (StringUtils.trimToNull(getUsername()) != null) {
            injectSecurityConfiguration(config);
        }
        config.put(AdminClient.CONNECTOR_TYPE, getConnectorType());
        client = AdminClientFactory.createAdminClient(config);
        if (client == null) {
            throw new DeploymentServiceException("Unable to connect to IBM WebSphere Application Server @ " + getHost() + ":" + getPort());
        }
    }

    @Override
    public void disconnect() {
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.keyStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
        System.clearProperty("javax.net.ssl.keyStorePassword");
        System.clearProperty("com.ibm.ssl.trustStore");
        System.clearProperty("com.ibm.ssl.keyStore");
        System.clearProperty("com.ibm.ssl.trustStorePassword");
        System.clearProperty("com.ibm.ssl.keyStorePassword");
        System.clearProperty("com.ibm.ssl.performURLHostNameVerification");
        if (client != null) {
            client.getConnectorProperties().clear();
            client = null;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("com.ibm.websphere.management.AdminClientFactory", false, getClass().getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private AdminClient getAdminClient() throws ConnectorException {
        if (client == null) {
            throw new DeploymentServiceException("No connection to WebSphere exists");
        }
        return client;
    }

    @Override
    public void additionalAttributes(Artifact artifact) throws Exception {

        final Session session = new Session(); // separeted session
        final ConfigService configService = new ConfigServiceProxy(getAdminClient());

        final ObjectName rootID = configService.resolve(session, "Deployment=" + artifact.getAppName())[0];
        final AttributeList depObjAttrLst = configService.getAttributes(session, rootID, new String[]{"deployedObject"}, false);
        final ObjectName appDeplID = (ObjectName) ConfigServiceHelper.getAttributeValue(depObjAttrLst, "deployedObject");

        // Locate the class loader.
        // Change the starting weight through the startingWeight attribute. The starting weight 
        // affects the order in which applications start.
        final AttributeList attrList = new AttributeList();

        if (artifact.getStartupOrder() != null) {
            attrList.add(new Attribute("startingWeight", artifact.getStartupOrder())); // "Startup order" 
        }

        // Change the WAR class loader policy through the warClassLoaderPolicy attribute by 
        // specifying SINGLE or MULTIPLE.
        // SINGLE=one classloader for all WAR modules
        if (artifact.getWarClassLoaderPolicy() != WarClassLoaderPolicy.DEFAULT) {
            attrList.add(new Attribute("warClassLoaderPolicy", artifact.getWarClassLoaderPolicy().getAttrName())); // "WAR class loader policy"
        }

        // Set the class loader mode to PARENT_FIRST or PARENT_LAST.
        if (artifact.getClassLoadOrder() != ClassLoadOrder.DEFAULT) {
            final AttributeList clList = (AttributeList) configService.getAttribute(session, appDeplID, "classloader");
            ConfigServiceHelper.setAttributeValue(clList, "mode", artifact.getClassLoadOrder().getAttrName()); // "Class loader order"
            attrList.add(new Attribute("classloader", clList));
        }

        // Set the new values.
        configService.setAttributes(session, appDeplID, attrList);

        final AttributeList modulesAttrLst = configService.getAttributes(session, appDeplID, new String[]{"modules"}, false);
        final ObjectName appWebDepId = (ObjectName) ((List) ConfigServiceHelper.getAttributeValue(modulesAttrLst, "modules")).get(0);
        final AttributeList attrListWeb = new AttributeList();

        if (artifact.getStartingWeightWeb() != null) {
            attrListWeb.add(new Attribute("startingWeight", artifact.getStartingWeightWeb()));// "Web Starting weight"              
        }
        if (artifact.getClassLoadOrderWeb() != ClassLoadOrder.DEFAULT) {
            attrListWeb.add(new Attribute("classloaderMode", artifact.getClassLoadOrderWeb().getAttrName()));// "Class loader order" 
        }

        // Set the new values.
        configService.setAttributes(session, appWebDepId, attrListWeb);

        // Save your changes.
        configService.save(session, false);
    }

    private void injectSecurityConfiguration(Properties config) {
        config.put(AdminClient.CACHE_DISABLED, "true");
        config.put(AdminClient.CONNECTOR_SECURITY_ENABLED, "true");
        config.put(AdminClient.USERNAME, getUsername());
        config.put(AdminClient.PASSWORD, getPassword());

        config.put("com.ibm.ssl.trustStore", getTrustStoreLocation().getAbsolutePath());
        config.put("javax.net.ssl.trustStore", getTrustStoreLocation().getAbsolutePath());

        config.put("com.ibm.ssl.keyStore", getKeyStoreLocation().getAbsolutePath());
        config.put("javax.net.ssl.keyStore", getKeyStoreLocation().getAbsolutePath());

        config.put("com.ibm.ssl.trustStorePassword", getTrustStorePassword());
        config.put("javax.net.ssl.trustStorePassword", getTrustStorePassword());

        config.put("com.ibm.ssl.keyStorePassword", getKeyStorePassword());
        config.put("javax.net.ssl.keyStorePassword", getKeyStorePassword());
    }

    public void setConnectorType(String type) {
        this.connectorType = type;
    }

    public String getConnectorType() {
        return this.connectorType;
    }

    private String checkDistributionStatus(Properties prop, BuildListener listener) throws MalformedObjectNameException, NullPointerException, IllegalStateException {
        String result = AppNotification.DISTRIBUTION_NOT_DONE;
        String compositeStatus = prop.getProperty(AppNotification.DISTRIBUTION_STATUS_COMPOSITE);
        listener.getLogger().println("Composite status of distribution: " + compositeStatus);

        if (compositeStatus == null || compositeStatus.isEmpty()) {
            return result;
        }

        boolean distrDoneFlag = false;
        String[] nodeStatus = compositeStatus.split("\\+");
        for (String s : nodeStatus) {
            ObjectName o = new ObjectName(s);
            if (o.getKeyProperty("distribution").equalsIgnoreCase("false")) {
                return result;
            } else if (o.getKeyProperty("distribution").equalsIgnoreCase("true")) {
                distrDoneFlag = true;
            }
        }
        if (distrDoneFlag) {
            result = AppNotification.DISTRIBUTION_DONE;
        }

        return result;
    }
}
