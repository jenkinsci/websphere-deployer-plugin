package org.jenkinsci.plugins.websphere_deployer;

import com.ibm.ejs.ras.ManagerAdmin;
import org.jenkinsci.plugins.websphere.services.deployment.DeployData;
import com.ibm.websphere.management.application.AppConstants;
import hudson.EnvVars;
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
import org.jenkinsci.plugins.websphere.services.deployment.WebSphereDeploymentService;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.websphere.services.deployment.ArtifactDescription;
import org.jenkinsci.plugins.websphere.services.deployment.ClassLoadOrder;
import org.jenkinsci.plugins.websphere.services.deployment.Utils;
import org.jenkinsci.plugins.websphere.services.deployment.WarClassLoaderPolicy;

/**
 * A Jenkins plugin for deploying to WebSphere either locally or remotely.
 *
 * @author Greg Peters
 */
public class WebSphereDeployerPlugin extends Notifier {

    private final static String OPERATION_REINSTALL = "1";
    private final static String OPERATION_UPDATE = "2";
    private final String ipAddress;
    private final String connectorType;
    private final String port;
    private final String username;
    private final String password;
    private final String clientKeyFile;
    private final String clientTrustFile;
    private final String clientKeyPassword;
    private final String clientTrustPassword;
    private final List<DeployData> deployData;
    private final String earLevel;
    private final int deploymentTimeout;
    private final String operations;
    private final boolean precompile;
    private final boolean reloading;
    private final boolean verbose;

    @DataBoundConstructor
    public WebSphereDeployerPlugin(String ipAddress,
            String connectorType,
            String port,
            String username,
            String password,
            String clientKeyFile,
            String clientTrustFile,
            String node,
            String cell,
            String server,
            String cluster,
            String clientKeyPassword,
            String clientTrustPassword,
            String earLevel,
            String deploymentTimeout,
            String operations,
            boolean precompile,
            boolean reloading,
            boolean verbose,
            List<DeployData> deployData) {
        this.ipAddress = ipAddress;
        this.connectorType = connectorType;
        this.port = port;
        this.username = username;
        this.password = Scrambler.scramble(password);
        this.clientKeyFile = clientKeyFile;
        this.clientTrustFile = clientTrustFile;
        this.operations = operations;
        this.clientKeyPassword = Scrambler.scramble(clientKeyPassword);
        this.clientTrustPassword = Scrambler.scramble(clientTrustPassword);
        this.earLevel = earLevel;
        this.deploymentTimeout = ((deploymentTimeout != null && !deploymentTimeout.isEmpty()) ? Integer.parseInt(deploymentTimeout) : 5);
        this.precompile = precompile;
        this.reloading = reloading;
        this.verbose = verbose;
        this.deployData = deployData;
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

    public boolean isVerbose() {
        return verbose;
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

    public List<DeployData> getDeployData() {
        return deployData;
    }

    public String getOperations() {
        return operations;
    }

    public int getDeploymentTimeout() {
        return deploymentTimeout;
    }

    private static final String DEPLOY_TO_WEBSPHERE = "DEPLOY_TO_WEBSPHERE";

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        if (build.getResult().equals(Result.SUCCESS)) {
            try {
                EnvVars env = build.getEnvironment(listener);

                if (!Boolean.valueOf(env.get(DEPLOY_TO_WEBSPHERE, "true"))) {
                    listener.getLogger().println("Plugin is turned off by \'" + DEPLOY_TO_WEBSPHERE + "\'");
                    return true;
                }

                final List<Artifact> preparedArtifacts = prepareArtifacts(build, listener, env);
                final List<Artifact> installedArtifacts = installAllArtifacts(preparedArtifacts, listener, env);

                printResult(preparedArtifacts, installedArtifacts, listener);
                if (installedArtifacts.isEmpty()) {
                    build.setResult(Result.FAILURE);
                } else if (installedArtifacts.size() < preparedArtifacts.size()) {
                    build.setResult(Result.UNSTABLE);
                }
            } catch (Exception e) {
                listener.getLogger().println("Error deploying to IBM WebSphere Application Server: " + e.toString());
                build.setResult(Result.FAILURE);
            }
        }
        return true;
    }

    private void printResult(final List<Artifact> preparedArtifacts, final List<Artifact> installedArtifacts, BuildListener listener) {

        final List<Artifact> failedArtifacts = new ArrayList<Artifact>(preparedArtifacts);
        failedArtifacts.removeAll(installedArtifacts);

        listener.getLogger().println("-------------- RESULT ---------------------");
        listener.getLogger().println("-------------------------------------------");
        listener.getLogger().println("Installed successfully:");
        for (Artifact a : installedArtifacts) {
            listener.getLogger().println("                       - " + a.getAppName());
        }
        listener.getLogger().println("Installed failure:");
        for (Artifact a : failedArtifacts) {
            listener.getLogger().println("                       - " + a.getAppName());
        }
        listener.getLogger().println("-------------------------------------------");

    }

    private List<Artifact> prepareArtifacts(AbstractBuild build, BuildListener listener, EnvVars env) throws Exception {
        WebSphereDeploymentService service = new WebSphereDeploymentService(isVerbose());
        connect(listener, service, env);

        try {
            final List<Artifact> result = new ArrayList<Artifact>(10);
            listener.getLogger().println("The following artifacts will be deployed in this order...");
            listener.getLogger().println("-------------------------------------------");
            for (DeployData dData : deployData) {
                String targets = Utils.targetsAsString(dData.getTargets());
                listener.getLogger().println(" ===> In " + targets + ": ");
                for (ArtifactDescription artDescr : dData.getArtifacts()) {
                    FilePath[] paths = build.getWorkspace().getParent().list(artDescr.getArtifact());
                    if (paths.length == 0) {
                        listener.getLogger().println("No deployable artifacts found in path: " + build.getWorkspace().getParent() + File.separator + artDescr.getArtifact());
                        throw new Exception("No deployable artifacts found!");

                    } else if (paths.length > 1 && StringUtils.isNotEmpty(artDescr.getBindUri())) {
                        listener.getLogger().println(">>>>>>>>>>>>>>");
                        listener.getLogger().println("!!!WARNING!!!");
                        listener.getLogger().println("Several artifacts was found by: " + artDescr.getArtifact() + " and URI is set:");
                        for (FilePath p : paths) {
                            listener.getLogger().println("      - " + p.getRemote());
                        }
                        listener.getLogger().println("Only first artifact will be deploed and assoctiated with given URI.");
                        listener.getLogger().println("<<<<<<<<<<<<<<");
                        listener.getLogger().println("      * " + paths[0].getName());
                        result.add(createArtifact(paths[0], artDescr, targets, listener, service));

                    } else {
                        for (FilePath path : paths) {
                            listener.getLogger().println("      * " + paths[0].getName());
                            result.add(createArtifact(path, artDescr, targets, listener, service));
                        }
                    }
                }
            }
            listener.getLogger().println("-------------------------------------------");
            return result;
        } finally {
            if (service.isConnected()) {
                service.disconnect();
            }
        }
    }

    private Artifact createArtifact(FilePath path, ArtifactDescription artDescr, String targets, BuildListener listener, WebSphereDeploymentService service) {
        final Artifact.Type type = Artifact.Type.fromFileExtention(path.getRemote());
        File pathToArtifact = new File(path.getRemote());
        final String appName = getAppName(type, pathToArtifact, service);

        final Artifact result = Artifact.Builder.create()
                .setType(type)
                .setSourcePath(pathToArtifact)
                .setAppName(appName)
                .setDeployTarget(targets)
                .setWebUri(artDescr.getBindUri())
                .setPrecompile(precompile)
                .setClassLoadOrder(artDescr.getClassLoadOrder())
                .setClassLoadOrderWeb(artDescr.getClassLoadOrderWeb())
                .setStartingWeightWeb(artDescr.getStartingWeightWeb())
                .setStartupOrder(artDescr.getStartupOrder())
                .setWarClassLoaderPolicy(artDescr.getWarClassLoaderPolicy())
                .build();

        if (type == Artifact.Type.TYPE_EAR) {
            return result;
        }
        pathToArtifact = generateEAR(result, listener, service);

        return Artifact.Builder.create()
                .setType(type)
                .setSourcePath(pathToArtifact)
                .setAppName(appName)
                .setDeployTarget(targets)
                .setWebUri(artDescr.getBindUri())
                .setPrecompile(precompile)
                .setClassLoadOrder(artDescr.getClassLoadOrder())
                .setClassLoadOrderWeb(artDescr.getClassLoadOrderWeb())
                .setStartingWeightWeb(artDescr.getStartingWeightWeb())
                .setStartupOrder(artDescr.getStartupOrder())
                .setWarClassLoaderPolicy(artDescr.getWarClassLoaderPolicy())
                .build();
    }

    private List<Artifact> installAllArtifacts(List<Artifact> artifacts, BuildListener listener, EnvVars env) throws Exception {

        final List<Artifact> installed = new ArrayList<Artifact>();
        for (Artifact artifact : artifacts) {
            WebSphereDeploymentService service = new WebSphereDeploymentService(isVerbose());
            try {
                connect(listener, service, env);
                stopArtifact(artifact.getAppName(), listener, service);
                if (getOperations().equals(OPERATION_REINSTALL)) {
                    listener.getLogger().println("==>> Operations mode is Install/Reinstall Application(s)");
                    uninstallArtifact(artifact.getAppName(), listener, service);
                    deployArtifact(artifact, listener, service);
                } else { //otherwise update application
                    listener.getLogger().println("==>> Operations mode is Install/Update Application(s)");
                    if (!service.isArtifactInstalled(artifact.getAppName())) {
                        deployArtifact(artifact, listener, service); //do initial deployment
                    } else {
                        listener.getLogger().println("==>> Application " + artifact.getAppName() + " has already installed.");
                        updateArtifact(artifact, listener, service);
                    }
                }
                applyAdditionalAttributes(artifact, listener, service);
                waitForDistribution(artifact.getAppName(), listener, service);
                startArtifact(artifact.getAppName(), listener, service);
                installed.add(artifact);
            } catch (InterruptedException ex) {
                throw ex;
            } catch (Exception e) {
                listener.getLogger().println("!!!!Error deploying for artifact \'" + artifact.getAppName() + "\': " + Utils.getStackTraceFor(e));
            } finally {
                if (service.isConnected()) {
                    service.disconnect();
                }
            }
        }
        return installed;
    }

    private void applyAdditionalAttributes(Artifact artifact, BuildListener listener, WebSphereDeploymentService service) throws Exception {
        listener.getLogger().println("==>> Apply additional attributes for: " + artifact.getAppName());
        service.additionalAttributes(artifact);
        listener.getLogger().println("==>> Additional attributes for '" + artifact.getAppName() + "' was applyed");
    }

    private void deployArtifact(Artifact artifact, BuildListener listener, WebSphereDeploymentService service) throws Exception {
        listener.getLogger().println("==>> Deploying '" + artifact.getAppName() + "' to IBM WebSphere Application Server");
        service.installArtifact(artifact, getDeploymentOptions(), getDeploymentTimeout(), listener);
        listener.getLogger().println("==>> Application '" + artifact.getAppName() + "' was deploed to IBM WebSphere Application Server");
    }

    private void uninstallArtifact(String appName, BuildListener listener, WebSphereDeploymentService service) throws Exception {
        if (service.isArtifactInstalled(appName)) {
            listener.getLogger().println("==>> Uninstalling Old Application '" + appName + "'...");
            service.uninstallArtifact(appName, listener, getDeploymentTimeout());
            listener.getLogger().println("==>> Old Application '" + appName + "' is uninstalled");
        }
    }

    private void waitForDistribution(String appName, BuildListener listener, WebSphereDeploymentService service) throws Exception {
        if (!service.waitForDistribution(appName, listener, deploymentTimeout)) {
            throw new Exception("Application had not distributed.");
        }
    }

    private void startArtifact(String appName, BuildListener listener, WebSphereDeploymentService service) throws Exception {
        listener.getLogger().println("==>> Starting Application '" + appName + "'...");
        service.startArtifact(appName, getDeploymentTimeout(), listener);
        listener.getLogger().println("==>> Application '" + appName + "' was started");
    }

    private void stopArtifact(String appName, BuildListener listener, WebSphereDeploymentService service) throws Exception {
        if (service.isArtifactInstalled(appName)) {
            listener.getLogger().println("==>> Stopping Old Application '" + appName + "'...");
            service.stopArtifact(appName, listener, verbose);
            listener.getLogger().println("==>> Old Application '" + appName + "' was stopped.");
        }
    }

    private void updateArtifact(Artifact artifact, BuildListener listener, WebSphereDeploymentService service) throws Exception {
        if (service.isArtifactInstalled(artifact.getAppName())) {
            listener.getLogger().println("==>> Updating '" + artifact.getAppName() + "' on IBM WebSphere Application Server");
            service.updateArtifact(artifact, getDeploymentOptions(), getDeploymentTimeout(), listener);
            listener.getLogger().println("==>> Application '" + artifact.getAppName() + "' was updated on IBM WebSphere Application Server");
        }
    }

    private HashMap<String, Object> getDeploymentOptions() {
        HashMap<String, Object> options = new HashMap<String, Object>();
        options.put(AppConstants.APPDEPL_JSP_RELOADENABLED, isReloading());
        options.put(AppConstants.APPDEPL_PRECOMPILE_JSP, isPrecompile());
        return options;
    }

    private void connect(BuildListener listener, WebSphereDeploymentService service, EnvVars env) throws Exception {
        listener.getLogger().println("==>> Connecting to IBM WebSphere Application Server...");
        service.setConnectorType(getConnectorType());
        service.setHost(env.expand(getIpAddress()));
        service.setPort(env.expand(getPort()));
        service.setUsername(env.expand(getUsername()));
        service.setPassword(env.expand(getPassword()));
        service.setKeyStoreLocation(new File(env.expand(getClientKeyFile())));
        service.setKeyStorePassword(env.expand(getClientKeyPassword()));
        service.setTrustStoreLocation(new File(env.expand(getClientTrustFile())));
        service.setTrustStorePassword(env.expand(getClientTrustPassword()));
        service.connect();
        listener.getLogger().println("==>> Connected successesfully.");
    }

    private String getAppName(Artifact.Type type, File pathToArtifact, WebSphereDeploymentService service) {
        if (type == Artifact.Type.TYPE_EAR) {
            return service.getAppName(pathToArtifact.getAbsolutePath());
        } else {
            String filename = pathToArtifact.getName();
            return filename.substring(0, filename.lastIndexOf("."));
        }
    }

    private File generateEAR(Artifact artifact, BuildListener listener, WebSphereDeploymentService service) {
        if (isVerbose()) {
            listener.getLogger().println("Generating EAR For Artifact: " + artifact.getAppName());
        }
        final File modified = new File(artifact.getSourcePath().getParent(), artifact.getAppName() + ".ear");
        service.generateEAR(artifact, modified, getEarLevel());
        return modified;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
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

        public FormValidation doTestConnection(@QueryParameter("ipAddress") String ipAddress,
                @QueryParameter("connectorType") String connectorType,
                @QueryParameter("port") String port,
                @QueryParameter("username") String username,
                @QueryParameter("password") String password,
                @QueryParameter("clientKeyFile") String clientKeyFile,
                @QueryParameter("clientTrustFile") String clientTrustFile,
                @QueryParameter("clientKeyPassword") String clientKeyPassword,
                @QueryParameter("clientTrustPassword") String clientTrustPassword) throws IOException, ServletException {
            WebSphereDeploymentService service = new WebSphereDeploymentService(false);
            try {
                if (!service.isAvailable()) {
                    String destination = "<Jenkins_Root>" + File.separator + "plugins" + File.separator + "websphere-deployer" + File.separator + "WEB-INF" + File.separator + "lib" + File.separator;
                    return FormValidation.warning("Cannot find the required IBM WebSphere Application Server jar files in '" + destination + "'. Please copy them from IBM WebSphere Application Server (see plugin documentation)");
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
            if (value.length() == 0) {
                return FormValidation.error("Select a port");
            }
            if (value.length() > 5) {
                return FormValidation.warning("Cannot be greater than 65535!!!");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckAdminClientPath(@QueryParameter String value)
                throws IOException, ServletException {
            if (!new File(value).exists()) {
                return FormValidation.error("Path '" + value + "' is not found");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckOrbClientPath(@QueryParameter String value)
                throws IOException, ServletException {
            if (!new File(value).exists()) {
                return FormValidation.error("Path '" + value + "' is not found");
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Deploy To IBM WebSphere Application Server";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            adminClientPath = formData.getString("adminClientPath");
            orbClientPath = formData.getString("orbClientPath");
            save();
            return super.configure(req, formData);
        }

        public String getAdminClientPath() {
            return adminClientPath;
        }

        public String getOrbClientPath() {
            return orbClientPath;
        }

        public static ClassLoadOrder[] getAllClassLoadOrder() {
            return ClassLoadOrder.values();
        }

        public static WarClassLoaderPolicy[] getAllWarClassLoaderPolicy() {
            return WarClassLoaderPolicy.values();
        }

        public static ClassLoadOrder[] getAllClassLoadOrderWeb() {
            return ClassLoadOrder.values();
        }
    }
}
