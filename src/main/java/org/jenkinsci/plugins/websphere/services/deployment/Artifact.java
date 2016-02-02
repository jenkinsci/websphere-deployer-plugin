package org.jenkinsci.plugins.websphere.services.deployment;

import java.io.File;

public class Artifact {

    public enum Type {

        TYPE_EAR(1, ".ear"),
        TYPE_WAR(2, ".war"),
        TYPE_JAR(3, ".jar"),
        TYPE_RAR(4, ".rar"),
        UNKNOWN_TYPE(5, "");

        private final int id;
        private final String fileExtention;

        private Type(int id, String fileExtention) {
            this.id = id;
            this.fileExtention = fileExtention;
        }

        public int getId() {
            return id;
        }

        public String getFileExtention() {
            return fileExtention;
        }

        public static Type fromFileExtention(String ext) {
            for (Type t : values()) {
                if (ext.endsWith(t.getFileExtention())) {
                    return t;
                }
            }
            return UNKNOWN_TYPE;
        }
    }



    private final Type type;
    private final File sourcePath;
    private final String appName;
    private final String deployTarget;
    private final String webUri;
    private final boolean precompile;
    private final Integer startupOrder;
    private final WarClassLoaderPolicy warClassLoaderPolicy;
    private final ClassLoadOrder classLoadOrder;
    private final Integer startingWeightWeb;
    private final ClassLoadOrder classLoadOrderWeb;

    private Artifact(Type type, File sourcePath, String appName, String deployTarget, String webUri, boolean precompile,
            Integer startupOrder, WarClassLoaderPolicy warClassLoaderPolicy, ClassLoadOrder classLoadOrder, Integer startingWeightWeb,
            ClassLoadOrder classLoadOrderWeb) {
        this.type = type;
        this.sourcePath = sourcePath;
        this.appName = appName;
        this.deployTarget = deployTarget;
        this.webUri = webUri;
        this.precompile = precompile;
        this.startupOrder = startupOrder;
        this.warClassLoaderPolicy = warClassLoaderPolicy;
        this.classLoadOrder = classLoadOrder;
        this.startingWeightWeb = startingWeightWeb;
        this.classLoadOrderWeb = classLoadOrderWeb;
    }

    public static class Builder {

        private Type type = Type.UNKNOWN_TYPE;
        private File sourcePath;
        private String appName;
        private String deployTarget;
        private String webUri;
        private boolean precompile = false;
        private Integer startupOrder;
        private WarClassLoaderPolicy warClassLoaderPolicy = WarClassLoaderPolicy.DEFAULT;
        private ClassLoadOrder classLoadOrder = ClassLoadOrder.DEFAULT;
        private Integer startingWeightWeb;
        private ClassLoadOrder classLoadOrderWeb = ClassLoadOrder.DEFAULT;

        private Builder() {
        }
        
        public static Builder create() {
            return new Builder();
        }

        public Artifact build() {
            return new Artifact(type, sourcePath, appName, deployTarget, webUri, precompile,
                    startupOrder, warClassLoaderPolicy, classLoadOrder, startingWeightWeb, classLoadOrderWeb);
        }

        public Builder setType(Type type) {
            this.type = type;
            return this;
        }

        public Builder setSourcePath(File sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public Builder setAppName(String appName) {
            this.appName = appName;
            return this;
        }

        public Builder setDeployTarget(String deployTarget) {
            this.deployTarget = deployTarget;
            return this;
        }

        public Builder setWebUri(String webUri) {
            this.webUri = webUri;
            return this;
        }

        public Builder setPrecompile(boolean precompile) {
            this.precompile = precompile;
            return this;
        }

        public Builder setStartupOrder(Integer startupOrder) {
            this.startupOrder = startupOrder;
            return this;
        }

        public Builder setWarClassLoaderPolicy(WarClassLoaderPolicy warClassLoaderPolicy) {
            this.warClassLoaderPolicy = warClassLoaderPolicy;
            return this;
        }

        public Builder setClassLoadOrder(ClassLoadOrder classLoadOrder) {
            this.classLoadOrder = classLoadOrder;
            return this;
        }

        public Builder setStartingWeightWeb(Integer startingWeightWeb) {
            this.startingWeightWeb = startingWeightWeb;
            return this;
        }

        public Builder setClassLoadOrderWeb(ClassLoadOrder classLoadOrderWeb) {
            this.classLoadOrderWeb = classLoadOrderWeb;
            return this;
        }
    }

    public Integer getStartingWeightWeb() {
        return startingWeightWeb;
    }

    public ClassLoadOrder getClassLoadOrderWeb() {
        return classLoadOrderWeb;
    }

    public ClassLoadOrder getClassLoadOrder() {
        return classLoadOrder;
    }

    public WarClassLoaderPolicy getWarClassLoaderPolicy() {
        return warClassLoaderPolicy;
    }

    public Integer getStartupOrder() {
        return startupOrder;
    }

    public Type getType() {
        return type;
    }

    public File getSourcePath() {
        return sourcePath;
    }

    public String getAppName() {
        return appName;
    }

    public String getDeployTarget() {
        return deployTarget;
    }

    public String getWebUri() {
        return webUri;
    }

    public boolean isPrecompile() {
        return precompile;
    }

}
