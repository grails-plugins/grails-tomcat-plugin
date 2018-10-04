if(System.getenv('TRAVIS_BRANCH')) {
    grails.project.repos.grailsCentral.username = System.getenv("GRAILS_CENTRAL_USERNAME")
    grails.project.repos.grailsCentral.password = System.getenv("GRAILS_CENTRAL_PASSWORD")    
}

grails.project.dependency.resolver = "maven"
grails.project.work.dir="target/work"
grails.project.dependency.resolution = {

    inherits "global"
    log "warn"

    repositories {
        grailsCentral()
        mavenLocal()
        mavenCentral()
    }

    dependencies {

        String tomcatVersion = "7.0.91"

        runtime("org.apache.tomcat:tomcat-catalina-ant:$tomcatVersion") {
            excludes 'org.apache.tomcat:tomcat-catalina', 'org.apache.tomcat:tomcat-coyote'
        }
        compile "org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion", {
            excludes 'org.apache.tomcat.embed:tomcat-embed-logging-juli', 'org.apache.tomcat.embed:tomcat-embed-logging-log4j'
        }
        runtime "org.apache.tomcat.embed:tomcat-embed-jasper:$tomcatVersion", {
            excludes 'org.eclipse.jdt.core.compiler:ecj', 'org.apache.tomcat.embed:tomcat-embed-core'
        }
        runtime "org.apache.tomcat.embed:tomcat-embed-logging-log4j:$tomcatVersion"
        runtime "org.apache.tomcat.embed:tomcat-embed-websocket:$tomcatVersion"

        // needed for JSP compilation
        runtime "org.eclipse.jdt.core.compiler:ecj:4.4.2"

        // needed for JNDI
        optional 'commons-dbcp:commons-dbcp:1.4'
    }

    plugins {
        build(':release:3.0.1', ':rest-client-builder:2.0.1') {
            export = false
        }
    }
}
