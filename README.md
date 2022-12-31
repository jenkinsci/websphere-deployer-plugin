Websphere Deployer Plugin (For Jenkins)
=========================

NOTE: Do to the nature of how WebSphere jar files are compiled. Any Jenkins runtime that uses Java Versions greater than 8+ will not work with this plugin. This is directly related to how Jenkins enforces Java standards when compiling this plugin. If your Jenkins server uses a version greater than Java 8, this plugin will not function because the underlying WebSphere jar files are compiled with Java 8 or less. There are no plans to update this plugin because this plugin is at the mercy of the WebSphere Jars to interact with WebSphere.

Details on this plugin can be found here: https://wiki.jenkins-ci.org/display/JENKINS/WebSphere+Deployer+Plugin

This plugin is designed to deploy to WebSphere Full Profile, WebSphere Portal and WebSphere Liberty Profile.

<ol>
  <li>Global Security</li>
  <li>WebSphere Liberty Profile</li>
  <li>WebSphere Application Server</li>
  <li>WebSphere Portal Server</li>
  <li>SOAP Connectivity</li>
  <li>Automatic EAR generation from WAR</li>
  <li>Cluster Deployments</li>
  <li>Standalone Server Deployments</li>
  <li>Single Deployment Manager for multiple environments (DEV,TEST,QA,UAT,PRE-PROD,PROD, etc...)</li>
  <li>Full synchronization on cluster deployments</li>
  <li>Automated rollback on deployment or startup errors</li>
</ol>
