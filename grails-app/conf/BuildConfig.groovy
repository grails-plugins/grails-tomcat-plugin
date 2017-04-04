if (System.getenv('TRAVIS_BRANCH')) {
	grails.project.repos.grailsCentral.username = System.getenv('GRAILS_CENTRAL_USERNAME')
	grails.project.repos.grailsCentral.password = System.getenv('GRAILS_CENTRAL_PASSWORD')
}

grails.project.work.dir = 'target'

grails.project.dependency.resolver = 'maven'
grails.project.dependency.resolution = {

	inherits 'global'
	log 'warn'

	repositories {
		mavenLocal()
		grailsCentral()
		mavenCentral()
	}

	dependencies {
		String tomcatVersion = '8.5.2'
		compile "org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion", { exclude 'ecj' }
		['el', 'jasper', 'logging-log4j', 'logging-juli', 'websocket'].each {
			runtime "org.apache.tomcat.embed:tomcat-embed-$it:$tomcatVersion", { exclude 'ecj' }
		}

		// TODO use 'org.eclipse.jdt.core.compiler:ecj:4.5' when it's available in Maven Central
		runtime 'org.eclipse.scout.sdk.deps:ecj:4.5'
	}

	plugins {
		build ':release:3.1.2', ':rest-client-builder:2.1.1', {
			export = false
		}
	}
}
