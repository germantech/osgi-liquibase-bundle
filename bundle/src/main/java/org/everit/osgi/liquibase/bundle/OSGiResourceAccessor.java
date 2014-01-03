package org.everit.osgi.liquibase.bundle;

/*
 * Copyright (c) 2011, Everit Kft.
 *
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;

import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.ResourceAccessor;

public class OSGiResourceAccessor extends CompositeResourceAccessor {

    private static class BundleResourceAccessor implements ResourceAccessor {

        private ClassLoader bundleClassLoader;

        public BundleResourceAccessor(final Bundle bundle) {
            BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
            this.bundleClassLoader = bundleWiring.getClassLoader();
        }

        @Override
        public InputStream getResourceAsStream(String file) throws IOException {
            return this.bundleClassLoader.getResourceAsStream(file);
        }

        @Override
        public Enumeration<URL> getResources(String packageName) throws IOException {
            return this.bundleClassLoader.getResources(packageName);
        }

        @Override
        public ClassLoader toClassLoader() {
            return this.bundleClassLoader;
        }
    }

    private Bundle bundle;

    public OSGiResourceAccessor(Bundle bundle) {
        super(new BundleResourceAccessor(bundle), new ClassLoaderResourceAccessor(
                OSGiResourceAccessor.class.getClassLoader()));
    }

    public Bundle getBundle() {
        return bundle;
    }
}
