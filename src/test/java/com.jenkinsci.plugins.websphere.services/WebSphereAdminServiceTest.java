package com.jenkinsci.plugins.websphere.services;

import com.ibm.ws.management.application.client.ListModules;
import junit.framework.Assert;
import org.jenkinsci.plugins.websphere_deployer.services.*;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Tests WebSphere services.
 *
 * Note: You must specify valid values for your environment to these tests to work. See the getDeployable method
 * for more information.
 *
 * @author Greg Peters
 */
public class WebSphereAdminServiceTest {

    private WebSphereAdminServiceImpl service;

    @BeforeTest
    private void connect() {
        service = new WebSphereAdminServiceImpl();
        service.connect(getDeployable());
    }

    private Deployable getDeployable() {
        Deployable deployable = new Deployable();
        deployable.setEarPath("/home/user/Desktop/wasadmin/myapp.ear");
        deployable.setAppName("APP");
        deployable.setClientKeyFile("/home/user/Desktop/wasadmin/DummyClientKeyFile.jks");
        deployable.setClientKeyPassword("WebAS");
        deployable.setClientTrustFile("/home/user/Desktop/wasadmin/DummyClientTrustFile.jks");
        deployable.setClientTrustPassword("WebAS");
        deployable.setHost("192.168.1.3");
        deployable.setUsername("user");
        deployable.setPassword("password");
        deployable.setTargetServer("server1");
        deployable.setTargetNode("serverNode01");
        deployable.setTargetCell("serverNode01Cell");
        return deployable;
    }

    @Test
    public void installApplication() throws Exception {
        service.installApplication(getDeployable());
    }

    @Test(dependsOnMethods = {"listModules","listURIs","checkIfApplicationExists"})
    public void uninstallApplication() throws Exception {
        service.uninstallApplication("APP");
    }

    @Test(dependsOnMethods = {"installApplication"})
    public void startApplication() throws Exception {
        service.startApplication("APP");
    }

    @Test(dependsOnMethods = {"startApplication"})
    public void stopApplication() throws Exception {
        service.stopApplication("APP");
    }

    @Test(dependsOnMethods = {"installApplication"})
    public void listApplications() throws Exception {
        List<J2EEApplication> applications = service.listApplications();
        Assert.assertTrue(applications.size() > 0);
    }

    @Test(dependsOnMethods = {"installApplication"})
    public void checkIfApplicationExists() throws Exception {
        Assert.assertTrue(service.isInstalled("APP"));
    }

    @Test
    public void listJVMs() throws Exception {
        List<JVM> jvms = service.listJVMs();
        for(JVM jvm:jvms) {
            System.out.println("JVM INFO");
            System.out.println("Free Memory: "+jvm.getFreeMemory());
            System.out.println("Max Memory: "+jvm.getMaxMemory());
        }
    }

    @Test
    public void listServers() throws Exception {
        List<Server> servers = service.listServers();
        for(Server server:servers) {
            System.out.println("Name: "+server.getServerName());
            System.out.println("Cell Name: "+server.getCellName());
            System.out.println("Node Name: "+server.getNodeName());
            System.out.println("Process Id: "+server.getProcessId());
            System.out.println("Server Vendor: "+server.getServerVendor());
            System.out.println("Server Version: "+server.getServerVersion());
            System.out.println("---------------------------------");
        }
        Assert.assertTrue(servers.size() > 0);
    }

    @Test(dependsOnMethods = {"installApplication"})
    public void listModules() throws Exception {
        ListModules modules = service.listModules("APP");
        Assert.assertNotNull(modules);
    }

    @Test(dependsOnMethods = {"installApplication"})
    public void listURIs() throws Exception {
        List<String> uris = service.listURIs("APP");
        Assert.assertTrue(uris.size() > 0);
    }
}
