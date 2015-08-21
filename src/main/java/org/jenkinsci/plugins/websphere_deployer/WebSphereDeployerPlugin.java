package org.jenkinsci.plugins.websphere_deployer;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.websphere.services.deployment.Artifact;
import org.jenkinsci.plugins.websphere.services.deployment.Server;
import org.jenkinsci.plugins.websphere.services.deployment.WebSphereDeploymentService;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.ibm.websphere.management.application.AppConstants;

/**
 * A Jenkins plugin for deploying to WebSphere either locally or remotely.
 *
 * @author Greg Peters
 */
public class WebSphereDeployerPlugin extends Notifier {

	private final static String OPERATION_REINSTALL = "1";
    private final String ipAddress;
    private final String connectorType;
    private final String port;
    private final String artifacts;
    private final String earLevel;
    private final String deploymentTimeout;
    private final String classLoaderOrder;
    private final String classLoaderPolicy;
    private final String operations;
    private final String context;
    private final String installPath;
    private final String targets;
    private final boolean precompile;
    private final boolean reloading;
    private final boolean jspReloading;
    private final boolean verbose;
    private final boolean distribute;
    private final WebSphereSecurity security;

    @DataBoundConstructor
    public WebSphereDeployerPlugin(String ipAddress,
                                   String connectorType,
                                   String port,
                                   String installPath,
                                   WebSphereSecurity security,
                                   String artifacts,
                                   String earLevel,
                                   String deploymentTimeout,
                                   String operations,
                                   String context,
                                   String targets,
                                   boolean precompile,
                                   boolean reloading,
                                   boolean jspReloading,
                                   boolean verbose,
                                   boolean distribute,
                                   String classLoaderPolicy,
                                   String classLoaderOrder) {
    	this.context = context;
    	this.targets = targets;
    	this.installPath = installPath;
        this.ipAddress = ipAddress;        
        this.connectorType = connectorType;
        this.artifacts = artifacts;
        this.port = port;
        this.operations = operations;
        this.earLevel = earLevel;
        this.deploymentTimeout = deploymentTimeout;
        this.precompile = precompile;
        this.reloading = reloading;
        this.jspReloading = jspReloading;
        this.verbose = verbose;
        this.distribute = distribute;
        this.security = security;
        this.classLoaderPolicy = classLoaderPolicy;
        this.classLoaderOrder = classLoaderOrder;
    }
    
    public String getClassLoaderOrder() {
    	return classLoaderOrder;
    }
    
    public String getClassLoaderPolicy() {
    	return classLoaderPolicy;
    }
    
    public String getTargets() {
    	return targets;
    }

    public String getEarLevel() {
        return earLevel;
    }

    public WebSphereSecurity getSecurity() {
    	return security;
    }
    
    public boolean isDistribute() {
    	return distribute;
    }
    
    public boolean isPrecompile() {
        return precompile;
    }

    public boolean isReloading() {
        return reloading;
    }
    
    public boolean isJspReloading() {
    	return jspReloading;
    }
    
    public boolean isVerbose() {
    	return verbose;
    }

    public String getIpAddress() {
        return ipAddress;
    }
    
    public String getContext() {
    	return context;
    }
    
    public String getInstallPath() {
    	return installPath;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public String getPort() {
        return port;
    }

    public String getArtifacts() {
        return artifacts;
    }

    public String getOperations() {
        return operations;
    }
    
    public String getDeploymentTimeout() {
		return deploymentTimeout;
	}    

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        if(build.getResult().equals(Result.SUCCESS)) {
        	WebSphereDeploymentService service = new WebSphereDeploymentService();
            try {            	
                EnvVars env = build.getEnvironment(listener);
                preInitializeService(listener,service, env);  
            	service.connect();                	               
                for(FilePath path:gatherArtifactPaths(build, listener)) {
                    Artifact artifact = createArtifact(path,listener,service);                    
                    stopArtifact(artifact.getAppName(),listener,service);
                    if(getOperations().equals(OPERATION_REINSTALL)) {
                    	uninstallArtifact(artifact.getAppName(),listener,service);
                    	deployArtifact(artifact,listener,service);
                    } else { //otherwise update application
                    	if(!service.isArtifactInstalled(artifact.getAppName())) {
                    		deployArtifact(artifact, listener, service); //do initial deployment
                    	} else {
                    		updateArtifact(artifact,listener,service);
                    	}
                    }
                    startArtifact(artifact.getAppName(),listener,service);
                }
            } catch (Exception e) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                e.printStackTrace();
                PrintStream p = new PrintStream(out);
                e.printStackTrace(p);
                listener.getLogger().println("Error deploying to IBM WebSphere Application Server: "+new String(out.toByteArray()));
                build.setResult(Result.FAILURE);
            } finally {
                service.disconnect();
            }
        }
        return true;
    }

    private void deployArtifact(Artifact artifact,BuildListener listener,WebSphereDeploymentService service) throws Exception {
        listener.getLogger().println("Deploying '" + artifact.getAppName() + "' to IBM WebSphere Application Server");
        service.installArtifact(artifact);
    }

    private void uninstallArtifact(String appName,BuildListener listener,WebSphereDeploymentService service) throws Exception {
        if(service.isArtifactInstalled(appName)) {
            listener.getLogger().println("Uninstalling Old Application '"+appName+"'...");
            service.uninstallArtifact(appName);
        }
    }

    private void startArtifact(String appName,BuildListener listener,WebSphereDeploymentService service) throws Exception {
    	listener.getLogger().println("Starting Application '"+appName+"'...");
    	try {
    		service.startArtifact(appName, Integer.parseInt(getDeploymentTimeout()));
    	} catch(NumberFormatException e) {
    		service.startArtifact(appName);
    	}
    }

    private void stopArtifact(String appName,BuildListener listener,WebSphereDeploymentService service) throws Exception {
        if(service.isArtifactInstalled(appName)) {
            listener.getLogger().println("Stopping Old Application '"+appName+"'...");
            service.stopArtifact(appName);
        }
    }
    
    private void updateArtifact(Artifact artifact,BuildListener listener,WebSphereDeploymentService service) throws Exception {
        if(service.isArtifactInstalled(artifact.getAppName())) {
            listener.getLogger().println("Updating '" + artifact.getAppName() + "' on IBM WebSphere Application Server");
            service.updateArtifact(artifact);
        }
    }      

    private Artifact createArtifact(FilePath path,BuildListener listener,WebSphereDeploymentService service) {
        Artifact artifact = new Artifact();
        if(path.getRemote().endsWith(".ear")) {
            artifact.setType(Artifact.TYPE_EAR);
        } else if(path.getRemote().endsWith(".war")) {
            artifact.setType(Artifact.TYPE_WAR);
        }
        if(StringUtils.trimToNull(context) != null) {
        	artifact.setContext(context);
        }        
        artifact.setClassLoaderOrder(classLoaderOrder);
        artifact.setClassLoaderPolicy(classLoaderPolicy);
        artifact.setTargets(targets);
        artifact.setInstallPath(installPath);
        artifact.setJspReloading(reloading);
        artifact.setDistribute(distribute);
        artifact.setPrecompile(isPrecompile());
        artifact.setSourcePath(new File(path.getRemote()));
        artifact.setAppName(getAppName(artifact,service));
        if(artifact.getType() == Artifact.TYPE_WAR) {
            generateEAR(artifact, listener, service);
        }
        return artifact;
    }

    private FilePath[] gatherArtifactPaths(AbstractBuild build,BuildListener listener) throws Exception {
        FilePath[] paths = build.getWorkspace().getParent().list(getArtifacts());
        if(paths.length == 0) {
            listener.getLogger().println("No deployable artifacts found in path: "+build.getWorkspace().getParent()+File.separator+getArtifacts());
            throw new Exception("No deployable artifacts found!");
        } else {
            listener.getLogger().println("The following artifacts will be deployed in this order...");
            listener.getLogger().println("-------------------------------------------");
            for(FilePath path:paths) {
                listener.getLogger().println(path.getName());
            }
            listener.getLogger().println("-------------------------------------------");
        }
        return paths;
    }

    private void preInitializeService(BuildListener listener,WebSphereDeploymentService service,EnvVars env) throws Exception {
        listener.getLogger().println("Connecting to IBM WebSphere Application Server...");
        service.setVerbose(isVerbose());
        service.setBuildListener(listener);;
        service.setConnectorType(getConnectorType());
        service.setHost(env.expand(getIpAddress()));
        service.setPort(env.expand(getPort()));
        service.setUsername(env.expand(security.getUsername()));
        service.setPassword(env.expand(security.getPassword()));
        service.setKeyStoreLocation(new File(env.expand(security.getClientKeyFile())));
        service.setKeyStorePassword(env.expand(security.getClientKeyPassword()));
        service.setTrustStoreLocation(new File(env.expand(security.getClientTrustFile())));
        service.setTrustStorePassword(env.expand(security.getClientTrustPassword()));
    }

    private String getAppName(Artifact artifact,WebSphereDeploymentService service) {
        if(artifact.getType() == Artifact.TYPE_EAR) {
            return service.getAppName(artifact.getSourcePath().getAbsolutePath());
        } else {
            String filename = artifact.getSourcePath().getName();
            return filename.substring(0,filename.lastIndexOf("."));
        }
    }

    private void generateEAR(Artifact artifact,BuildListener listener,WebSphereDeploymentService service) {
        listener.getLogger().println("Generating EAR For Artifact: "+artifact.getAppName());
        File modified = new File(artifact.getSourcePath().getParent(),artifact.getAppName()+".ear");
        service.generateEAR(artifact, modified, getEarLevel());
        artifact.setSourcePath(modified);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String adminClientPath;
        private String orbClientPath;

        public DescriptorImpl() {
            load();
        }

        public FormValidation doTestConnection(@QueryParameter("ipAddress")String ipAddress,
                                               @QueryParameter("connectorType")String connectorType,
                                               @QueryParameter("port")String port,
                                               @QueryParameter("username")String username,
                                               @QueryParameter("password")String password,
                                               @QueryParameter("clientKeyFile")String clientKeyFile,
                                               @QueryParameter("clientTrustFile")String clientTrustFile,
                                               @QueryParameter("clientKeyPassword")String clientKeyPassword,
                                               @QueryParameter("clientTrustPassword")String clientTrustPassword) throws IOException, ServletException {
            WebSphereDeploymentService service = new WebSphereDeploymentService();
            try {
                if(!service.isAvailable()) {
                    String destination = "<Jenkins_Root>"+File.separator+"plugins"+File.separator+"websphere-deployer"+File.separator+"WEB-INF"+File.separator+"lib"+File.separator;
                    return FormValidation.warning("Cannot find the required IBM WebSphere Application Server jar files in '"+destination+"'. Please copy them from IBM WebSphere Application Server (see plugin documentation)");
                }
                service.setConnectorType(connectorType);
                service.setHost(ipAddress);
                service.setPort(port);
                service.setUsername(username);
                service.setPassword(password);
                service.setKeyStoreLocation(new File(clientKeyFile));
                service.setKeyStorePassword(clientKeyPassword);
                service.setTrustStoreLocation(new File(clientTrustFile));
                service.setTrustStorePassword(clientTrustPassword);
                service.connect();
                return FormValidation.ok("Connection Successful!");
            } catch (Exception e) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintStream p = new PrintStream(out);
                e.printStackTrace(p);
                return FormValidation.error("Connection failed: " + new String(out.toByteArray()));
            } finally {
                service.disconnect();
            }
        }

        public FormValidation doCheckPort(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Select a port");
            if (value.length() > 5)
                return FormValidation.warning("Cannot be greater than 65535");
            return FormValidation.ok();
        }

        public FormValidation doCheckAdminClientPath(@QueryParameter String value)
                throws IOException, ServletException {
            if(!new File(value).exists()) {
                return FormValidation.error("Path '"+value+"' is not found");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckOrbClientPath(@QueryParameter String value)
                throws IOException, ServletException {
            if(!new File(value).exists()) {
                return FormValidation.error("Path '"+value+"' is not found");
            }
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
        	return "Deploy To IBM WebSphere Application Server";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            adminClientPath = formData.getString("adminClientPath");
            orbClientPath = formData.getString("orbClientPath");
            save();
            return super.configure(req,formData);
        }

        public String getAdminClientPath() {
            return adminClientPath;
        }

        public String getOrbClientPath() {
            return orbClientPath;
        }
    }
}

