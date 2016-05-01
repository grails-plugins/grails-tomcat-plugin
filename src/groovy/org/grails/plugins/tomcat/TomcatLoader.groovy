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

import groovy.transform.CompileStatic

import java.beans.PropertyChangeListener

import org.apache.catalina.Context
import org.apache.catalina.Lifecycle
import org.apache.catalina.LifecycleState
import org.apache.catalina.Loader
import org.apache.catalina.util.LifecycleBase
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A loader instance used for the embedded version of Tomcat.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class TomcatLoader extends LifecycleBase implements Loader {

	protected Logger log = LoggerFactory.getLogger(getClass())

	ClassLoader classLoader
	Context context
	boolean delegate
	boolean reloadable

	TomcatLoader(ClassLoader cl) {
		// Class loader that only searches the parent
		classLoader = new ParentDelegatingClassLoader(cl)
	}

	@Override
	protected void initInternal() {}

	@Override
	protected void destroyInternal() {
		classLoader = null
	}

	@Override
	protected void startInternal() {
		fireLifecycleEvent Lifecycle.START_EVENT, this
		setState LifecycleState.STARTING
	}

	@Override
	protected void stopInternal() {
		fireLifecycleEvent Lifecycle.STOP_EVENT, this
		setState LifecycleState.STOPPING
	}

	void addPropertyChangeListener(PropertyChangeListener listener) {}
	void removePropertyChangeListener(PropertyChangeListener listener) {}
	void addRepository(String repository) {}
	void backgroundProcess() {}
	boolean modified() { false }
}
