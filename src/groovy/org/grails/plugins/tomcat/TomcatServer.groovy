/*
 * Copyright 2011-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.tomcat

import static grails.build.logging.GrailsConsole.instance as CONSOLE
import grails.util.BuildSettings
import grails.util.BuildSettingsHolder
import grails.util.PluginBuildSettings
import grails.web.container.EmbeddableServer
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import org.apache.catalina.Context
import org.apache.catalina.connector.Connector
import org.apache.catalina.core.AprLifecycleListener
import org.apache.catalina.startup.Tomcat
import org.apache.coyote.http11.Http11NioProtocol
import org.apache.tomcat.util.scan.StandardJarScanner
import org.codehaus.groovy.grails.cli.support.GrailsBuildEventListener
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils
import org.springframework.util.ReflectionUtils

/**
 * Provides common functionality for the inline and isolated variants of tomcat server.
 *
 * @author Graeme Rocher
 * @see org.grails.plugins.tomcat.fork.TomcatWarRunner
 * @see org.grails.plugins.tomcat.fork.TomcatDevelopmentRunner
 */
@CompileStatic
abstract class TomcatServer implements EmbeddableServer {

	private static final int NULL_INT = Integer.MIN_VALUE

	protected final BuildSettings buildSettings
	protected final PluginBuildSettings pluginSettings

	protected final File workDir
	protected final File tomcatDir

	protected boolean usingUserKeystore
	protected File keystoreFile
	protected String keyPassword
	protected String truststore
	protected File truststoreFile
	protected String trustPassword
	protected Boolean shouldScan = false
	protected Set<String> extraJarsToSkip

	Context context
	final Tomcat tomcat = new Tomcat()

	// These are set from the outside in _GrailsRun
	def grailsConfig
	GrailsBuildEventListener eventListener

	TomcatServer() {
		buildSettings = BuildSettingsHolder.getSettings()
		pluginSettings = GrailsPluginUtils.getPluginBuildSettings()

		workDir = buildSettings.projectWorkDir
		tomcatDir = getWorkDirFile('tomcat')
		tomcat.baseDir = tomcatDir.absolutePath

		initKeystore()

		System.setProperty 'org.mortbay.xml.XmlParser.NotValidating', 'true'

		Map scanConfig = (Map)getConfigParam('scan')
		if (scanConfig) {
			shouldScan = (Boolean) (scanConfig.enabled instanceof Boolean ? scanConfig.enabled : false)
			extraJarsToSkip = (Set)((scanConfig.excludes instanceof Collection) ? scanConfig.excludes.collect { it.toString() } : [])
		}

		tomcatDir.deleteDir()
		new File(tomcatDir, 'webapps').mkdirs()

		initListeners()
	}

	protected void initKeystore() {
		def userKeystore = getConfigParam('keystorePath')
		if (userKeystore) {
			usingUserKeystore = true
			keystoreFile = new File(userKeystore.toString())
			keyPassword = getConfigParam('keystorePassword') ?: 'changeit' // changeit is the keystore default
		}
		else {
			usingUserKeystore = false
			keystoreFile = getWorkDirFile('ssl/keystore')
			keyPassword = '123456'
		}

		def userTruststore = getConfigParam('truststorePath')
		if (userKeystore) {
			truststore = userTruststore
			trustPassword = getConfigParam('truststorePassword') ?: 'changeit'
		}
		else {
			truststore = "${buildSettings.grailsWorkDir}/ssl/truststore"
			trustPassword = '123456'
		}

		truststoreFile = new File(truststore)
	}

	protected void initListeners() {
		tomcat.server.addLifecycleListener new AprLifecycleListener(SSLEngine: 'on', useAprConnector: true)
	}

	@CompileStatic(TypeCheckingMode.SKIP)
	protected void configureSsl(String host, int httpsPort) {
		def sslConnector
		try {
			sslConnector = loadInstance('org.apache.catalina.connector.Connector')
		}
		catch (Exception e) {
			throw new RuntimeException("Couldn't create HTTPS connector", e)
		}

		sslConnector.scheme = 'https'
		sslConnector.secure = true
		sslConnector.port = httpsPort
		sslConnector.setProperty 'SSLEnabled', 'true'
		sslConnector.URIEncoding = 'UTF-8'

		if (host != 'localhost') {
			sslConnector.setAttribute 'address', host
		}

		def certificateKeyFile = getConfigParam('certificateKeyFile') ?: ''
		def certificateFile = getConfigParam('certificateFile') ?: ''
		if (certificateKeyFile && certificateFile) {
			sslConnector.setAttribute 'SSLHonorCipherOrder', false
			sslConnector.setAttribute 'SSLCertificateKeyFile', certificateKeyFile
			sslConnector.setAttribute 'SSLCertificateFile', certificateFile
			def certificateKeyPassword = getConfigParam('certificateKeyPassword') ?: ''
			if (certificateKeyPassword) {
				sslConnector.setAttribute 'SSLPassword', certificateKeyPassword
			}
		}
		else {
			sslConnector.setAttribute 'keystoreFile', keystoreFile.absolutePath
			sslConnector.setAttribute 'keystorePass', keyPassword

			if (truststoreFile.exists()) {
				CONSOLE.addStatus "Using truststore $truststore"
				sslConnector.setAttribute 'truststoreFile', truststore
				sslConnector.setAttribute 'truststorePass', trustPassword
				sslConnector.setAttribute 'clientAuth', getConfigParam('clientAuth') ?: 'want'
			}
		}

		sslConnector.addUpgradeProtocol loadInstance('org.apache.coyote.http2.Http2Protocol')

		tomcat.service.addConnector sslConnector
	}

	protected loadInstance(String name) {
		tomcat.getClass().classLoader.loadClass(name).newInstance()
	}

	protected void configureJarScanner(Context context) {
		if (extraJarsToSkip && shouldScan) {
			try {
				def jarsToSkipField = ReflectionUtils.findField(StandardJarScanner, 'defaultJarsToSkip', Set)
				ReflectionUtils.makeAccessible jarsToSkipField
				Set jarsToSkip = (Set)jarsToSkipField.get(StandardJarScanner)
				jarsToSkip.addAll extraJarsToSkip
			}
			catch (ignored) {}
		}

		context.jarScanner = new StandardJarScanner(scanClassPath: shouldScan)
	}

	/**
	 * The host and port params will never be null, defaults will be passed if necessary.
	 *
	 * If httpsPort is > 0, the server should listen for https requests on that port.
	 */
	protected void doStart(String host, int httpPort, int httpsPort) {
		tomcat.port = httpPort

		if (getConfigParam("nio")) {
			CONSOLE.updateStatus "Enabling Tomcat NIO Connector"
			def connector = new Connector(Http11NioProtocol.name)
			connector.port = httpPort
			tomcat.service.addConnector connector
			tomcat.connector = connector
		}

		try {
			configureJarScanner context
		}
		catch (Throwable e) {
			CONSOLE.error "Error loading Tomcat: $e.message", e
			System.exit 1
		}

		tomcat.enableNaming()

		final Connector connector = tomcat.connector

		// Only bind to host name if we aren't using the default
		if (host != "localhost") {
			connector.setAttribute "address", host
			connector.setAttribute "port", httpPort
		}

		connector.URIEncoding = "UTF-8"

		if (httpsPort) {
			configureSsl host, httpsPort
		}
	}

	/**
	 * Shutdown the server.
	 */
	abstract void stop()

	void restart() {
		stop()
		start()
	}

	void start() {
		start null, NULL_INT
	}

	void start(int port) {
		start null, port
	}

	void start(String host, int port) {
		doStart host ?: DEFAULT_HOST, port ?: DEFAULT_PORT, 0
	}

	void startSecure() {
		startSecure null, NULL_INT, NULL_INT
	}

	void startSecure(int port) {
		startSecure null, NULL_INT, port
	}

	void startSecure(String host, int httpPort, int httpsPort) {
		if (!keystoreFile.exists()) {
			if (usingUserKeystore) {
				throw new IllegalStateException("cannot start tomcat in https because use keystore does not exist (value: $keystoreFile)")
			}
			else {
				createSSLCertificate()
			}
		}

		doStart host ?: DEFAULT_HOST, (httpPort && httpPort != NULL_INT) ? httpPort : DEFAULT_PORT,
				(httpsPort && httpsPort != NULL_INT) ? httpsPort : DEFAULT_SECURE_PORT
	}

	protected File getWorkDirFile(String path) {
		new File(workDir, path)
	}

	@CompileStatic(TypeCheckingMode.SKIP)
	protected getConfigParam(String name) {
		buildSettings.config.grails.tomcat[name]
	}

	@CompileStatic(TypeCheckingMode.SKIP)
	protected Map getConfigParams() {
		buildSettings.config.grails.tomcat
	}

	@CompileStatic(TypeCheckingMode.SKIP)
	protected createSSLCertificate() {
		CONSOLE.updateStatus 'Creating SSL Certificate...'

		def keystoreDir = keystoreFile.parentFile
		if (!keystoreDir.exists() && !keystoreDir.mkdir()) {
			throw new RuntimeException("Unable to create keystore folder: $keystoreDir.canonicalPath")
		}

		getKeyToolClass().main(
				'-genkey',
				'-alias', 'localhost',
				'-dname', 'CN=localhost,OU=Test,O=Test,C=US',
				'-keyalg', 'RSA',
				'-validity', '365',
				'-storepass', 'key',
				'-keystore', keystoreFile.absolutePath,
				'-storepass', keyPassword,
				'-keypass', keyPassword)

		println 'Created SSL Certificate.'
	}

	protected getKeyToolClass() {
		try {
			// Sun JDK 8
			Class.forName 'sun.security.tools.keytool.Main'
		}
		catch (ClassNotFoundException e1) {
			try {
				// Sun pre-JDK 8
				Class.forName 'sun.security.tools.KeyTool'
			}
			catch (ClassNotFoundException e2) {
				// no try/catch for this one, if neither is found let it fail
				Class.forName 'com.ibm.crypto.tools.KeyTool'
			}
		}
	}
}
