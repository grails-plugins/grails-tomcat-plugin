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
import grails.util.Environment
import grails.util.GrailsNameUtils
import grails.util.Holders
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import org.apache.catalina.Context
import org.apache.catalina.Loader
import org.apache.catalina.WebResourceRoot
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.webresources.StandardRoot
import org.apache.tomcat.util.descriptor.web.ContextResource
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils
import org.grails.plugins.tomcat.fork.ForkedTomcatServer

/**
 * Serves the app, without packaging as a war and runs it in the same JVM.
 */
@CompileStatic
class InlineExplodedTomcatServer extends TomcatServer {

	InlineExplodedTomcatServer(String basedir, String webXml, String contextPath, ClassLoader classLoader) {

		if (contextPath == '/') {
			contextPath = ''
		}

		context = tomcat.addWebapp(contextPath, basedir)

		// we handle reloading manually
		context.reloadable = false
		context.altDDName = getWorkDirFile("resources/web.xml").absolutePath

		configureAliases context

		def loader = createTomcatLoader(classLoader)
		loader.context = context
		context.loader = loader
		initialize tomcat
	}

	protected void initialize(Tomcat tomcat) {
		// do nothing, for subclasses to override
	}

	protected void configureAliases(Context context) {
		def aliases = new HashMap<String, String>()

		for (plugin in Holders.getPluginManager()?.userPlugins) {
			def dir = pluginSettings.getPluginDirForName(GrailsNameUtils.getScriptName(plugin.name))
			File webappDir = dir ? new File(dir.file.absolutePath, "web-app") : null
			if (webappDir?.exists()) {
				aliases["/plugins/$plugin.fileSystemName".toString()] = webappDir.absolutePath
			}
		}
		registerAliases aliases
	}

	protected void registerAliases(Map<String, String> aliases) {
		if (!aliases) {
			return
		}

		WebResourceRoot resources = context.resources
		if (!resources) {
			resources = new StandardRoot(context)
			context.resources = resources
		}

		aliases.each { String alias, String realPath ->
			URL realPathUrl = new File(realPath).toURI().toURL()
			resources.createWebResourceSet WebResourceRoot.ResourceSetType.POST, alias, realPathUrl, '/'
		}
	}

	protected Loader createTomcatLoader(ClassLoader classLoader) {
		new TomcatLoader(classLoader)
	}

	void doStart(String host, int httpPort, int httpsPort) {
		preStart()

		super.doStart host, httpPort, httpsPort

		if (Environment.isFork()) {
			ForkedTomcatServer.startKillSwitch tomcat, httpPort
		}

		tomcat.start()
	}

	void stop() {
		tomcat.stop()
		tomcat.destroy()
		GrailsPluginUtils.clearCaches()
	}

	@CompileStatic(TypeCheckingMode.SKIP)
	protected void preStart() {
		eventListener?.triggerEvent "ConfigureTomcat", tomcat

		def jndiEntries = grailsConfig?.grails?.naming?.entries
		if (!(jndiEntries instanceof Map)) {
			return
		}

		System.setProperty "javax.sql.DataSource.Factory", "org.apache.commons.dbcp.BasicDataSourceFactory"

		jndiEntries.each { name, resCfg ->
			if (!resCfg) {
				return
			}

			if (!resCfg.type) {
				throw new IllegalArgumentException("Must supply a resource type for JNDI configuration")
			}

			def res = new ContextResource(
				auth: resCfg.remove('auth'),
				description: resCfg.remove("description"),
				name: name,
				scope: resCfg.remove('scope'),
				type: resCfg.remove('type'))

			// now it's only the custom properties left in the Map...
			resCfg.each { key, value -> res.setProperty key, value }

			context.namingResources.addResource res
		}
	}
}
