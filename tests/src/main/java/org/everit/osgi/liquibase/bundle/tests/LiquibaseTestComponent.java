/**
 * This file is part of Everit Liquibase OSGi Bundle Tests.
 *
 * Everit Liquibase OSGi Bundle Tests is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit Liquibase OSGi Bundle Tests is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit Liquibase OSGi Bundle Tests.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.liquibase.bundle.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import javax.sql.DataSource;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.ChangeLogParseException;
import liquibase.exception.DatabaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.everit.osgi.dev.testrunner.TestDuringDevelopment;
import org.everit.osgi.liquibase.bundle.LiquibaseOSGiUtil;
import org.everit.osgi.liquibase.bundle.OSGiResourceAccessor;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;

@Component(name = "LiquibaseTest", immediate = true)
@Service(value = LiquibaseTestComponent.class)
@Properties({ @Property(name = "eosgi.testEngine", value = "junit4"),
        @Property(name = "eosgi.testId", value = "liquibaseTest") })
public class LiquibaseTestComponent {

    private static void copyURLContentToStream(final URL url, final OutputStream out) {
        try (InputStream is = url.openStream()) {
            byte[] buffer = new byte[1024];
            int len = is.read(buffer);
            while (len > -1) {
                out.write(buffer, 0, len);
                len = is.read(buffer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error during readin stream " + url.toExternalForm(), e);
        }
    }

    private BundleContext bundleContext;

    @Reference
    private DataSource dataSource;

    @Activate
    public void activate(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void bindDataSource(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private void dropAll() {
        Database database = null;
        try {
            Connection connection = dataSource.getConnection();
            database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            // database.setDefaultSchemaName("PUBLIC");
            Liquibase liquibase = new Liquibase((String) null, (ResourceAccessor) null, database);
            liquibase.dropAll();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (database != null) {
                try {
                    database.close();
                } catch (DatabaseException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void installAndStartBundle(final String pathPrefix, final String manifestPropertiesPath,
            final String... filePaths) {
        Manifest manifest = resolveManifest("/META-INF/testBundles/" + pathPrefix + "/" + manifestPropertiesPath);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        JarOutputStream zipOutputStream;
        try {
            zipOutputStream = new JarOutputStream(bout, manifest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Bundle bundle = bundleContext.getBundle();
        for (String filePath : filePaths) {
            ZipEntry zipEntry = new ZipEntry(filePath);
            String resourcePath = "/META-INF/testBundles/" + pathPrefix + "/" + filePath;
            try {
                zipOutputStream.putNextEntry(zipEntry);
                URL resource = bundle.getResource(resourcePath);
                LiquibaseTestComponent.copyURLContentToStream(resource, zipOutputStream);
                zipOutputStream.closeEntry();
            } catch (IOException e) {
                throw new RuntimeException("Error during reading resource " + resourcePath, e);
            }

        }
        try {
            zipOutputStream.close();
            byte[] bundleBA = bout.toByteArray();
            Bundle installedBundle = bundleContext.installBundle(pathPrefix, new ByteArrayInputStream(bundleBA));
            installedBundle.start();
            BundleWiring bundleWiring = installedBundle.adapt(BundleWiring.class);
            List<BundleCapability> capabilities = bundleWiring.getCapabilities(null);
            System.out.println(capabilities);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (BundleException e) {
            throw new RuntimeException(e);
        }
    }

    private void installAndStartBundles() {
        installAndStartBundle("bundle2", "META-INF/MANIFEST.properties", "META-INF/liquibase/car.xml",
                "META-INF/liquibase/person.xml");
        installAndStartBundle("bundle1", "META-INF/MANIFEST.properties", "META-INF/liquibase/myApp.xml");
    }

    private void removeBundles() {

        try {
            Bundle bundle1 = bundleContext.getBundle("bundle1");
            bundle1.uninstall();
            Bundle bundle2 = bundleContext.getBundle("bundle2");
            bundle2.uninstall();
        } catch (BundleException e) {
            throw new RuntimeException(e);
        }
    }

    private Manifest resolveManifest(final String manifestPropertiesPath) {
        Bundle bundle = bundleContext.getBundle();
        URL manifestProperties = bundle.getResource(manifestPropertiesPath);
        java.util.Properties properties = new java.util.Properties();
        InputStream mfPropsStream = null;
        try {
            mfPropsStream = manifestProperties.openStream();
            properties.load(mfPropsStream);
            Manifest manifest = new Manifest();
            Attributes mainAttributes = manifest.getMainAttributes();
            mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            for (Entry<Object, Object> entries : properties.entrySet()) {
                mainAttributes.putValue((String) entries.getKey(), (String) entries.getValue());
            }
            return manifest;
        } catch (IOException e) {
            throw new RuntimeException("Cannot load manifest properties from path: " + manifestPropertiesPath, e);
        } finally {
            if (mfPropsStream != null) {
                try {
                    mfPropsStream.close();
                } catch (IOException e) {
                    throw new RuntimeException("Error closing manifest properties stream", e);
                }
            }
        }
    }

    /**
     * Testing normal OSGi inclusion in a ChangeLog file.
     */
    @Test
    @TestDuringDevelopment
    public void testChangeLogWithNormalAndOSGiInclude() {
        installAndStartBundles();
        try {
            Map<Bundle, List<BundleCapability>> bundles =
                    LiquibaseOSGiUtil.findBundlesBySchemaExpression("myApp", bundleContext, Bundle.ACTIVE);

            Assert.assertEquals(1, bundles.size());
            Entry<Bundle, List<BundleCapability>> bundleWithCapability = bundles.entrySet().iterator().next();
            Bundle testBundle = bundleWithCapability.getKey();
            List<BundleCapability> capabilities = bundleWithCapability.getValue();
            Assert.assertEquals(1, capabilities.size());
            BundleCapability capability = capabilities.get(0);
            String resourceName = (String) capability.getAttributes().get(LiquibaseOSGiUtil.ATTR_SCHEMA_RESOURCE);

            ResourceAccessor resourceAccessor = new OSGiResourceAccessor(testBundle);
            Database database = null;
            try {
                Connection connection = dataSource.getConnection();
                database =
                        DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                Liquibase liquibase = new Liquibase(resourceName, resourceAccessor, database);
                liquibase.update((String) null);
                Statement statement = connection.createStatement();
                Assert.assertTrue(statement.execute("select * from \"person\""));

            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (database != null) {
                    try {
                        database.close();
                    } catch (DatabaseException e) {
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            dropAll();
            removeBundles();
        }
    }

    /**
     * Testing the {@link}createFilterForLiquibaseCapabilityAttributes function with invalid schema expressions.
     */
    @Test
    public void testcreateFilterForLiquibaseCapabilityAttributes() {
        try {
            LiquibaseOSGiUtil.createFilterForLiquibaseCapabilityAttributes("myApp;myApp2");
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof RuntimeException);
        }
        try {
            LiquibaseOSGiUtil.createFilterForLiquibaseCapabilityAttributes("myApp;a=x");
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof RuntimeException);
        }
        try {
            LiquibaseOSGiUtil.createFilterForLiquibaseCapabilityAttributes("myApp;a:=x;b:=y");
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof RuntimeException);
        }
        try {
            LiquibaseOSGiUtil.createFilterForLiquibaseCapabilityAttributes("myApp;notfilter:=(name=asd)");
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof RuntimeException);
        }
        try {
            LiquibaseOSGiUtil.createFilterForLiquibaseCapabilityAttributes("myApp;filter:=(version>2)");
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof RuntimeException);
        }
        LiquibaseOSGiUtil.createFilterForLiquibaseCapabilityAttributes("myApp;filter:=(name=asd)");
        Assert.assertTrue(true);
    }

    /**
     * Testing the case if two equal Provide-Capability entry is present in a manifest file for a capability we want to
     * include in a ChangeLog file.
     */
    @Test
    public void testDoubledCapability() {
        installAndStartBundle("bundle2", "META-INF/MANIFEST.properties", "META-INF/liquibase/car.xml");
        try {
            Map<Bundle, List<BundleCapability>> bundles =
                    LiquibaseOSGiUtil.findBundlesBySchemaExpression("carandperson", bundleContext,
                            Bundle.ACTIVE);
            Entry<Bundle, List<BundleCapability>> bundleWithCapability = bundles.entrySet().iterator().next();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Bundle bundle2 = bundleContext.getBundle("bundle2");
            try {
                bundle2.uninstall();
            } catch (BundleException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Test case, when the capability we want to include in our ChangeLog file is optional, and there is no bundle that
     * provide that capability. In this case, the normal behavior is to throw a NoSuchElementException.
     */
    @Test
    @TestDuringDevelopment
    public void testHalfwired() {
        installAndStartBundle("bundle2", "META-INF/MANIFEST.properties", "META-INF/liquibase/car.xml",
                "META-INF/liquibase/person.xml");
        installAndStartBundle("bundle1", "META-INF/MANIFEST.properties",
                "META-INF/liquibase/include_eosgi_halfwired.xml");

        try {
            Map<Bundle, List<BundleCapability>> bundles =
                    LiquibaseOSGiUtil.findBundlesBySchemaExpression("halfwired", bundleContext,
                            Bundle.ACTIVE);
            Entry<Bundle, List<BundleCapability>> bundleWithCapability = bundles.entrySet().iterator().next();
            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof NoSuchElementException);
        } finally {
            removeBundles();
        }
    }

    /**
     * Testing the case when the resource attribute of the included capability is missing. In this case, the normal
     * behavior is to throw a ChangeLogParseException.
     */
    @Test
    public void testNoResource() {
        installAndStartBundle("bundle2", "META-INF/MANIFEST.properties", "META-INF/liquibase/car.xml",
                "META-INF/liquibase/person.xml");
        installAndStartBundle("bundle1", "META-INF/MANIFEST.properties",
                "META-INF/liquibase/include_eosgi_noresource.xml");
        try {
            Map<Bundle, List<BundleCapability>> bundles =
                    LiquibaseOSGiUtil.findBundlesBySchemaExpression("noresource_test", bundleContext,
                            Bundle.ACTIVE);
            Entry<Bundle, List<BundleCapability>> bundleWithCapability = bundles.entrySet().iterator().next();
            Bundle testBundle = bundleWithCapability.getKey();
            List<BundleCapability> capabilities = bundleWithCapability.getValue();
            BundleCapability capability = capabilities.get(0);
            String resourceName = (String) capability.getAttributes().get(LiquibaseOSGiUtil.ATTR_SCHEMA_RESOURCE);

            ResourceAccessor resourceAccessor = new OSGiResourceAccessor(testBundle);
            Database database = null;

            try {
                Connection connection = dataSource.getConnection();
                database =
                        DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                Liquibase liquibase = new Liquibase(resourceName, resourceAccessor, database);
                liquibase.update((String) null);

                Assert.assertTrue(false);
            } catch (Exception e) {
                Assert.assertTrue(e instanceof ChangeLogParseException);
            } finally {
                if (database != null) {
                    try {
                        database.close();
                    } catch (DatabaseException e) {
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            removeBundles();
        }
    }

    /**
     * Test the case when there is no such capability provided what we want to include in a ChangeLog file. Should throw
     * a ChangeLogParseException.
     */
    @Test
    public void testNotExistingProvideCapability() {
        installAndStartBundle("bundle2", "META-INF/MANIFEST.properties", "META-INF/liquibase/test.xml");
        try {
            Map<Bundle, List<BundleCapability>> bundles =
                    LiquibaseOSGiUtil.findBundlesBySchemaExpression("test", bundleContext, Bundle.ACTIVE);

            Entry<Bundle, List<BundleCapability>> bundleWithCapability = bundles.entrySet().iterator().next();
            Bundle testBundle = bundleWithCapability.getKey();
            List<BundleCapability> capabilities = bundleWithCapability.getValue();
            BundleCapability capability = capabilities.get(0);
            String resourceName = (String) capability.getAttributes().get(LiquibaseOSGiUtil.ATTR_SCHEMA_RESOURCE);

            ResourceAccessor resourceAccessor = new OSGiResourceAccessor(testBundle);
            Database database = null;
            try {
                Connection connection = dataSource.getConnection();
                database =
                        DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                Liquibase liquibase = new Liquibase(resourceName, resourceAccessor, database);
                liquibase.update((String) null);

                Assert.assertTrue(false);
            } catch (Exception e) {
                Assert.assertTrue(e instanceof ChangeLogParseException);
            } finally {
                if (database != null) {
                    try {
                        database.close();
                    } catch (DatabaseException e) {
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            dropAll();
            try {
                Bundle bundle2 = bundleContext.getBundle("bundle2");
                bundle2.uninstall();
            } catch (BundleException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Test case, where the given changeLogFile to a Liquibase object refers to an OSGi based dependency but the
     * resourceAccessor has different type from OSGiResourceAccessor. Should throw a ChangeLogParseException.
     */
    @Test
    @TestDuringDevelopment
    public void testResourceAccessor() {

        Bundle bundle = bundleContext.getBundle();
        BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
        ClassLoader classLoader = bundleWiring.getClassLoader();

        ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor(classLoader);
        Database database = null;
        try {
            Connection connection = dataSource.getConnection();
            database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase("/META-INF/testBundles/bundle1/META-INF/liquibase/myApp.xml",
                    resourceAccessor, database);
            liquibase.update((String) null);

            Assert.assertTrue(false);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ChangeLogParseException);
            Assert.assertTrue(e.getMessage().indexOf("refers to an OSGi based dependency but the resourceAccessor") != -1);
        } finally {
            if (database != null) {
                try {
                    database.close();
                } catch (DatabaseException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
