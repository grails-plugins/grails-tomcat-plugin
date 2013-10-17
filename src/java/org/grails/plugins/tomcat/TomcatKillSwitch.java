package org.grails.plugins.tomcat;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.codehaus.groovy.grails.plugins.GrailsVersionUtils;

import java.io.IOException;
import java.net.ServerSocket;

/**
 *  Allows the tomcat server to be killed by pinging a port one greater than the server port.
 *
 *  @author Graeme Rocher
 *  @since 2.1
 */
public class TomcatKillSwitch implements Runnable {

    public static final String TOMCAT_KILL_SWITCH_ACTIVE = "TomcatKillSwitch.active";

    private Tomcat tomcat;
    private int serverPort;
    private String grailsVersion;

    public TomcatKillSwitch(Tomcat tomcat, int serverPort) {
        this.tomcat = tomcat;
        this.serverPort = serverPort;
    }

    public TomcatKillSwitch(Tomcat tomcat, int serverPort, String grailsVersion) {
        this(tomcat, serverPort);
        this.grailsVersion = grailsVersion;
    }

    public static boolean isActive() {
        return Boolean.getBoolean("TomcatKillSwitch.active");
    }

    public void run() {
        System.setProperty("TomcatKillSwitch.active", "true");
        int killPortOffset = isRandomPortSupported(grailsVersion) ? -1 : 1;
        int killListenerPort = serverPort + killPortOffset;
        ServerSocket serverSocket = createKillSwitch(killListenerPort);
        if (serverSocket != null) {
            try {
                serverSocket.accept();
                try {
                    tomcat.stop();
                    tomcat.destroy();
                    System.setProperty(TOMCAT_KILL_SWITCH_ACTIVE, "false");
                    System.exit(0);
                } catch (LifecycleException e) {
                    System.err.println("Error stopping Tomcat: " + e.getMessage());
                    System.exit(1);
                }
            } catch (IOException e) {
                // just exit
            }
        }
    }

    private static ServerSocket createKillSwitch(int killListenerPort) {
        try {
            return new ServerSocket(killListenerPort);
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean isRandomPortSupported(String grailsVersion) {
        if (grailsVersion == null) {
            return false;
        } else {
            return GrailsVersionUtils.isValidVersion(grailsVersion, "2.3.2 > *");
        }
    }
}
