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

        String tomcatVersion = "7.0.42"

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

        // needed for JSP compilation
        runtime "org.eclipse.jdt.core.compiler:ecj:3.7.2"
    }

    plugins {
        build(':release:3.0.0', ':rest-client-builder:1.0.3') {
            export = false
        }
    }
}
