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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.felix.utils.manifest.Attribute;
import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Directive;
import org.apache.felix.utils.manifest.Parser;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

public final class LiquibaseOSGiUtil {

    public static final String LIQUIBASE_CAPABILITY_NS = "liquibase.schema";

    public static final String ATTR_SCHEMA_NAME = "name";

    public static final String ATTR_SCHEMA_RESOURCE = "resource";
    
    public static final String INCLUDE_FILE_OSGI_PREFIX = "eosgi:";

    public static final BundleWire findMatchingWireForSchemaExpression(final Bundle currentBundle,
            final String schemaExpression) {

        BundleWiring bundleWiring = currentBundle.adapt(BundleWiring.class);
        List<BundleWire> wires = bundleWiring.getRequiredWires(LIQUIBASE_CAPABILITY_NS);

        if (wires.size() == 0) {
            return null;
        }

        Filter capabilityFilter = createFilterForLiquibaseCapabilityAttributes(schemaExpression);

        Iterator<BundleWire> iterator = wires.iterator();
        BundleWire matchingWire = null;
        // Iterate through the wires to find the one that matches the schema expression
        while (matchingWire == null && iterator.hasNext()) {
            BundleWire wire = iterator.next();
            BundleWiring providerWiring = wire.getProviderWiring();
            // Only check the wire if it is in use (it is not used if e.g. it is optional and there is no provider)
            if (providerWiring != null) {
                BundleCapability capability = wire.getCapability();
                Map<String, Object> capabilityAttributes = capability.getAttributes();
                if (capabilityFilter.matches(capabilityAttributes)) {
                    Object schemaResourceAttr = capabilityAttributes.get(ATTR_SCHEMA_RESOURCE);
                    if (schemaResourceAttr != null) {
                        matchingWire = wire;
                    } else {
                        // TODO Write WARNING
                    }

                }
            }
        }
        return matchingWire;
    }

    public static Filter createFilterForLiquibaseCapabilityAttributes(final String schemaExpression) {
        Clause[] clauses = Parser.parseClauses(new String[] { schemaExpression });
        if (clauses.length != 1) {
            // TODO throw an exception
        }
        Clause clause = clauses[0];
        String schemaName = clause.getName();
        Attribute[] attributes = clause.getAttributes();
        if (attributes.length > 0) {
            // TODO throw excetpion that no attributes are supported
        }
        Directive[] directives = clause.getDirectives();
        if (directives.length > 1) {
            // TODO throw exception that onl
        }
        String filterString = "(" + ATTR_SCHEMA_NAME + "=" + schemaName + ")";
        if (directives.length == 1) {
            if (!Constants.FILTER_DIRECTIVE.equals(directives[0].getName())) {
                // TODO throw exception as only filter is supported
            }
            String additionalFilterString = directives[0].getValue();
            filterString = "(&" + filterString + additionalFilterString + ")";
            
        }
        try {
            return FrameworkUtil.createFilter(filterString);
        } catch (InvalidSyntaxException e) {
            // TODO throw runtime exception
            e.printStackTrace();
            return null;
        }
    }

    private LiquibaseOSGiUtil() {
    }
}
