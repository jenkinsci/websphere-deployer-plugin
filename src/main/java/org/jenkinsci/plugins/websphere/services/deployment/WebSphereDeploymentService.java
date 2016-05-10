package org.jenkinsci.plugins.websphere.services.deployment;

import hudson.model.BuildListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.enterprise.deploy.spi.Target;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationFilterSupport;
import javax.management.ObjectName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang.ArrayUtils;
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
import com.ibm.websphere.management.exception.ConnectorException;

/**
 * @author Greg Peters
 */
public class WebSphereDeploymentService extends AbstractDeploymentService {

    public static final String CONNECTOR_TYPE_SOAP = "SOAP";
    private static final String className = WebSphereDeploymentService.class.getName();
    private static Logger log = Logger.getLogger(className);

    private AdminClient client;
    private String connectorType;
    private boolean verbose;
    private BuildListener buildListener;
    /**
     * This is used to prevent weird behaviors caused by IBM wsadmin that overrides
     * system properties.
     *
     * @see <a href="https://github.com/jenkinsci/websphere-deployer-plugin/pull/11">
     *   GitHub discussion</a> for a reference.
     */
    private Properties storedProperties;

    public List<Server> listServers() {
        try {
        	if(!isConnected()) {
        		throw new DeploymentServiceException("Cannot list servers, please connect to WebSphere first");
        	}
            ObjectName targetQuery = new ObjectName("WebSphere:*,type=J2EEAppDeployment");
            Set<ObjectName> appDeployments = client.queryNames(targetQuery, null);
            List<Server> servers = new ArrayList<Server>();
            for(ObjectName appDeployment:appDeployments) {
            	//reference: http://www-01.ibm.com/support/knowledgecenter/SSEQTP_8.5.5/com.ibm.websphere.wlp.doc/ae/rwlp_mbeans_operation.html?cp=SSEQTP_8.5.5%2F1-3-11-0-3-2-14-2-1
            	Target[] targets = (Target[])client.invoke(appDeployment, "getTargets", new Object[]{
            			null,
            			null
            	}, new String[] {
            			Hashtable.class.getName(),
            			String.class.getName()
            	});
            	for(Target target:targets) {
            		if(target.getName().contains("J2EEServer")) { //only J2EE servers can be deployed to
            			Server server = new Server();
            			server.setObjectName(target.getName());
            			server.setTarget(getFormattedTarget(target.getName()));
            			servers.add(server);
            		}
            	}
            	Collections.sort(servers);
            	int i=0;
            	for(Server server:servers) {
            		server.setIndex(i++); //set index after sort
            	}
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
            out.write(getApplicationXML(artifact,earLevel).getBytes());
            out.closeEntry();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
    This method tries to read ibm-web-ext.xml and extract the value of context-root.
    If any exception is thrown, it will fall back to the WAR name.
     */
    private String getContextRoot(Artifact artifact) {
        try {
        	if(artifact.getContext() != null) {
        		return artifact.getContext();
        	}
            // open WAR and find ibm-web-ext.xml
            ZipFile zipFile = new ZipFile(artifact.getSourcePath());
            ZipEntry webExt = zipFile.getEntry("WEB-INF/ibm-web-ext.xml");
            if(webExt != null) { //not an IBM based WAR
	            InputStream webExtContent = zipFile.getInputStream(webExt);
	
	            // parse ibm-web-ext.xml
	            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	            Document doc = dBuilder.parse(webExtContent);
	
	            // find uri attribute in context-root element
	            Element contextRoot = (Element) doc.getElementsByTagName("context-root").item(0);
	            String uri = contextRoot.getAttribute("uri");
	            uri = uri.startsWith("/") ? uri : "/" + uri;
	            return uri;
            }
            return getContextRootFromWarName(artifact);            		
        } catch (Exception e) {            
        	e.printStackTrace();
            return getContextRootFromWarName(artifact);
        }
    }
    
    private String getContextRootFromWarName(Artifact artifact) {
    	String warName = artifact.getSourcePath().getName();
    	return warName.substring(0, warName.lastIndexOf("."));
    }

    private String getApplicationXML(Artifact artifact,String earLevel) {
        String contextRoot = getContextRoot(artifact);
        String warName = artifact.getSourcePath().getName();
        String displayName = StringUtils.trimToNull(artifact.getAppName());
        if(displayName == null) {
        	displayName = warName;
        }        
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<application xmlns=\"http://java.sun.com/xml/ns/javaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "+getSchemaVersion(earLevel)+">\n" +
                                "  <description>"+warName+" was deployed using WebSphere Deployer Plugin</description>\n" +
                                "  <display-name>"+displayName+"</display-name>\n" +
                                "  <module>\n" +
                                "    <web>\n" +
                                "      <web-uri>"+warName+"</web-uri>\n" +
                                "      <context-root>"+contextRoot+"</context-root>\n" +
                                "    </web>\n" +
                                "  </module>\n" +
                                "</application>";
    }
    
    private String getSchemaVersion(String earLevel) {
    	if(earLevel == "7") {
    		return "xsi:schemaLocation=\"http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/application_"+earLevel+".xsd\" version=\""+earLevel+"\"";
    	} else { //EAR is EE5 or EE6
    		return "xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_"+earLevel+".xsd\" version=\""+earLevel+"\"";
    	}
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
    
    private Hashtable<String,Object> buildDeploymentPreferences(Artifact artifact) throws Exception {
        Hashtable<String,Object> preferences = new Hashtable<String,Object>();
        preferences.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());
        preferences.put(AppConstants.APPDEPL_DFLTBNDG, new Properties());
        AppDeploymentController controller = AppDeploymentController.readArchive(artifact.getSourcePath().getAbsolutePath(), preferences);
        
        String[] validationResult = controller.validate();
        if (validationResult != null && validationResult.length > 0) {
           throw new DeploymentServiceException("Unable to complete all task data for deployment preparation. Reason: " + Arrays.toString(validationResult));
        }

        preferences.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());
        preferences.put(AppConstants.APPDEPL_ARCHIVE_UPLOAD, Boolean.TRUE);
        preferences.put(AppConstants.APPDEPL_PRECOMPILE_JSP, artifact.isPrecompile());
        preferences.put(AppConstants.APPDEPL_DISTRIBUTE_APP, artifact.isDistribute());
        preferences.put(AppConstants.APPDEPL_JSP_RELOADENABLED, artifact.isJspReloading());
        preferences.put(AppConstants.APPDEPL_RELOADENABLED, artifact.isReloading());
        if(!artifact.isJspReloading()) {        	
        	preferences.put(AppConstants.APPDEPL_JSP_RELOADINTERVAL, new Integer(0));
        } else {
        	preferences.put(AppConstants.APPDEPL_JSP_RELOADINTERVAL, new Integer(15));
        }
        if(!artifact.isReloading()) {
        	preferences.put(AppConstants.APPDEPL_RELOADINTERVAL, new Integer(0));
        } else {        	
        	preferences.put(AppConstants.APPDEPL_RELOADINTERVAL, new Integer(15));
        }
        if(StringUtils.trimToNull(artifact.getAppName()) != null) {
        	preferences.put(AppConstants.APPDEPL_APPNAME, artifact.getAppName());
        }
        if(StringUtils.trimToNull(artifact.getInstallPath()) != null) {
        	preferences.put(AppConstants.APPDEPL_INSTALL_DIR, artifact.getInstallPath());	
        }        
        if(StringUtils.trimToNull(artifact.getClassLoaderOrder()) != null) {
        	preferences.put(AppConstants.APPDEPL_CLASSLOADINGMODE,artifact.getClassLoaderOrder());
        }          
        if(StringUtils.trimToNull(artifact.getClassLoaderPolicy()) != null) {
        	preferences.put(AppConstants.APPDEPL_CLASSLOADERPOLICY,artifact.getClassLoaderPolicy());
        }
        if(artifact.getContext() != null) {
        	buildListener.getLogger().println("Setting context root to: "+artifact.getContext());
        	preferences.put(AppConstants.APPDEPL_WEBMODULE_CONTEXTROOT, artifact.getContext());
        	preferences.put(AppConstants.APPDEPL_WEB_CONTEXTROOT, artifact.getContext());
        }  

        Hashtable<String,Object> module2server = new Hashtable<String,Object>();
        buildListener.getLogger().println("Deploying to targets: "+getFormattedTargets(artifact.getTargets()));
        module2server.put("*", getFormattedTargets(artifact.getTargets()));
        preferences.put(AppConstants.APPDEPL_MODULE_TO_SERVER, module2server);    	
        return preferences;
    }

    public void installArtifact(Artifact artifact) {
        if(!isConnected()) {
            throw new DeploymentServiceException("Cannot install artifact, no connection to IBM WebSphere Application Server exists");
        }
        try {        	
        	Hashtable<String,Object> preferences = buildDeploymentPreferences(artifact);            
            AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(getAdminClient());
            appManagementProxy.installApplication(artifact.getSourcePath().getAbsolutePath(),artifact.getAppName(),preferences, null);
            
            NotificationFilterSupport filterSupport = createFilterSupport();
            DeploymentNotificationListener notifyListener = new DeploymentNotificationListener(getAdminClient(), filterSupport, "Install " + artifact.getAppName(),AppNotification.INSTALL,buildListener,verbose);            
            
            synchronized(notifyListener) {
            	notifyListener.wait();
            }

            if(!notifyListener.isSuccessful())
               throw new DeploymentServiceException("Application not successfully deployed: " + notifyListener.getMessage());            
            
        } catch (Exception e) {
            e.printStackTrace();
            throw new DeploymentServiceException("Failed to install artifact: "+e.getMessage());
        }
    }
    
	public void updateArtifact(Artifact artifact) {
        if(!isConnected()) {
            throw new DeploymentServiceException("Cannot update artifact, no connection to IBM WebSphere Application Server exists");
        }		
        try {
        	Hashtable<String,Object> preferences = buildDeploymentPreferences(artifact);
            
            AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(getAdminClient());
            
            appManagementProxy.redeployApplication(artifact.getSourcePath().getAbsolutePath(),artifact.getAppName(),preferences, null);
            
            NotificationFilterSupport filterSupport = createFilterSupport();
            DeploymentNotificationListener notifyListener = new DeploymentNotificationListener(getAdminClient(), filterSupport, "Update " + artifact.getAppName(),AppNotification.INSTALL,buildListener,verbose);            
            
            synchronized(notifyListener) {
            	notifyListener.wait();
            }

            if(!notifyListener.isSuccessful())
               throw new DeploymentServiceException("Application not successfully updated: " + notifyListener.getMessage());            
            
        } catch (Exception e) {
            e.printStackTrace();
            throw new DeploymentServiceException("Failed to updated artifact: "+e.getMessage());
        }        
	}    

    public void uninstallArtifact(String appName) throws Exception {
    	try {
			Hashtable<Object, Object> prefs = new Hashtable<Object, Object>();
			NotificationFilterSupport filterSupport = createFilterSupport();
			
			DeploymentNotificationListener notifyListener = new DeploymentNotificationListener(getAdminClient(),filterSupport,"Uninstall " + appName, AppNotification.UNINSTALL,buildListener,verbose);        

			AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(getAdminClient());
			
			appManagementProxy.uninstallApplication(appName,prefs,null);
			
			synchronized (notifyListener) {
				notifyListener.wait();
			}
			if (!notifyListener.isSuccessful()) {
				throw new DeploymentServiceException("Application not successfully undeployed: "+ notifyListener.getMessage());
			}
		} catch (Exception e) {
			throw new DeploymentServiceException("Could not undeploy application", e);
		}
    }

    public void startArtifact(String appName) throws Exception {
    	startArtifact(appName, 5);
    }
    
    public void startArtifact(String appName, int deploymentTimeout) throws Exception {
		try {			
			AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(getAdminClient());			
			if(waitForApplicationDistribution(appManagementProxy, appName, deploymentTimeout * 60)) {
				String targetsStarted = appManagementProxy.startApplication(appName, null, null);
				log.info("Application was started on the following targets: "+ targetsStarted);
				if (targetsStarted == null) {
					throw new DeploymentServiceException("Application did not start successfully. WAS JVM logs should contain more detailed information.");
				}
			} else {
				throw new DeploymentServiceException("Distribution of application did not succeed on all nodes.");
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new DeploymentServiceException("Could not start artifact '"+ appName + "': " + e.toString());
		}
    }
    
    private boolean waitForApplicationDistribution(AppManagement appManagementProxy,String appName,int secondsToWait) throws Exception {
    	int totalSeconds = 0;
    	NotificationFilterSupport filterSupport = createFilterSupport();
    	DeploymentNotificationListener distributionListener = null;
		while (checkDistributionStatus(distributionListener) != AppNotification.DISTRIBUTION_DONE && totalSeconds < secondsToWait) {
			Thread.sleep(1000);			
			totalSeconds++;
			distributionListener = new DeploymentNotificationListener(getAdminClient(), filterSupport, null,AppNotification.DISTRIBUTION_STATUS_NODE,buildListener,verbose);
			synchronized (distributionListener) {
				appManagementProxy.getDistributionStatus(appName,new Hashtable<Object, Object>(), null);
				distributionListener.wait();
			}
		}    	
		return totalSeconds <= secondsToWait;
    }

    public void stopArtifact(String appName) throws Exception {
        try {
            AppManagementProxy.getJMXProxyForClient(getAdminClient()).stopApplication(appName, new Hashtable<Object,Object>(), null);
        } catch(Exception e) {
            e.printStackTrace();
            throw new DeploymentServiceException("Could not stop artifact '"+appName+"': "+e.getMessage());
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

    private NotificationFilterSupport createFilterSupport(){
        NotificationFilterSupport filterSupport = new NotificationFilterSupport();
        filterSupport.enableType(AppConstants.NotificationType);
        return filterSupport;
    }

    public boolean isConnected() {
        try {
            return client != null && client.isAlive() != null;
        } catch(Exception e) {
            return false;
        }
    }

    public void connect() throws Exception {
        // store the current environment, before that wsadmin client overrides it
        storedProperties = (Properties) System.getProperties().clone();
        if(isConnected()) {
        	log.warning("Already connected to WebSphere Application Server");
        }
        Properties config = new Properties();
        config.put (AdminClient.CONNECTOR_HOST, getHost());
        config.put (AdminClient.CONNECTOR_PORT, getPort());
        if(StringUtils.trimToNull(getUsername()) != null) {
            injectSecurityConfiguration(config);
        }
        config.put(AdminClient.CONNECTOR_TYPE, getConnectorType());
        client = AdminClientFactory.createAdminClient(config);
        if(client == null) {
            throw new DeploymentServiceException("Unable to connect to IBM WebSphere Application Server @ "+getHost()+":"+getPort());
        }
    }

    public void disconnect() {
        // restore environment after execution
        if (storedProperties != null) {
            System.setProperties(storedProperties);
            storedProperties = null;
        }
    	if(client != null) {
    		client.getConnectorProperties().clear();
    		client = null;
    	}
    }

    public boolean isAvailable() {
        try {
            Class.forName("com.ibm.websphere.management.AdminClientFactory", false, getClass().getClassLoader());
            return true;
        } catch(Throwable e) {
            return false;
        }
    }

    private AdminClient getAdminClient() throws ConnectorException {
        if(client == null) {
            throw new DeploymentServiceException("No connection to WebSphere exists");
        }
        return client;
    }

    private void injectSecurityConfiguration(Properties config) {
        config.put(AdminClient.CACHE_DISABLED, "true");
        config.put(AdminClient.CONNECTOR_SECURITY_ENABLED, "true");
        config.put(AdminClient.USERNAME, getUsername());
        config.put(AdminClient.PASSWORD, getPassword());

        if(getTrustStoreLocation() != null) {
	        config.put("com.ibm.ssl.trustStore", getTrustStoreLocation().getAbsolutePath());
	        config.put("javax.net.ssl.trustStore", getTrustStoreLocation().getAbsolutePath());
        }

        if(getKeyStoreLocation() != null) {
        	config.put("com.ibm.ssl.keyStore", getKeyStoreLocation().getAbsolutePath());
        	config.put("javax.net.ssl.keyStore", getKeyStoreLocation().getAbsolutePath());
        }

        if(getTrustStorePassword() != null) {
        	config.put("com.ibm.ssl.trustStorePassword", getTrustStorePassword());
        	config.put("javax.net.ssl.trustStorePassword",getTrustStorePassword());
        }

        if(getKeyStorePassword() != null) {
        	config.put("com.ibm.ssl.keyStorePassword", getKeyStorePassword());
        	config.put("javax.net.ssl.keyStorePassword", getKeyStorePassword());
        }
    }

    private String getFormattedTargets(String targets) {
    	List<String> result = new ArrayList<String>();
    	for(StringTokenizer st = new StringTokenizer(targets.trim(),"\r\n");st.hasMoreTokens();) {
    		result.add(st.nextToken());
    	}    	
        return StringUtils.join(result,"+");
    }
    
    private String getFormattedTarget(String target) {
    	target = target.replace("WebSphere:","").replace(",j2eeType=J2EEServer",""); //remove 'WebSphere:' & 'j2eeType' to work on comma delimited array
    	String[] elements = target.split(",");
    	ArrayUtils.reverse(elements);
    	return "WebSphere:"+StringUtils.join(elements,",");
    }
    public void setConnectorType(String type) {
        this.connectorType = type;
    }
    public String getConnectorType() {
        return this.connectorType;
    }
    public void setVerbose(boolean verbose) {
    	this.verbose = verbose;
    }
    public void setBuildListener(BuildListener listener) {
    	this.buildListener = listener;
    }

    /*
     * Checks the listener and figures out the aggregate distribution status of all nodes
     */
    private String checkDistributionStatus(DeploymentNotificationListener listener) throws MalformedObjectNameException {
		String distributionState = AppNotification.DISTRIBUTION_UNKNOWN;
		if (listener != null) {
			String compositeServers = listener.getNotificationProps().getProperty(AppNotification.DISTRIBUTION_STATUS_COMPOSITE);
			if (compositeServers != null) {
				if(verbose) {
					buildListener.getLogger().println("Server Composite: "+ compositeServers);
				}
				String[] servers = compositeServers.split("\\+");
				int countTrue = 0, countFalse = 0, countUnknown = 0;
				for (String server : servers) {
					ObjectName serverObject = new ObjectName(server);
					distributionState = serverObject.getKeyProperty("distribution");
					if(verbose) {
						buildListener.getLogger().println("Distributed to "+server+": "+distributionState);
					}
					if (distributionState.equals("true"))
						countTrue++;
					if (distributionState.equals("false"))
						countFalse++;
					if (distributionState.equals("unknown"))
						countUnknown++;
				}
				if (countUnknown > 0) {
					distributionState = AppNotification.DISTRIBUTION_UNKNOWN;
				} else if (countFalse > 0) {
					distributionState = AppNotification.DISTRIBUTION_NOT_DONE;
				} else if (countTrue > 0) {
					distributionState = AppNotification.DISTRIBUTION_DONE;
				} else {
					throw new DeploymentServiceException("Reported distribution status is invalid.");
				}
			}
		}
		return distributionState;
    }

}
