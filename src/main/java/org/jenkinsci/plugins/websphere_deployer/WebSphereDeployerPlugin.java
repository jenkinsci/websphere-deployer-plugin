package org.jenkinsci.plugins.websphere_deployer;

import com.ibm.websphere.management.application.AppConstants;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.Scrambler;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.websphere.services.deployment.Artifact;
import org.jenkinsci.plugins.websphere.services.deployment.DeploymentServiceException;
import org.jenkinsci.plugins.websphere.services.deployment.WebSphereDeploymentService;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;

/**
 * A Jenkins plugin for deploying to WebSphere either locally or remotely.
 *
 * @author Greg Peters
 */
public class WebSphereDeployerPlugin extends Notifier {

    private final String ipAddress;
    private final String connectorType;
    private final String port;
    private final String username;
    private final String password;
    private final String clientKeyFile;
    private final String clientTrustFile;
    private final String node;
    private final String cell;
    private final String server;
    private final String artifacts;
    private final String clientKeyPassword;
    private final String clientTrustPassword;
    private final String earLevel;
    private final String deploymentTimeout;
    private final boolean autoStart;
    private final boolean precompile;
    private final boolean reloading;

    @DataBoundConstructor
    public WebSphereDeployerPlugin(String ipAddress,
                                   String connectorType,
                                   String port,
                                   String username,
                                   String password,
                                   String clientKeyFile,
                                   String clientTrustFile,
                                   String artifacts,
                                   String node,
                                   String cell,
                                   String server,
                                   String clientKeyPassword,
                                   String clientTrustPassword,
                                   String earLevel,
                                   String deploymentTimeout,
                                   boolean autoStart,
                                   boolean precompile,
                                   boolean reloading) {
        this.ipAddress = ipAddress;
        this.connectorType = connectorType;
        this.port = port;
        this.username = username;
        this.password = Scrambler.scramble(password);
        this.clientKeyFile = clientKeyFile;
        this.clientTrustFile = clientTrustFile;
        this.artifacts = artifacts;
        this.node = node;
        this.cell = cell;
        this.server = server;
        this.autoStart = autoStart;
        this.clientKeyPassword = Scrambler.scramble(clientKeyPassword);
        this.clientTrustPassword = Scrambler.scramble(clientTrustPassword);
        this.earLevel = earLevel;
        this.deploymentTimeout = deploymentTimeout;
        this.precompile = precompile;
        this.reloading = reloading;
    }

    public String getEarLevel() {
        return earLevel;
    }

    public boolean isPrecompile() {
        return precompile;
    }

    public boolean isReloading() {
        return reloading;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public String getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return Scrambler.descramble(password);
    }

    public String getClientKeyPassword() {
        return Scrambler.descramble(clientKeyPassword);
    }

    public String getClientTrustPassword() {
        return Scrambler.descramble(clientTrustPassword);
    }

    public String getClientKeyFile() {
        return clientKeyFile;
    }

    public String getClientTrustFile() {
        return clientTrustFile;
    }

    public String getNode() {
        return node;
    }

    public String getServer() {
        return server;
    }

    public String getCell() {
        return cell;
    }

    public String getArtifacts() {
        return artifacts;
    }

    public boolean isAutoStart() {
        return autoStart;
    }
    
    public String getDeploymentTimeout() {
		return deploymentTimeout;
	}    

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        if(build.getResult().equals(Result.SUCCESS)) {
            WebSphereDeploymentService service = new WebSphereDeploymentService();
            try {
                connect(listener, service);
                for(FilePath path:gatherArtifactPaths(build, listener)) {
                    Artifact artifact = createArtifact(path,listener,service);
                    stopArtifact(artifact.getAppName(),listener,service);
                    uninstallArtifact(artifact.getAppName(),listener,service);
                    deployArtifact(artifact,listener,service);                    
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
        listener.getLogger().println("Deploying New '"+artifact.getAppName()+"' to IBM WebSphere Application Server");
        HashMap<String,Object> options = new HashMap<String, Object>();
        options.put(AppConstants.APPDEPL_JSP_RELOADENABLED,isReloading());
        options.put(AppConstants.APPDEPL_PRECOMPILE_JSP,isPrecompile());
        service.installArtifact(artifact, options);
    }

    private void uninstallArtifact(String appName,BuildListener listener,WebSphereDeploymentService service) throws Exception {
        if(service.isArtifactInstalled(appName)) {
            listener.getLogger().println("Uninstalling Old Application '"+appName+"'...");
            service.uninstallArtifact(appName);
        }
    }

    private void startArtifact(String appName,BuildListener listener,WebSphereDeploymentService service) throws Exception {
        if(isAutoStart()) {
        	listener.getLogger().println("Starting New Application '"+appName+"'...");
        	service.startArtifact(appName, Integer.parseInt(getDeploymentTimeout()));
        }
    }

    private void stopArtifact(String appName,BuildListener listener,WebSphereDeploymentService service) throws Exception {
        if(service.isArtifactInstalled(appName)) {
            listener.getLogger().println("Stopping Old Application '"+appName+"'...");
            service.stopArtifact(appName);
        }
    }

    private Artifact createArtifact(FilePath path,BuildListener listener,WebSphereDeploymentService service) {
        Artifact artifact = new Artifact();
        if(path.getRemote().endsWith(".ear")) {
            artifact.setType(Artifact.TYPE_EAR);
        } else if(path.getRemote().endsWith(".war")) {
            artifact.setType(Artifact.TYPE_WAR);
        }
        artifact.setPrecompile(isPrecompile());
        artifact.setSourcePath(new File(path.getRemote()));
        artifact.setAppName(getAppName(artifact,service));
        if(artifact.getType() == Artifact.TYPE_WAR) {
            generateEAR(artifact,listener,service);
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

    private void connect(BuildListener listener,WebSphereDeploymentService service) throws Exception {
        listener.getLogger().println("Connecting to IBM WebSphere Application Server...");
        service.setConnectorType(getConnectorType());
        service.setHost(getIpAddress());
        service.setPort(getPort());
        service.setUsername(getUsername());
        service.setPassword(getPassword());
        service.setKeyStoreLocation(new File(getClientKeyFile()));
        service.setKeyStorePassword(getClientKeyPassword());
        service.setTrustStoreLocation(new File(getClientTrustFile()));
        service.setTrustStorePassword(getClientTrustPassword());
        service.setTargetCell(getCell());
        service.setTargetNode(getNode());
        service.setTargetServer(getServer());
        service.connect();
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
        listener.getLogger().println("Generating EAR For New Artifact: "+artifact.getAppName());
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
                    String destination = System.getProperty("user.home")+File.separator+".jenkins"+File.separator+"plugins"+File.separator+"websphere-deployer"+File.separator+"WEB-INF"+File.separator+"lib"+File.separator;
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

