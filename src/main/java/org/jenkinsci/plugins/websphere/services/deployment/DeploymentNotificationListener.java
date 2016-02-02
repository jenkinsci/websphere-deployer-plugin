package org.jenkinsci.plugins.websphere.services.deployment;

import hudson.model.BuildListener;

import java.util.Properties;
import java.util.logging.Logger;

import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.application.AppNotification;
import com.ibm.websphere.management.exception.ConnectorException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;

public class DeploymentNotificationListener implements NotificationListener {

    private static Logger LOG = Logger.getLogger(DeploymentNotificationListener.class.getName());
    private final AdminClient adminClient;
    private final NotificationFilterSupport filterSupport;
    private final ObjectName objectName;
    private final List<String> eventTypeToCheck;
    private boolean successful = true;
    private String message = "";
    private AtomicReference<Properties> notificationProps = new AtomicReference<Properties>();
    private final BuildListener listener;
    private final Object handBack;
    private final boolean isVerbose;
    private final CountDownLatch lautch = new CountDownLatch(1);

    public static DeploymentNotificationListener createListener(AdminClient adminClient, NotificationFilterSupport support,
            Object handBack, String eventTypeToCheck, BuildListener listener, boolean isVerbose)
            throws ConnectorException, MalformedObjectNameException, InstanceNotFoundException {
        final DeploymentNotificationListener result
                = new DeploymentNotificationListener(adminClient, support, handBack, Arrays.asList(eventTypeToCheck), listener, isVerbose);
        result.subscribe();
        return result;
    }

    private DeploymentNotificationListener(AdminClient adminClient, NotificationFilterSupport support,
            Object handBack, List<String> eventTypeToCheck, BuildListener listener, boolean isVerbose)
            throws ConnectorException, MalformedObjectNameException {
        super();
        this.adminClient = adminClient;
        this.filterSupport = support;
        this.eventTypeToCheck = Collections.unmodifiableList(new ArrayList<String>(eventTypeToCheck));
        this.listener = listener;
        this.objectName = (ObjectName) adminClient.queryNames(new ObjectName("WebSphere:type=AppManagement,*"), null).iterator().next();
        this.handBack = handBack;
        this.isVerbose = isVerbose;
    }

    private void subscribe() throws InstanceNotFoundException, ConnectorException {
        adminClient.addNotificationListener(objectName, this, filterSupport, handBack);
    }

    public void unsubscribe() {
        try {
            adminClient.removeNotificationListener(objectName, this);
        } catch (Exception e) {
        }
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        AppNotification appNotification = (AppNotification) notification.getUserData();
        if (isVerbose) {
            listener.getLogger().println(appNotification.taskName + "] " + appNotification.message + "[" + appNotification.taskStatus + "]");
        }
        message += ("\n" + appNotification.message);
        if ((eventTypeToCheck.contains(appNotification.taskName)
                && appNotification.taskStatus.equals(AppNotification.STATUS_COMPLETED)
                || appNotification.taskStatus.equals(AppNotification.STATUS_FAILED))) {
            try {
                adminClient.removeNotificationListener(objectName, this);
                if (appNotification.taskStatus.equals(AppNotification.STATUS_FAILED)) {
                    successful = false;
                } else {
                    notificationProps.set(appNotification.props);
                }
            } catch (Exception e) {
                listener.getLogger().println("Can`t remove listener: " + e.toString());
            } finally {
                lautch.countDown();
            }
        }
    }

    public String getMessage() {
        return message;
    }

    public Properties getNotificationProps() {
        return notificationProps.get();
    }

    public boolean isSuccessful() {
        return successful;
    }

    public AdminClient getAdminClient() {
        return adminClient;
    }

    public NotificationFilterSupport getFilterSupport() {
        return filterSupport;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public void await(TimeUnit timeUnit, int deploymentTimeout) throws InterruptedException {
        lautch.await(deploymentTimeout, timeUnit);
    }
}
