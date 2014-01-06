package liquibase.servicelocator;

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
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import org.everit.osgi.liquibase.bundle.OSGiResourceAccessor;
import org.everit.osgi.liquibase.bundle.internal.BundlePackageScanClassResolver;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import liquibase.exception.ServiceNotFoundException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.logging.Logger;
import liquibase.logging.core.DefaultLogger;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import liquibase.util.StringUtils;

/**
 * Modified version of the original ServiceLocator class coming from Liquibase to make it work inside an OSGi
 * environment. It is not a problem to reimplement a class in this way as based on the specification this class will be
 * used for sure as this entry is before the embedded jars classes on the classpath of the bundle. It is not good to set
 * the instance of the ServiceLocator from a BundleActivator as bundles may not be started during calling a refresh on
 * bundles that use the packages of this one.
 */
public class ServiceLocator {

    private static ServiceLocator instance;

    static {
        reset();
    }

    private ResourceAccessor resourceAccessor;

    private Map<Class, List<Class>> classesBySuperclass;
    private List<String> packagesToScan;
    private Logger logger = new DefaultLogger(); // cannot look up regular logger because you get a stackoverflow since
                                                 // we are in the servicelocator
    private PackageScanClassResolver classResolver;

    protected ServiceLocator() {
        this.classResolver = defaultClassLoader();
        setResourceAccessor(new ClassLoaderResourceAccessor());
    }

    protected ServiceLocator(ResourceAccessor accessor) {
        this.classResolver = defaultClassLoader();
        setResourceAccessor(accessor);
    }

    protected ServiceLocator(PackageScanClassResolver classResolver) {
        this.classResolver = classResolver;
        setResourceAccessor(new ClassLoaderResourceAccessor());
    }

    protected ServiceLocator(PackageScanClassResolver classResolver, ResourceAccessor accessor) {
        this.classResolver = classResolver;
        setResourceAccessor(accessor);
    }

    public static ServiceLocator getInstance() {
        return instance;
    }

    public static void setInstance(ServiceLocator newInstance) {
        instance = newInstance;
    }

    private PackageScanClassResolver defaultClassLoader() {
        if (WebSpherePackageScanClassResolver.isWebSphereClassLoader(this.getClass().getClassLoader())) {
            logger.debug("Using WebSphere Specific Class Resolver");
            return new WebSpherePackageScanClassResolver("liquibase/parser/core/xml/dbchangelog-2.0.xsd");
        } else {
            return new DefaultPackageScanClassResolver();
        }
    }

    public void setResourceAccessor(ResourceAccessor resourceAccessor) {
        this.resourceAccessor = resourceAccessor;
        this.classesBySuperclass = new HashMap<Class, List<Class>>();

        this.classResolver.setClassLoaders(new HashSet<ClassLoader>(Arrays.asList(new ClassLoader[] { resourceAccessor
                .toClassLoader() })));

        packagesToScan = new ArrayList<String>();
        String packagesToScanSystemProp = System.getProperty("liquibase.scan.packages");
        if ((packagesToScanSystemProp != null)
                && ((packagesToScanSystemProp = StringUtils.trimToNull(packagesToScanSystemProp)) != null)) {
            for (String value : packagesToScanSystemProp.split(",")) {
                addPackageToScan(value);
            }
        } else {
            Enumeration<URL> manifests = null;
            try {
                manifests = resourceAccessor.getResources("META-INF/MANIFEST.MF");
                while (manifests.hasMoreElements()) {
                    URL url = manifests.nextElement();
                    InputStream is = url.openStream();
                    Manifest manifest = new Manifest(is);
                    String attributes =
                            StringUtils.trimToNull(manifest.getMainAttributes().getValue("Liquibase-Package"));
                    if (attributes != null) {
                        for (Object value : attributes.split(",")) {
                            addPackageToScan(value.toString());
                        }
                    }
                    is.close();
                }
            } catch (IOException e) {
                throw new UnexpectedLiquibaseException(e);
            }

            if (packagesToScan.size() == 0) {
                addPackageToScan("liquibase.change");
                addPackageToScan("liquibase.database");
                addPackageToScan("liquibase.parser");
                addPackageToScan("liquibase.precondition");
                addPackageToScan("liquibase.datatype");
                addPackageToScan("liquibase.serializer");
                addPackageToScan("liquibase.sqlgenerator");
                addPackageToScan("liquibase.executor");
                addPackageToScan("liquibase.snapshot");
                addPackageToScan("liquibase.logging");
                addPackageToScan("liquibase.diff");
                addPackageToScan("liquibase.structure");
                addPackageToScan("liquibase.structurecompare");
                addPackageToScan("liquibase.lockservice");
                addPackageToScan("liquibase.ext");
            }
        }
    }

    public void addPackageToScan(String packageName) {
        logger.debug("Adding package to scan: " + packageName);
        packagesToScan.add(packageName);
    }

    public Class findClass(Class requiredInterface) throws ServiceNotFoundException {
        Class[] classes = findClasses(requiredInterface);
        if (PrioritizedService.class.isAssignableFrom(requiredInterface)) {
            PrioritizedService returnObject = null;
            for (Class clazz : classes) {
                PrioritizedService newInstance;
                try {
                    newInstance = (PrioritizedService) clazz.newInstance();
                } catch (Exception e) {
                    throw new UnexpectedLiquibaseException(e);
                }

                if (returnObject == null || newInstance.getPriority() > returnObject.getPriority()) {
                    returnObject = newInstance;
                }
            }

            if (returnObject == null) {
                throw new ServiceNotFoundException("Could not find implementation of " + requiredInterface.getName());
            }
            return returnObject.getClass();
        }

        if (classes.length != 1) {
            throw new ServiceNotFoundException("Could not find unique implementation of " + requiredInterface.getName()
                    + ".  Found " + classes.length + " implementations");
        }

        return classes[0];
    }

    public <T> Class<? extends T>[] findClasses(Class<T> requiredInterface) throws ServiceNotFoundException {
        logger.debug("ServiceLocator.findClasses for " + requiredInterface.getName());

        try {
            Class.forName(requiredInterface.getName());

            if (!classesBySuperclass.containsKey(requiredInterface)) {
                classesBySuperclass.put(requiredInterface, findClassesImpl(requiredInterface));
            }
        } catch (Exception e) {
            throw new ServiceNotFoundException(e);
        }

        List<Class> classes = classesBySuperclass.get(requiredInterface);
        HashSet<Class> uniqueClasses = new HashSet<Class>(classes);
        return uniqueClasses.toArray(new Class[uniqueClasses.size()]);
    }

    public Object newInstance(Class requiredInterface) throws ServiceNotFoundException {
        try {
            return findClass(requiredInterface).newInstance();
        } catch (Exception e) {
            throw new ServiceNotFoundException(e);
        }
    }

    private List<Class> findClassesImpl(Class requiredInterface) throws Exception {
        logger.debug("ServiceLocator finding classes matching interface " + requiredInterface.getName());

        List<Class> classes = new ArrayList<Class>();

        classResolver.addClassLoader(resourceAccessor.toClassLoader());
        for (Class<?> clazz : classResolver.findImplementations(requiredInterface,
                packagesToScan.toArray(new String[packagesToScan.size()]))) {
            if (clazz.getAnnotation(LiquibaseService.class) != null
                    && clazz.getAnnotation(LiquibaseService.class).skip()) {
                continue;
            }

            if (!Modifier.isAbstract(clazz.getModifiers()) && !Modifier.isInterface(clazz.getModifiers())
                    && Modifier.isPublic(clazz.getModifiers())) {
                try {
                    clazz.getConstructor();
                    logger.debug(clazz.getName() + " matches " + requiredInterface.getName());

                    classes.add(clazz);
                } catch (NoSuchMethodException e) {
                    logger.info("Can not use " + clazz
                            + " as a Liquibase service because it does not have a no-argument constructor");
                } catch (NoClassDefFoundError e) {
                    String message =
                            "Can not use " + clazz + " as a Liquibase service because "
                                    + e.getMessage().replace("/", ".") + " is not in the classpath";
                    if (e.getMessage().startsWith("org/yaml/snakeyaml")) {
                        logger.info(message);
                    } else {
                        logger.warning(message);
                    }
                }
            }
        }

        return classes;
    }

    public static void reset() {
        Bundle bundle = FrameworkUtil.getBundle(ServiceLocator.class);
        if (bundle != null) {
            BundlePackageScanClassResolver classResolver = new BundlePackageScanClassResolver(bundle);
            ResourceAccessor resourceAccessor = new OSGiResourceAccessor(bundle);
            instance = new ServiceLocator(classResolver, resourceAccessor);
        } else {
            instance = new ServiceLocator();
        }
    }

    protected Logger getLogger() {
        return logger;
    }
}
