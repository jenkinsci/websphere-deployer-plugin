package org.jenkinsci.plugins.websphere_deployer.services;

import java.util.Date;

/**
 * A J2EE application that defines state and other information.
 *
 * @author Greg Peters
 */
public class J2EEApplication {

    public static final int STATE_STARTING = 0;
    public static final int STATE_STARTED = 1;
    public static final int STATE_STOPPING = 2;
    public static final int STATE_STOPPED = 3;
    public static final int STATE_FAILED = 4;

    private String deploymentDescriptor;
    private String name;
    private int state;
    private Date startTime;

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDeploymentDescriptor() {
        return deploymentDescriptor;
    }

    public void setDeploymentDescriptor(String deploymentDescriptor) {
        this.deploymentDescriptor = deploymentDescriptor;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public boolean isStarted() {
        return this.state == STATE_STARTED;
    }

    public boolean isStopped() {
        return this.state == STATE_STOPPED;
    }

    public boolean isStarting() {
        return this.state == STATE_STARTING;
    }

    public boolean isStopping() {
        return this.state == STATE_STOPPING;
    }

    public boolean isFailed() {
        return this.state == STATE_FAILED;
    }
}
