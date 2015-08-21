package org.jenkinsci.plugins.websphere.services.deployment;

import java.io.File;

public class Artifact {

    public static final int TYPE_EAR = 1;
    public static final int TYPE_WAR = 2;
    public static final int TYPE_JAR = 3;
    public static final int TYPE_RAR = 4;
    private File sourcePath;
    private String appName;
    private String context;
    private String targets;
    private int type;
    private boolean distribute;
    private boolean precompile;
    private boolean jspReloading;
    private boolean reloading;
    private String installPath;
    private String classLoaderOrder;
    private String classLoaderPolicy;
    
    public String getTypeName() {
    	switch(type) {
	    	case TYPE_EAR: {
	    		return "ear";	
	    	}
	    	case TYPE_WAR: {
	    		return "war";
	    	}
	    	case TYPE_JAR: {
	    		return "jar";
	    	}
	    	case TYPE_RAR: {
	    		return "rar";
	    	}
	    	default: {
	    		return "ear";
	    	}
    	}
    }
    
	public String getClassLoaderOrder() {
		return classLoaderOrder;
	}

	public void setClassLoaderOrder(String classLoaderOrder) {
		this.classLoaderOrder = classLoaderOrder;
	}

	public String getClassLoaderPolicy() {
		return classLoaderPolicy;
	}

	public void setClassLoaderPolicy(String classLoaderPolicy) {
		this.classLoaderPolicy = classLoaderPolicy;
	}

	public boolean isReloading() {
		return reloading;
	}

	public void setReloading(boolean reloading) {
		this.reloading = reloading;
	}

	public String getTargets() {
		return targets;
	}

	public void setTargets(String targets) {
		this.targets = targets;
	}

	public boolean isJspReloading() {
		return jspReloading;
	}

	public void setJspReloading(boolean jspReloading) {
		this.jspReloading = jspReloading;
	}

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

	public String getContext() {
		return context;
	}

	public void setContext(String context) {
		this.context = context;
	}

	public boolean isDistribute() {
		return distribute;
	}

	public void setDistribute(boolean distribute) {
		this.distribute = distribute;
	}
	
	public String getInstallPath() {
		return installPath;
	}
	
	public void setInstallPath(String installPath) {
		this.installPath = installPath;
	}  		
}
