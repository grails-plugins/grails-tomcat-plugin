/*
 * Copyright 2013-2016 the original author or authors.
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
package org.grails.plugins.tomcat.fork

import grails.build.logging.GrailsConsole
import grails.util.Metadata
import groovy.transform.CompileStatic

import org.apache.catalina.Context
import org.apache.catalina.LifecycleException
import org.apache.catalina.startup.Tomcat
import org.grails.plugins.tomcat.TomcatServer

/**
 * A Tomcat runner that runs a WAR file
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
class TomcatWarRunner extends TomcatServer {

	protected static final GrailsConsole CONSOLE = GrailsConsole.getInstance()

	protected String warPath
	protected String contextPath

	TomcatWarRunner(String warPath, String contextPath) {
		this.warPath = warPath
		this.contextPath = contextPath
	}

	@Override
	protected void doStart(String host, int httpPort, int httpsPort) {

		Metadata.getCurrent()[Metadata.WAR_DEPLOYED] = "true"
		tomcat.silent = true

		try {
			context = tomcat.addWebapp(contextPath, warPath)
		}
		catch (Throwable e) {
			CONSOLE.error "Error loading Tomcat: $e.message", e
			System.exit 1
		}

		super.doStart host, httpPort, httpsPort

		ForkedTomcatServer.startKillSwitch tomcat, httpPort

		try {
			tomcat.start()
			CONSOLE.addStatus "Server running. Browse to http://${host ?: "localhost"}:$httpPort$contextPath"
		}
		catch (LifecycleException e) {
			CONSOLE.error "Error loading Tomcat: $e.message", e
			System.exit 1
		}
	}

	@Override
	void stop() {
		tomcat.stop()
	}
}
