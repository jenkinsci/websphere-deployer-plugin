package org.jenkinsci.plugins.websphere_deployer;

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
import org.jenkinsci.plugins.websphere.services.deployment.LibertyDeploymentService;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.channels.IllegalSelectorException;
import java.nio.charset.Charset;
import java.util.HashMap;

/**
 * @author Greg Peters
 */
public class LibertyDeployerPlugin extends Notifier {

    private final String ipAddress;
    private final String port;
    private final String username;
    private final String consolePassword;
    private final String clientTrustFile;
    private final String clientTrustPassword;
    private final String artifacts;

    @DataBoundConstructor
    public LibertyDeployerPlugin(String ipAddress,
                                 String port,
                                 String username,
                                 String consolePassword,
                                 String clientTrustFile,
                                 String clientTrustPassword,
                                 String artifacts) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.username = username;
        this.consolePassword = Scrambler.scramble(consolePassword);
        this.clientTrustFile = clientTrustFile;
        this.clientTrustPassword = Scrambler.scramble(clientTrustPassword);
        this.artifacts = artifacts;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getConsolePassword() {
        return Scrambler.descramble(consolePassword);
    }

    public String getClientTrustFile() {
        return clientTrustFile;
    }

    public String getClientTrustPassword() {
        return Scrambler.descramble(clientTrustPassword);
    }

    public String getArtifacts() {
        return artifacts;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
    	Result buildResult = build.getResult();
    	if(buildResult == null) {
    		listener.getLogger().println("Error deploying to IBM WebSphere Liberty Profile: Build result is null");
    		throw new IllegalStateException("Build result is null");
    	}
        if(buildResult.equals(Result.SUCCESS)) {
            LibertyDeploymentService service = new LibertyDeploymentService();
            try {
                connect(listener,service);
                for(FilePath path:gatherArtifactPaths(build, listener)) {
                    Artifact artifact = createArtifact(path,listener);
                    stopArtifact(artifact,listener,service);
                    uninstallArtifact(artifact,listener,service);
                    deployArtifact(artifact,listener,service);
                    Thread.sleep(2000); //wait 2 seconds for deployment to settle
                    startArtifact(artifact,listener,service);
                }
            } catch (Exception e) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintStream p = null;
				try {
					p = new PrintStream(out,true,"UTF-8");
	                e.printStackTrace(p);
	                listener.getLogger().println("Error deploying to IBM WebSphere Liberty Profile: "+new String(out.toByteArray(),"UTF-8"));
	                build.setResult(Result.FAILURE);
				} catch (UnsupportedEncodingException e1) {
					e1.printStackTrace();
				} finally {
					if(p != null) {
						p.close();
					}
				}
            } finally {
                try {
                    disconnect(listener,service);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }

    private Artifact createArtifact(FilePath path,BuildListener listener) {
        Artifact artifact = new Artifact();
        if(path.getRemote().endsWith(".ear")) {
            artifact.setType(Artifact.TYPE_EAR);
        } else if(path.getRemote().endsWith(".war")) {
            artifact.setType(Artifact.TYPE_WAR);
        } else if(path.getRemote().endsWith(".rar")) {
            artifact.setType(Artifact.TYPE_RAR);
        } else if(path.getRemote().endsWith(".jar")) {
            artifact.setType(Artifact.TYPE_JAR);
        }
        artifact.setSourcePath(new File(path.getRemote()));
        artifact.setAppName(path.getBaseName());
        return artifact;
    }

    private void connect(BuildListener listener,LibertyDeploymentService service) throws Exception {
        listener.getLogger().println("Connecting to IBM WebSphere Liberty Profile...");
        service.setHost(getIpAddress());
        service.setPort(getPort());
        service.setUsername(getUsername());
        service.setPassword(getConsolePassword());
        service.setTrustStoreLocation(new File(getClientTrustFile()));
        service.setTrustStorePassword(getClientTrustPassword());
        service.connect();
    }

    private void disconnect(BuildListener listener,LibertyDeploymentService service) throws Exception {
        listener.getLogger().println("Disconnecting from IBM WebSphere Liberty Profile...");
        service.disconnect();
    }

    private void stopArtifact(Artifact artifact,BuildListener listener,LibertyDeploymentService service) throws Exception {
        if(service.isArtifactInstalled(artifact)) {
            listener.getLogger().println("Stopping Old Application '"+artifact+"'...");
            service.stopArtifact(artifact);
        }
    }

    private void uninstallArtifact(Artifact artifact,BuildListener listener,LibertyDeploymentService service) throws Exception {
        if(service.isArtifactInstalled(artifact)) {
            listener.getLogger().println("Uninstalling Old Application '"+artifact.getAppName()+"'...");
            service.uninstallArtifact(artifact);
        }
    }

    private void deployArtifact(Artifact artifact,BuildListener listener,LibertyDeploymentService service) throws Exception {
        listener.getLogger().println("Deploying '"+artifact.getAppName()+"' to IBM WebSphere Liberty Profile");
        service.installArtifact(artifact);
    }

    private void startArtifact(Artifact artifact,BuildListener listener,LibertyDeploymentService service) throws Exception {
        listener.getLogger().println("Starting Application '"+artifact.getAppName()+"'...");
        service.startArtifact(artifact);
    }

    private FilePath[] gatherArtifactPaths(AbstractBuild build,BuildListener listener) throws Exception {
    	FilePath workspace = build.getWorkspace();
    	if(workspace == null) {
    		listener.getLogger().println("Failed to gather artifact paths: Build workspace is null");
    		throw new IllegalStateException("Failed to gather artifact paths: Build workspace is null");
    	}
    	FilePath workspaceParent = workspace.getParent();
    	if(workspaceParent == null) {
    		listener.getLogger().println("Failed to gather artifact paths: Build workspace's parent path is null");
    		throw new IllegalStateException("Failed to gather artifact paths: Build workspace's parent path is null");
    	}
        FilePath[] paths = workspaceParent.list(getArtifacts());
        if(paths.length == 0) {
            listener.getLogger().println("No deployable artifacts found in path: "+workspaceParent+ File.separator+getArtifacts());
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

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {


        public DescriptorImpl() {
            load();
        }

        public FormValidation doTestConnection(@QueryParameter("ipAddress")String ipAddress,
                                               @QueryParameter("port")String port,
                                               @QueryParameter("username")String username,
                                               @QueryParameter("consolePassword")String password,
                                               @QueryParameter("clientTrustFile")String clientTrustFile,
                                               @QueryParameter("clientTrustPassword")String clientTrustPassword) throws IOException, ServletException {
            LibertyDeploymentService service = new LibertyDeploymentService();
            System.out.println("ClassLoader: "+getClass().getClassLoader());
            System.out.println("Parent ClassLoader: "+getClass().getClassLoader().getParent());
            System.out.println("System ClassLoader: "+ClassLoader.getSystemClassLoader());
            if(!service.isAvailable()) {
                String destination = System.getProperty("user.home")+File.separator+".jenkins"+File.separator+"plugins"+File.separator+"websphere-deployer"+File.separator+"WEB-INF"+File.separator+"lib"+File.separator;
                return FormValidation.warning("Cannot find the required IBM WebSphere Liberty jar files in '"+destination+"'. Please copy them from IBM WebSphere Liberty (see plugin documentation)");
            }
            try {
                service.setHost(ipAddress);
                service.setPort(port);
                service.setUsername(username);
                service.setPassword(password);
                service.setTrustStoreLocation(new File(clientTrustFile));
                service.setTrustStorePassword(clientTrustPassword);
                service.connect();
                return FormValidation.ok("Connection Successful!");
            } catch (Exception e) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintStream p = new PrintStream(out,true,"UTF-8");
                e.printStackTrace(p);
                return FormValidation.error("Connection failed: " + new String(out.toByteArray(),"UTF-8"));
            } finally {
                service.disconnect();
            }

        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Deploy To IBM WebSphere Liberty Profile";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req,formData);
        }
    }
}
