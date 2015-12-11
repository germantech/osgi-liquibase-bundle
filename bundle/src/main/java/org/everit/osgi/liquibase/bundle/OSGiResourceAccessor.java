/**
 * This file is part of Everit OSGi Liquibase Bundle.
 *
 * Everit OSGi Liquibase Bundle is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit OSGi Liquibase Bundle is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit OSGi Liquibase Bundle.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.liquibase.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;

import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.ResourceAccessor;

/**
 * The resource accessor that should be used in OSGi environments.
 *
 */
public class OSGiResourceAccessor extends CompositeResourceAccessor {

    private static class BundleResourceAccessor implements ResourceAccessor {

        private ClassLoader bundleClassLoader;
		private Bundle hostBundle;

        public BundleResourceAccessor(final Bundle bundle) {
			BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
            this.bundleClassLoader = bundleWiring.getClassLoader();
            hostBundle = bundle;
        }

        @Override
        public InputStream getResourceAsStream(String file) throws IOException {
            return this.bundleClassLoader.getResourceAsStream(file);
        }

        @Override
        public Enumeration<URL> getResources(String packageName) throws IOException {
        	// Create file objects and then entries
        	Enumeration<String> discoveredFiles = hostBundle.getEntryPaths(packageName);
        	// Nothing was found
        	if(discoveredFiles == null)
        		return new EmptyEnumeration<URL>();
        	
        	// Create files and then URLs
        	Vector<URL> files = new Vector<URL>();
        	while (discoveredFiles.hasMoreElements()) {
        		URL entry = hostBundle.getResource(discoveredFiles.nextElement());
        		if(entry != null)
        			files.add(entry);
			}
        	return files.elements();
        }

        @Override
        public ClassLoader toClassLoader() {
            return this.bundleClassLoader;
        }
        
        private class EmptyEnumeration<E> implements Enumeration<E> {
        	@Override
        	public boolean hasMoreElements() {
        		return false;
        	}
        	
        	@Override
        	public E nextElement() {
        		return null;
        	}
        }
        
    }
    
    private final Bundle bundle;

    private final Map<String, Object> attributes;

    /**
     * Creating a new resource accessor for the specified bundle without any attributes.
     * 
     * @param bundle
     *            The bundle.
     */
    public OSGiResourceAccessor(Bundle bundle) {
        this(bundle, (Map<String, Object>)null);
    }

    public OSGiResourceAccessor(Bundle bundle, ResourceAccessor... accessors) {
    	this(bundle, null, accessors);
    }
    
    /**
     * Creating a new {@link OSGiResourceAccessor} for the specified bundle with the specified attributes.
     * 
     * @param bundle
     *            The bundle.
     * @param attributes
     *            See {@link #getAttributes()}.
     */
    public OSGiResourceAccessor(Bundle bundle, Map<String, Object> attributes, ResourceAccessor... accessors) {
    	super(combine(accessors, new BundleResourceAccessor(bundle), new ClassLoaderResourceAccessor(OSGiResourceAccessor.class.getClassLoader())));
    	this.bundle = bundle;
    	if (attributes == null) {
    		this.attributes = Collections.emptyMap();
    	} else {
    		this.attributes = Collections.unmodifiableMap(new HashMap<String, Object>(attributes));
    	}
    }
    
    /**
     * Creating a new {@link OSGiResourceAccessor} for the specified bundle with the specified attributes.
     * 
     * @param bundle
     *            The bundle.
     * @param attributes
     *            See {@link #getAttributes()}.
     */
    public OSGiResourceAccessor(Bundle bundle, Map<String, Object> attributes) {
        this(bundle, attributes, new ResourceAccessor[]{});
    }

    public Bundle getBundle() {
        return bundle;
    }

    /**
     * Attributes are normally coming from the liquibase.schema capability definition.
     * 
     * @return The attributes of the resource accessor.
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    private static List<ResourceAccessor> combine(ResourceAccessor[] listed, ResourceAccessor... accessors) {
    	List<ResourceAccessor> allAccessors = new ArrayList<ResourceAccessor>();
    	if(listed != null)
    		allAccessors.addAll(Arrays.asList(listed));
    	if(accessors != null)
    		allAccessors.addAll(Arrays.asList(accessors));
    	
    	return allAccessors;
    }
}
