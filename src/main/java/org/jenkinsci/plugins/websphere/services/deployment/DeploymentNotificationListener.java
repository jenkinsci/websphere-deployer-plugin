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

public class DeploymentNotificationListener implements NotificationListener
{
   private static Logger log = Logger.getLogger(DeploymentNotificationListener.class.getName());
   private AdminClient adminClient;
   private NotificationFilterSupport filterSupport;
   private ObjectName objectName;
   private String eventTypeToCheck;
   private boolean successful = true;
   private String message = "";
   private Properties notificationProps = new Properties();
   private BuildListener listener;
   private boolean verbose;
   private boolean eventTriggered;

   public DeploymentNotificationListener(AdminClient adminClient, NotificationFilterSupport support, Object handBack, String eventTypeToCheck,BuildListener listener,boolean verbose) throws Exception {
      super();
      this.adminClient = adminClient;
      this.filterSupport = support;
      this.eventTypeToCheck = eventTypeToCheck;
      this.listener = listener;
      this.verbose = verbose;
      this.objectName = (ObjectName) adminClient.queryNames(new ObjectName("WebSphere:type=AppManagement,*"), null).iterator().next();
      adminClient.addNotificationListener(objectName, this, filterSupport, handBack);
   }

   public void handleNotification(Notification notification, Object handback) {
      AppNotification appNotification = (AppNotification) notification.getUserData();
      if(verbose) {
    	  listener.getLogger().println(appNotification.taskName+"] "+appNotification.message+"["+appNotification.taskStatus+"]");
      }
      message += ("\n" + appNotification.message);
      if (appNotification.taskName.equals(eventTypeToCheck) && (appNotification.taskStatus.equals(AppNotification.STATUS_COMPLETED) || appNotification.taskStatus.equals(AppNotification.STATUS_FAILED))) {
			try {
				adminClient.removeNotificationListener(objectName, this);
				if (appNotification.taskStatus.equals(AppNotification.STATUS_FAILED)) {
					successful = false;
				} else {
					notificationProps = appNotification.props;
				}

				synchronized(this) {
					eventTriggered = true;
					this.notify();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
      }
   }
   
   public boolean hasEventTriggered() {
	   return eventTriggered;
   }

	public String getMessage() {
		return message;
	}

	public Properties getNotificationProps() {
		return notificationProps;
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
   
   
}