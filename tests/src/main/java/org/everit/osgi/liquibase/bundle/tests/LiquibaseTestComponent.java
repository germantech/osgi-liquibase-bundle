package org.everit.osgi.liquibase.bundle.tests;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.sql.DataSource;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
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

@Component(name = "LiquibaseTest", immediate = true)
@Service(value = LiquibaseTestComponent.class)
@Properties({ @Property(name = "eosgi.testEngine", value = "junit4"),
        @Property(name = "eosgi.testId", value = "liquibaseTest") })
public class LiquibaseTestComponent {

    @Reference
    private DataSource dataSource;

    private BundleContext bundleContext;

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
//            database.setDefaultCatalogName("public");
//            database.setDefaultSchemaName("public");
            Liquibase liquibase = new Liquibase(null, null, database);
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

    @Test
    @TestDuringDevelopment
    public void testChangeLogWithNormalAndOSGiInclude() {
        installAndStartBundles();
        try {
            Map<Bundle, List<BundleCapability>> bundles =
                    LiquibaseOSGiUtil.findBundlesBySchemaExpression("myApp", bundleContext);

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
                liquibase.update(null);
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

    private void installAndStartBundles() {
        installAndStartBundle("bundle2", "META-INF/MANIFEST.MF", "META-INF/liquibase/car.xml",
                "META-INF/liquibase/person.xml");
        installAndStartBundle("bundle1", "META-INF/MANIFEST.MF", "META-INF/liquibase/myApp.xml");
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

    private void installAndStartBundle(String pathPrefix, String... filePaths) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(bout);
        Bundle bundle = bundleContext.getBundle();
        for (String filePath : filePaths) {
            ZipEntry zipEntry = new ZipEntry(filePath);
            String resourcePath = "/META-INF/testBundles/" + pathPrefix + "/" + filePath;
            try {
                zipOutputStream.putNextEntry(zipEntry);
                URL resource = bundle.getResource(resourcePath);
                copyURLContentToStream(resource, zipOutputStream);
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (BundleException e) {
            throw new RuntimeException(e);
        }
    }

    private static void copyURLContentToStream(URL url, OutputStream out) {
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
}
