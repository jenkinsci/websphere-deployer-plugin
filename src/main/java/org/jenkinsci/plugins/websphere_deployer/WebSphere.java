package org.jenkinsci.plugins.websphere_deployer;

import org.jenkinsci.plugins.websphere_deployer.services.WebSphereAdminService;
import org.jenkinsci.plugins.websphere_deployer.services.WebSphereAdminServiceImpl;

/**
 * A singleton for creating a link to WebSphere.
 *
 * @author Greg Peters
 */
public class WebSphere {

    private static final WebSphereAdminServiceImpl instance = new WebSphereAdminServiceImpl();

    private WebSphere() {}

    public static WebSphereAdminService getInstance() {
        return instance;
    }
}
