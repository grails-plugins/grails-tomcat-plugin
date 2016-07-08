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

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.codehaus.groovy.grails.io.support.Resource
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils
import org.grails.plugins.tomcat.InlineExplodedTomcatServer

/**
 * @author Graeme Rocher
 */
@CompileStatic
class TomcatDevelopmentRunner extends InlineExplodedTomcatServer {

	protected String currentHost
	protected int currentPort

	TomcatDevelopmentRunner(String basedir, String webXml, String contextPath, ClassLoader classLoader) {
		super(basedir, webXml, contextPath, classLoader)
	}

	@Override
	@CompileStatic
	protected void initialize(Tomcat tomcat) {
		final autodeployDir = buildSettings.autodeployDir
		if (autodeployDir.exists()) {
			for (File f in autodeployDir.listFiles()) {
				if (f.name.endsWith(".war")) {
					configureJarScanner tomcat.addWebapp(f.name - '.war', f.absolutePath)
				}
			}
		}

		invokeCustomizer tomcat
	}

	@CompileStatic(TypeCheckingMode.SKIP)
	protected void invokeCustomizer(Tomcat tomcat) {
		Class cls
		try { cls = forkedClassLoader.loadClass("org.grails.plugins.tomcat.ForkedTomcatCustomizer") }
		catch (Throwable ignored) {}

		if (cls) {
			try {
				cls.newInstance().customize tomcat
			}
			catch (e) {
				throw new RuntimeException("Error invoking Tomcat server customizer: $e.message", e)
			}
		}
	}

	@Override
	protected void configureAliases(Context context) {
		def aliases = [:]
		for (Resource dir in GrailsPluginUtils.getPluginDirectories()) {
			def webappDir = new File(dir.file.absolutePath, "web-app")
			if (webappDir.exists()) {
				aliases["/plugins/$dir.file.name"] = webappDir.absolutePath
			}
		}
		registerAliases aliases
	}

	@Override
	void start(String host, int port) {
		currentHost = host
		currentPort = port
		super.start host, port
	}

	@Override
	void stop() {
		try { new URL("http://${currentHost}:${currentPort + 1}").text }
		catch (ignored) {}
	}
}
