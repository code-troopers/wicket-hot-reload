package codetroopers.wicket;

import codetroopers.wicket.web.HotReloadingUtils;
import org.apache.wicket.util.time.Duration;
import org.eclipse.jetty.http.ssl.SslContextFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;

public class Start {
    public static void main(String[] args) throws Exception {
        int timeout = (int) Duration.ONE_HOUR.getMilliseconds();

        Server server = new Server();
        SocketConnector connector = new SocketConnector();

        // Set some timeout options to make debugging easier.
        connector.setMaxIdleTime(timeout);
        connector.setSoLingerTime(-1);
        connector.setPort(8081);
        server.addConnector(connector);

        // check if a keystore for a SSL certificate is available, and
        // if so, start a SSL connector on port 8443. By default, the
        // quickstart comes with a Apache Wicket Quickstart Certificate
        // that expires about half way september 2021. Do not use this
        // certificate anywhere important as the passwords are available
        // in the source.

        Resource keystore = Resource.newClassPathResource("/keystore");
        if (keystore != null && keystore.exists()) {
            connector.setConfidentialPort(8443);

            SslContextFactory factory = new SslContextFactory();
            factory.setKeyStoreResource(keystore);
            factory.setKeyStorePassword("wicket");
            factory.setTrustStoreResource(keystore);
            factory.setKeyManagerPassword("wicket");
            SslSocketConnector sslConnector = new SslSocketConnector(factory);
            sslConnector.setMaxIdleTime(timeout);
            sslConnector.setPort(8443);
            sslConnector.setAcceptors(4);
            server.addConnector(sslConnector);

            System.out.println("SSL access to the quickstart has been enabled on port 8443");
            System.out.println("You can access the application using SSL on https://localhost:8443");
            System.out.println();
        }

        WebAppContext bb = new WebAppContext();
        bb.setServer(server);
        bb.setContextPath("/");
        bb.setWar("src/test/webapp");

        // START JMX SERVER
        // MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        // MBeanContainer mBeanContainer = new MBeanContainer(mBeanServer);
        // server.getContainer().addEventListener(mBeanContainer);
        // mBeanContainer.start();

        server.setHandler(bb);
        
        //TOGGLE between these two method to experiment different reloading strategies
        // Autoreload builds and reload the classes automatically
        setAutoReloadProperties();
        // WatchClasses watch the target dir for reloading (build with your IDE or maven)
        //setWatchClassesProperties();
        try {
            System.out.println(">>> STARTING EMBEDDED JETTY SERVER, PRESS ANY KEY TO STOP");
            server.start();
            System.in.read();
            System.out.println(">>> STOPPING EMBEDDED JETTY SERVER");
            server.stop();
            server.join();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void setAutoReloadProperties() {
        // Reload We set autoreload to true
        System.setProperty(HotReloadingUtils.KEY_AUTO, "true");
        // Reload We set the source location to src/test/java as our classes in this examples are in test sources
        System.setProperty(HotReloadingUtils.KEY_SOURCES, "src/test/java");
        System.setProperty(HotReloadingUtils.KEY_ROOTPKG, "codetroopers.wicket.page");
    }
    
    private static void setWatchClassesProperties() {
        System.setProperty(HotReloadingUtils.KEY_ENABLED, "true");
        // Reload We set the source location to src/test/java as our classes in this examples are in test sources
        //System.setProperty("wicket.hotreload.sourceRoots", "src/test/java");
        System.setProperty(HotReloadingUtils.KEY_TARGET, "target/test-classes");
        System.setProperty(HotReloadingUtils.KEY_ROOTPKG, "codetroopers.wicket.page");
    }
}

