package org.jenkinsci.plugins.websphere.services.deployment;

import java.io.File;

public class Artifact {

    public static final int TYPE_EAR = 1;
    public static final int TYPE_WAR = 2;
    public static final int TYPE_JAR = 3;
    public static final int TYPE_RAR = 4;
    private File sourcePath;
    private String appName;
    private int type;
    private boolean precompile;

    public boolean isPrecompile() {
        return precompile;
    }

    public void setPrecompile(boolean precompile) {
        this.precompile = precompile;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getType() {
        return this.type;
    }

    public File getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(File sourcePath) {
        this.sourcePath = sourcePath;
    }
}
