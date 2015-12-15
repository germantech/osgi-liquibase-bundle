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
package org.everit.osgi.liquibase.bundle.internal.parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.everit.osgi.liquibase.bundle.LiquibaseOSGiUtil;
import org.everit.osgi.liquibase.bundle.OSGiResourceAccessor;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWire;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import br.com.germantech.liquibase.IFile;
import br.com.germantech.liquibase.impl.BundleFile;
import br.com.germantech.liquibase.impl.NormalFile;
import liquibase.Contexts;
import liquibase.change.AddColumnConfig;
import liquibase.change.Change;
import liquibase.change.ChangeFactory;
import liquibase.change.ChangeWithColumns;
import liquibase.change.ColumnConfig;
import liquibase.change.ConstraintsConfig;
import liquibase.change.core.AbstractModifyDataChange;
import liquibase.change.core.AddColumnChange;
import liquibase.change.core.CreateIndexChange;
import liquibase.change.core.CreateProcedureChange;
import liquibase.change.core.CreateViewChange;
import liquibase.change.core.ExecuteShellCommandChange;
import liquibase.change.core.InsertDataChange;
import liquibase.change.core.LoadDataChange;
import liquibase.change.core.LoadDataColumnConfig;
import liquibase.change.core.RawSQLChange;
import liquibase.change.core.StopChange;
import liquibase.change.core.UpdateDataChange;
import liquibase.change.custom.CustomChangeWrapper;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.exception.ChangeLogParseException;
import liquibase.exception.CustomChangeException;
import liquibase.exception.LiquibaseException;
import liquibase.exception.MigrationFailedException;
import liquibase.exception.UnknownChangelogFormatException;
import liquibase.logging.LogFactory;
import liquibase.logging.Logger;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.parser.core.xml.IncludeAllFilter;
import liquibase.precondition.CustomPreconditionWrapper;
import liquibase.precondition.Precondition;
import liquibase.precondition.PreconditionFactory;
import liquibase.precondition.PreconditionLogic;
import liquibase.precondition.core.PreconditionContainer;
import liquibase.precondition.core.SqlPrecondition;
import liquibase.resource.ResourceAccessor;
import liquibase.sql.visitor.SqlVisitor;
import liquibase.sql.visitor.SqlVisitorFactory;
import liquibase.util.FileUtil;
import liquibase.util.ObjectUtil;
import liquibase.util.StringUtils;
import liquibase.util.file.FilenameUtils;

class OSGiXMLChangeLogSAXHandler extends DefaultHandler {

    /**
     * Wrapper for Attributes that expands the value as needed
     */
    private class ExpandingAttributes implements Attributes {
        private final Attributes attributes;

        private ExpandingAttributes(final Attributes attributes) {
            this.attributes = attributes;
        }

        @Override
        public int getIndex(final String qName) {
            return attributes.getIndex(qName);
        }

        @Override
        public int getIndex(final String uri, final String localName) {
            return attributes.getIndex(uri, localName);
        }

        @Override
        public int getLength() {
            return attributes.getLength();
        }

        @Override
        public String getLocalName(final int index) {
            return attributes.getLocalName(index);
        }

        @Override
        public String getQName(final int index) {
            return attributes.getQName(index);
        }

        @Override
        public String getType(final int index) {
            return attributes.getType(index);
        }

        @Override
        public String getType(final String qName) {
            return attributes.getType(qName);
        }

        @Override
        public String getType(final String uri, final String localName) {
            return attributes.getType(uri, localName);
        }

        @Override
        public String getURI(final int index) {
            return attributes.getURI(index);
        }

        @Override
        public String getValue(final int index) {
            return attributes.getValue(index);
        }

        @Override
        public String getValue(final String qName) {
            return changeLogParameters.expandExpressions(attributes.getValue(qName));
        }

        @Override
        public String getValue(final String uri, final String localName) {
            return changeLogParameters.expandExpressions(attributes.getValue(uri, localName));
        }
    }

    private static final char LIQUIBASE_FILE_SEPARATOR = '/';

    static File extractZipFile(final URL resource) throws IOException {
        String file = resource.getFile();
        String path = file.split("!")[0];
        if (path.matches("file:\\/[A-Za-z]:\\/.*")) {
            path = path.replaceFirst("file:\\/", "");
        } else {
            path = path.replaceFirst("file:", "");
        }
        path = URLDecoder.decode(path, "UTF-8");
        File zipfile = new File(path);

        File tempDir = File.createTempFile("liquibase-sax", ".dir");
        tempDir.delete();
        tempDir.mkdir();

        JarFile jarFile = new JarFile(zipfile);
        try {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                File entryFile = new File(tempDir, entry.getName());
                entryFile.mkdirs();
            }

            FileUtil.forceDeleteOnExit(tempDir);
        } finally {
            jarFile.close();
        }

        return tempDir;
    }

    private final ChangeFactory changeFactory;
    private final PreconditionFactory preconditionFactory;

    private final SqlVisitorFactory sqlVisitorFactory;

    private final ChangeLogParserFactory changeLogParserFactory;
    protected Logger log;
    private final DatabaseChangeLog databaseChangeLog;
    private Change change;
    private final Stack changeSubObjects = new Stack();
    private StringBuffer text;
    private PreconditionContainer rootPrecondition;
    private final Stack<PreconditionLogic> preconditionLogicStack = new Stack<PreconditionLogic>();
    private ChangeSet changeSet;
    private String paramName;

    private final ResourceAccessor resourceAccessor;
    private Precondition currentPrecondition;

    private final ChangeLogParameters changeLogParameters;
    private boolean inRollback = false;
    private boolean inModifySql = false;
    private Set<String> modifySqlDbmsList;

    private Contexts modifySqlContexts;

    private boolean modifySqlAppliedOnRollback = false;

    protected OSGiXMLChangeLogSAXHandler(final String physicalChangeLogLocation,
            final ResourceAccessor resourceAccessor,
            final ChangeLogParameters changeLogParameters) {
        log = LogFactory.getInstance().getLog();
        this.resourceAccessor = resourceAccessor;

        databaseChangeLog = new DatabaseChangeLog();
        databaseChangeLog.setPhysicalFilePath(physicalChangeLogLocation);
        databaseChangeLog.setChangeLogParameters(changeLogParameters);

        this.changeLogParameters = changeLogParameters;

        changeFactory = ChangeFactory.getInstance();
        preconditionFactory = PreconditionFactory.getInstance();
        sqlVisitorFactory = SqlVisitorFactory.getInstance();
        changeLogParserFactory = ChangeLogParserFactory.getInstance();
    }

    @Override
    public void characters(final char ch[], final int start, final int length) throws SAXException {
        if (text != null) {
            text.append(new String(ch, start, length));
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        String textString = null;
        if ((text != null) && (text.length() > 0)) {
            textString = changeLogParameters.expandExpressions(StringUtils.trimToNull(text.toString()));
        }

        try {
            if (changeSubObjects.size() > 0) {
                changeSubObjects.pop();
            } else if (rootPrecondition != null) {
                if ("preConditions".equals(qName)) {
                    if (changeSet == null) {
                        databaseChangeLog.setPreconditions(rootPrecondition);
                        handlePreCondition(rootPrecondition);
                    } else {
                        changeSet.setPreconditions(rootPrecondition);
                    }
                    rootPrecondition = null;
                } else if ("and".equals(qName)) {
                    preconditionLogicStack.pop();
                    currentPrecondition = null;
                } else if ("or".equals(qName)) {
                    preconditionLogicStack.pop();
                    currentPrecondition = null;
                } else if ("not".equals(qName)) {
                    preconditionLogicStack.pop();
                    currentPrecondition = null;
                } else if (qName.equals("sqlCheck")) {
                    ((SqlPrecondition) currentPrecondition).setSql(textString);
                    currentPrecondition = null;
                } else if (qName.equals("customPrecondition")) {
                    ((CustomPreconditionWrapper) currentPrecondition).setClassLoader(resourceAccessor.toClassLoader());
                    currentPrecondition = null;
                }

            } else if ((changeSet != null) && "rollback".equals(qName)) {
                changeSet.addRollBackSQL(textString);
                inRollback = false;
            } else if ((change != null) && (change instanceof RawSQLChange) && "comment".equals(qName)) {
                ((RawSQLChange) change).setComment(textString);
                text = new StringBuffer();
            } else if ((change != null) && "where".equals(qName)) {
                if (change instanceof AbstractModifyDataChange) {
                    ((AbstractModifyDataChange) change).setWhere(textString);
                } else {
                    throw new RuntimeException("Unexpected change type: " + change.getClass().getName());
                }
                text = new StringBuffer();
            } else if ((change != null) && (change instanceof CreateProcedureChange) && "comment".equals(qName)) {
                ((CreateProcedureChange) change).setComments(textString);
                text = new StringBuffer();
            } else if ((change != null) && (change instanceof CustomChangeWrapper) && (paramName != null)
                    && "param".equals(qName)) {
                ((CustomChangeWrapper) change).setParam(paramName, textString);
                text = new StringBuffer();
                paramName = null;
            } else if ((changeSet != null) && "comment".equals(qName)) {
                changeSet.setComments(textString);
                text = new StringBuffer();
            } else if ((changeSet != null) && "changeSet".equals(qName)) {
                handleChangeSet(changeSet);
                changeSet = null;
            } else if ((change != null) && qName.equals("column") && (textString != null)) {
                if (change instanceof InsertDataChange) {
                    List<ColumnConfig> columns = ((InsertDataChange) change).getColumns();
                    columns.get(columns.size() - 1).setValue(textString);
                } else if (change instanceof UpdateDataChange) {
                    List<ColumnConfig> columns = ((UpdateDataChange) change).getColumns();
                    columns.get(columns.size() - 1).setValue(textString);
                } else {
                    throw new RuntimeException("Unexpected column with text: " + textString);
                }
                text = new StringBuffer();
            } else if ((change != null) && (change instanceof AbstractModifyDataChange) && qName.equals("param")
                    && (textString != null)) {
                List<ColumnConfig> columns = ((AbstractModifyDataChange) change)
                        .getWhereParams();
                columns.get(columns.size() - 1).setValue(textString);
                text = new StringBuffer();
            } else if ((change != null)
                    && localName.equals(changeFactory.getChangeMetaData(change).getName())) {
                if (textString != null) {
                    if (change instanceof RawSQLChange) {
                        // We've already expanded expressions when we defined 'textString' above. If we enabled
                        // escaping, we cannot re-expand; the now-literal variables in the text would get
                        // incorrectly expanded. If we haven't enabled escaping, then retain the current behavior.
                        String expandedExpression = textString;

                        if (false == ChangeLogParameters.EnableEscaping) {
                            expandedExpression = changeLogParameters.expandExpressions(textString);
                        }
                        ((RawSQLChange) change).setSql(expandedExpression);
                    } else if (change instanceof CreateProcedureChange) {
                        ((CreateProcedureChange) change).setProcedureText(textString);
                        // } else if (change instanceof AlterViewChange) {
                        // ((AlterViewChange)
                        // change).setSelectQuery(textString);
                    } else if (change instanceof CreateViewChange) {
                        ((CreateViewChange) change).setSelectQuery(textString);
                    } else if (change instanceof StopChange) {
                        ((StopChange) change).setMessage(textString);
                    } else {
                        throw new RuntimeException("Unexpected text in "
                                + changeFactory.getChangeMetaData(change).getName());
                    }
                }
                text = null;
                if (inRollback) {
                    changeSet.addRollbackChange(change);
                } else {
                    changeSet.addChange(change);
                }
                change = null;
            } else if ((changeSet != null) && "validCheckSum".equals(qName)) {
                changeSet.addValidCheckSum(text.toString());
                text = null;
            } else if ("modifySql".equals(qName)) {
                inModifySql = false;
                modifySqlDbmsList = null;
                modifySqlContexts = null;
                modifySqlAppliedOnRollback = false;
            }
        } catch (Exception e) {
            log.severe("Error thrown as a SAXException: " + e.getMessage(), e);
            throw new SAXException(databaseChangeLog.getPhysicalFilePath() + ": " + e.getMessage(), e);
        }
    }

    public DatabaseChangeLog getDatabaseChangeLog() {
        return databaseChangeLog;
    }

    private <T extends ColumnConfig> T getLastColumnConfigFromChange() {
        T result = null;
        if (change instanceof ChangeWithColumns) {
            List<T> columns = ((ChangeWithColumns) change).getColumns();
            if (columns.size() > 0) {
                result = columns.get(columns.size() - 1);
            }
        } else {
            throw new RuntimeException("Unexpected change: " + change.getClass().getName());
        }
        return result;
    }

    protected void handleChangeSet(final ChangeSet changeSet) {
        databaseChangeLog.addChangeSet(changeSet);
    }

    protected boolean handleIncludedChangeLog(String fileName, final boolean isRelativePath,
            final String relativeBaseFileName)
            throws LiquibaseException {

        if (fileName.equalsIgnoreCase(".svn") || fileName.equalsIgnoreCase("cvs")) {
            return false;
        }

        ResourceAccessor resourceAccessorToUse = resourceAccessor;
        if (fileName.startsWith(LiquibaseOSGiUtil.INCLUDE_FILE_OSGI_PREFIX)) {
            if (!(resourceAccessor instanceof OSGiResourceAccessor)) {
                throw new ChangeLogParseException("Inclusion '" + fileName
                        + "' refers to an OSGi based dependency but the resourceAccessor"
                        + " has different type from OSGiResourceAccessor");
            }
            OSGiResourceAccessor osgiResourceAccessor = (OSGiResourceAccessor) resourceAccessor;
            fileName = fileName.substring(LiquibaseOSGiUtil.INCLUDE_FILE_OSGI_PREFIX.length());
            BundleWire bundleWire = LiquibaseOSGiUtil.findMatchingWireBySchemaExpression(
                    osgiResourceAccessor.getBundle(), fileName);

            if (bundleWire == null) {
                throw new ChangeLogParseException("No matching bundle wire for inclusion: " + fileName);
            }

            fileName = String.valueOf(
                    bundleWire.getCapability().getAttributes().get(LiquibaseOSGiUtil.ATTR_SCHEMA_RESOURCE));
            Bundle providerBundle = bundleWire.getProviderWiring().getBundle();

            String changeLogLocation = providerBundle.getLocation() + "@" + fileName;
            Set<String> processedChangeLogsOnThread = OSGiXMLChangeLogSAXParser.getProcessedChangeLogsOnThread();
            if (processedChangeLogsOnThread.contains(changeLogLocation)) {
                return false;
            } else {
                processedChangeLogsOnThread.add(changeLogLocation);
            }

            Map<String, Object> attributes = bundleWire.getCapability().getAttributes();
            resourceAccessorToUse = new OSGiResourceAccessor(providerBundle, attributes);

        } else if (isRelativePath) {
            // workaround for FilenameUtils.normalize() returning null for relative paths like ../conf/liquibase.xml
            String tempFile = FilenameUtils.concat(FilenameUtils.getFullPath(relativeBaseFileName), fileName);
            if ((tempFile != null) && (new File(tempFile).exists() == true)) {
                fileName = tempFile;
            } else {
                fileName = FilenameUtils.getFullPath(relativeBaseFileName) + fileName;
            }
        }
        DatabaseChangeLog changeLog;
        try {
            changeLog = changeLogParserFactory.getParser(fileName, resourceAccessorToUse).parse(fileName,
                    changeLogParameters,
                    resourceAccessorToUse);
        } catch (UnknownChangelogFormatException e) {
            throw new ChangeLogParseException("included file " + fileName + " is not a recognized file type");
        }
        PreconditionContainer preconditions = changeLog.getPreconditions();
        if (preconditions != null) {
            if (null == databaseChangeLog.getPreconditions()) {
                databaseChangeLog.setPreconditions(new PreconditionContainer());
            }
            databaseChangeLog.getPreconditions().addNestedPrecondition(preconditions);
        }
        for (ChangeSet changeSet : changeLog.getChangeSets()) {
            handleChangeSet(changeSet);
        }

        return true;
    }

    protected void handlePreCondition(@SuppressWarnings("unused") final Precondition precondition) {
        databaseChangeLog.setPreconditions(rootPrecondition);
    }

    private void populateColumnFromAttributes(final Attributes atts, final ColumnConfig column)
            throws IllegalAccessException,
            InvocationTargetException, CustomChangeException {
        for (int i = 0; i < atts.getLength(); i++) {
            String attributeName = atts.getLocalName(i);
            String attributeValue = atts.getValue(i);
            setProperty(column, attributeName, attributeValue);
        }
    }

    private void setAllProperties(final Object object, final Attributes atts) throws IllegalAccessException,
            InvocationTargetException,
            CustomChangeException {
        for (int i = 0; i < atts.getLength(); i++) {
            String attributeName = atts.getQName(i);
            String attributeValue = atts.getValue(i);
            setProperty(object, attributeName, attributeValue);
        }
    }

    private void setProperty(final Object object, final String attributeName, final String attributeValue)
            throws IllegalAccessException,
            InvocationTargetException, CustomChangeException {
        if (object instanceof CustomChangeWrapper) {
            if (attributeName.equals("class")) {
                ((CustomChangeWrapper) object).setClass(changeLogParameters.expandExpressions(attributeValue));
            } else {
                ((CustomChangeWrapper) object).setParam(attributeName,
                        changeLogParameters.expandExpressions(attributeValue));
            }
        } else {
            ObjectUtil.setProperty(object, attributeName, changeLogParameters.expandExpressions(attributeValue));
        }
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName,
            final Attributes baseAttributes) throws SAXException {
        Attributes atts = new ExpandingAttributes(baseAttributes);
        try {
            if ("comment".equals(qName)) {
                text = new StringBuffer();
            } else if ("validCheckSum".equals(qName)) {
                text = new StringBuffer();
            } else if ("databaseChangeLog".equals(qName)) {
                String schemaLocation = atts.getValue("xsi:schemaLocation");
                if (schemaLocation != null) {
                    Matcher matcher = Pattern.compile(".*dbchangelog-(\\d+\\.\\d+).xsd").matcher(schemaLocation);
                    if (matcher.matches()) {
                        String version = matcher.group(1);
                        if (!version.equals(OSGiXMLChangeLogSAXParser
                                .getSchemaVersion())) {
                            log.warning(databaseChangeLog.getPhysicalFilePath()
                                    + " is using schema version " + version
                                    + " rather than version "
                                    + OSGiXMLChangeLogSAXParser.getSchemaVersion());
                        }
                    }
                }
                databaseChangeLog.setLogicalFilePath(atts.getValue("logicalFilePath"));
                ObjectQuotingStrategy quotingStrategy = ObjectQuotingStrategy.LEGACY;
                String quotingStrategyText = atts.getValue("objectQuotingStrategy");
                if (quotingStrategyText != null) {
                    quotingStrategy = ObjectQuotingStrategy.valueOf(quotingStrategyText);
                }
                databaseChangeLog.setObjectQuotingStrategy(quotingStrategy);
            } else if ("include".equals(qName)) {
                String fileName = atts.getValue("file");
                fileName = fileName.replace('\\', '/');
                boolean isRelativeToChangelogFile = Boolean.parseBoolean(atts.getValue("relativeToChangelogFile"));
                handleIncludedChangeLog(fileName, isRelativeToChangelogFile, databaseChangeLog.getPhysicalFilePath());
            } else if ("includeAll".equals(qName)) {
                String pathName = atts.getValue("path");
                // Replace backwards slash with normal slash
                pathName = pathName.replace('\\', '/');

                // Add the normal slash to the end of the path, if not present already
                if (!(pathName.endsWith("/"))) {
                    pathName = pathName + '/';
                }
                
                log.debug("includeAll for " + pathName);
                log.debug("Using file opener for includeAll: " + resourceAccessor.toString());
                
                boolean isRelativeToChangelogFile = Boolean.parseBoolean(atts.getValue("relativeToChangelogFile"));

                String resourceFilterDef = atts.getValue("resourceFilter");
                IncludeAllFilter resourceFilter = null;
                if (resourceFilterDef != null) {
                    resourceFilter = (IncludeAllFilter) Class.forName(resourceFilterDef).newInstance();
                }
                
                // Check if we are in a OSGi context
                String base = FilenameUtils.getFullPath(pathName);
				Enumeration<URL> res = resourceAccessor.getResources(base);
                while (res.hasMoreElements()) {
					URL url = res.nextElement();
					LogFactory.getInstance().getLog().info(String.format("Found file resource at [%s]: [%s]", base, url.toString()));
				}
                
                // Relative to changelog
                if (isRelativeToChangelogFile) {
                	// Resolve the relative path of folder to include
                    IFile changeLogFile = null;

                    boolean osgiContext = resourceAccessor instanceof OSGiResourceAccessor;
                    
					if (osgiContext) {
                    	// Cria o file
						OSGiResourceAccessor resAccessor = (OSGiResourceAccessor) resourceAccessor;
						changeLogFile = new BundleFile(resAccessor.getBundle(), databaseChangeLog.getPhysicalFilePath());
					} else {
	                    Enumeration<URL> resources = resourceAccessor.getResources(databaseChangeLog.getPhysicalFilePath());
	                    
	                    // Does the changelog file being processed exist? Can we get its path?
	                    while (resources.hasMoreElements()) {
	                        try {
	                            changeLogFile = new NormalFile(new File(resources.nextElement().toURI()));
	                        } catch (URISyntaxException e) {
	                            continue; // ignore error, probably a URL or something like that
	                        }
	                        if (changeLogFile.exists()) {
	                            break;
	                        } else {
	                            changeLogFile = null;
	                        }
	                    }
					}

                    if (changeLogFile == null) {
                        throw new SAXException("Cannot determine physical location of " + databaseChangeLog.getPhysicalFilePath());
                    }
                    
                    // Get the base "folder" where all xmls are included
//                    File resourceBase = new File(changeLogFile.getParentFile(), pathName);
                    IFile resourceBase = null;
                    
                    if (osgiContext) {
                    	// It's OSGi
                    	OSGiResourceAccessor resAccessor = (OSGiResourceAccessor) resourceAccessor;
                    	resourceBase = new BundleFile(resAccessor.getBundle(), changeLogFile.getParentFile(), pathName);
                    	
                    } else
                    	resourceBase = new NormalFile(new File(changeLogFile.getParentFile().getPath(), pathName));
                    
                    // Check if the base folder exists
                    if (!resourceBase.exists()) {
                        throw new SAXException(String.format("Resource directory for includeAll does not exist [%s]", 
                        		resourceBase.getCanonicalPath()));
                    }

                    pathName = resourceBase.getPath();
                    // Remove the parent folder absolute path from the path, to keep only the relative
                    pathName = pathName.replaceFirst("^\\Q" + changeLogFile.getParentFile().getAbsolutePath() + "\\E", "");
                    // Get only the last "part" of the path: "test/one/directory" becomes "/directory"
                    pathName = databaseChangeLog.getFilePath().replaceFirst("/[^/]*$", "") + pathName;
                    // Transform into unix path syntax
                    pathName = pathName.replace('\\', '/');
                    // If path does not end with "/", add it because it should be a folder
                    if (!pathName.endsWith("/")) {
                        pathName = pathName + "/";
                    }

                    while (pathName.matches(".*/\\.\\./.*")) {
                        pathName = pathName.replaceFirst("[^/]+/\\.\\.", "/");
                    }
                }

                Enumeration<URL> resourcesEnum = resourceAccessor.getResources(pathName);
                SortedSet<URL> resources = new TreeSet<URL>(new Comparator<URL>() {
                    @Override
                    public int compare(final URL o1, final URL o2) {
                        return o1.toString().compareTo(o2.toString());
                    }
                });
                while (resourcesEnum.hasMoreElements()) {
                    resources.add(resourcesEnum.nextElement());
                }

                boolean foundResource = false;

                Set<String> seenPaths = new HashSet<String>();
                List<String> includedChangeLogs = new LinkedList<String>();
                for (URL fileUrl : resources) {
                	// Check if it is a bundle file
                	boolean isBundleFile = fileUrl.getProtocol().equals("bundleentry") || fileUrl.getProtocol().equals("bundleresource");
                    
                	if (!fileUrl.toExternalForm().startsWith("file:") && !isBundleFile) { // Bundle files are treated differently
                    	// Extract the compressed file
                        if (fileUrl.toExternalForm().startsWith("jar:file:")
                                || fileUrl.toExternalForm().startsWith("wsjar:file:")
                                || fileUrl.toExternalForm().startsWith("zip:")) {
                            File zipFileDir = OSGiXMLChangeLogSAXHandler.extractZipFile(fileUrl);
                            if (pathName.startsWith("classpath:")) {
                                log.debug("replace classpath");
                                pathName = pathName.replaceFirst("classpath:", "");
                            }
                            if (pathName.startsWith("classpath*:")) {
                                log.debug("replace classpath*");
                                pathName = pathName.replaceFirst("classpath\\*:", "");
                            }
                            URI fileUri = new File(zipFileDir, pathName).toURI();
                            fileUrl = fileUri.toURL();
                        } else {
                            log.debug(fileUrl.toExternalForm() + " is not a file path");
                            continue;
                        }
                    }
                    
                    IFile file = null;
                    
					if(isBundleFile) {
                    	// OSGi resource... treat it differently
                    	OSGiResourceAccessor accessor = (OSGiResourceAccessor) resourceAccessor;
                    	file = new BundleFile(accessor.getBundle(), fileUrl.getPath());
                    } else
                    	file = new NormalFile(new File(fileUrl.toURI()));
                    
                    log.debug("includeAll using path " + file.getCanonicalPath());
                    // Check if file exists and if it is a directory
                    if (!file.exists()) {
                        throw new SAXException("includeAll path " + pathName + " could not be found. Tried in "
                                + file.toString());
                    }
                    // If it's a directory, then must traverse the directory too
                    if (file.isDirectory()) {
                        log.debug(file.getCanonicalPath() + " is a directory");
                        for (IFile childFile : new TreeSet<IFile>(file.listFiles())) {
                            String path = pathName + childFile.getName();
                            if (!seenPaths.add(path)) {
                                log.debug("already included " + path);
                                continue;
                            }

                            includedChangeLogs.add(path);
                        }
                    } else {
                        String path = pathName + file.getName();
                        if (!seenPaths.add(path)) {
                            log.debug("already included " + path);
                            continue;
                        }
                        includedChangeLogs.add(path);
                    }
                }
                if (resourceFilter != null) {
                    includedChangeLogs = resourceFilter.filter(includedChangeLogs);
                }

                for (String path : includedChangeLogs) {
                    if (handleIncludedChangeLog(path, false, databaseChangeLog.getPhysicalFilePath())) {
                        foundResource = true;
                    }
                }

                if (!foundResource) {
                    throw new SAXException("Could not find directory or directory was empty for includeAll '"
                            + pathName + "'");
                }
            } else if ((changeSet == null) && "changeSet".equals(qName)) {
                boolean alwaysRun = false;
                boolean runOnChange = false;
                if ("true".equalsIgnoreCase(atts.getValue("runAlways"))) {
                    alwaysRun = true;
                }
                if ("true".equalsIgnoreCase(atts.getValue("runOnChange"))) {
                    runOnChange = true;
                }
                String filePath = atts.getValue("logicalFilePath");
                if ((filePath == null) || "".equals(filePath)) {
                    filePath = databaseChangeLog.getFilePath();
                }

                ObjectQuotingStrategy quotingStrategy = databaseChangeLog.getObjectQuotingStrategy();
                String quotingStrategyText = atts.getValue("objectQuotingStrategy");
                if (quotingStrategyText != null) {
                    quotingStrategy = ObjectQuotingStrategy.valueOf(quotingStrategyText);
                }

                changeSet = new ChangeSet(atts.getValue("id"), atts.getValue("author"), alwaysRun, runOnChange,
                        filePath,
                        atts.getValue("context"), atts.getValue("dbms"), Boolean.valueOf(atts
                                .getValue("runInTransaction")),
                        quotingStrategy, databaseChangeLog);
                if (StringUtils.trimToNull(atts.getValue("failOnError")) != null) {
                    changeSet.setFailOnError(Boolean.parseBoolean(atts.getValue("failOnError")));
                }
                if (StringUtils.trimToNull(atts.getValue("onValidationFail")) != null) {
                    changeSet.setOnValidationFail(ChangeSet.ValidationFailOption.valueOf(atts
                            .getValue("onValidationFail")));
                }
                changeSet.setChangeLogParameters(changeLogParameters);

            } else if ((changeSet != null) && "rollback".equals(qName)) {
                text = new StringBuffer();
                String id = atts.getValue("changeSetId");
                if (id != null) {
                    String path = atts.getValue("changeSetPath");
                    if (path == null) {
                        path = databaseChangeLog.getFilePath();
                    }
                    String author = atts.getValue("changeSetAuthor");
                    ChangeSet changeSet = databaseChangeLog.getChangeSet(path, author, id);
                    if (changeSet == null) {
                        throw new SAXException("Could not find changeSet to use for rollback: " + path + ":" + author
                                + ":" + id);
                    } else {
                        for (Change change : changeSet.getChanges()) {
                            this.changeSet.addRollbackChange(change);
                        }
                    }
                }
                inRollback = true;
            } else if ("preConditions".equals(qName)) {
                rootPrecondition = new PreconditionContainer();
                rootPrecondition.setOnFail(StringUtils.trimToNull(atts.getValue("onFail")));
                rootPrecondition.setOnError(StringUtils.trimToNull(atts.getValue("onError")));
                rootPrecondition.setOnFailMessage(StringUtils.trimToNull(atts.getValue("onFailMessage")));
                rootPrecondition.setOnErrorMessage(StringUtils.trimToNull(atts.getValue("onErrorMessage")));
                rootPrecondition.setOnSqlOutput(StringUtils.trimToNull(atts.getValue("onSqlOutput")));
                preconditionLogicStack.push(rootPrecondition);
            } else if ((currentPrecondition != null) && (currentPrecondition instanceof CustomPreconditionWrapper)
                    && qName.equals("param")) {
                ((CustomPreconditionWrapper) currentPrecondition).setParam(atts.getValue("name"),
                        atts.getValue("value"));
            } else if (rootPrecondition != null) {
                currentPrecondition = preconditionFactory.create(localName);

                setAllProperties(currentPrecondition, atts);
                preconditionLogicStack.peek().addNestedPrecondition(currentPrecondition);

                if (currentPrecondition instanceof PreconditionLogic) {
                    preconditionLogicStack.push(((PreconditionLogic) currentPrecondition));
                }

                if ("sqlCheck".equals(qName)) {
                    text = new StringBuffer();
                }
            } else if ("modifySql".equals(qName)) {
                inModifySql = true;
                if (StringUtils.trimToNull(atts.getValue("dbms")) != null) {
                    modifySqlDbmsList = new HashSet<String>(StringUtils.splitAndTrim(atts.getValue("dbms"), ","));
                }
                if (StringUtils.trimToNull(atts.getValue("context")) != null) {
                    modifySqlContexts = new Contexts(atts.getValue("context"));
                }
                if (StringUtils.trimToNull(atts.getValue("applyToRollback")) != null) {
                    modifySqlAppliedOnRollback = Boolean.valueOf(atts.getValue("applyToRollback"));
                }
            } else if (inModifySql) {
                SqlVisitor sqlVisitor = sqlVisitorFactory.create(localName);
                for (int i = 0; i < atts.getLength(); i++) {
                    String attributeName = atts.getLocalName(i);
                    String attributeValue = atts.getValue(i);
                    setProperty(sqlVisitor, attributeName, attributeValue);
                }
                sqlVisitor.setApplicableDbms(modifySqlDbmsList);
                sqlVisitor.setApplyToRollback(modifySqlAppliedOnRollback);
                sqlVisitor.setContexts(modifySqlContexts);

                changeSet.addSqlVisitor(sqlVisitor);
            } else if ((changeSet != null) && (change == null)) {
                change = changeFactory.create(localName);
                if (change == null) {
                    throw new SAXException("Unknown Liquibase extension: " + localName
                            + ".  Are you missing a jar from your classpath?");
                }
                change.setChangeSet(changeSet);
                text = new StringBuffer();
                if (change == null) {
                    throw new MigrationFailedException(changeSet, "Unknown change: " + localName);
                }
                change.setResourceAccessor(resourceAccessor);
                if (change instanceof CustomChangeWrapper) {
                    ((CustomChangeWrapper) change).setClassLoader(resourceAccessor.toClassLoader());
                }

                setAllProperties(change, atts);
                change.finishInitialization();
            } else if ((change != null) && "column".equals(qName)) {
                ColumnConfig column;
                if (change instanceof LoadDataChange) {
                    column = new LoadDataColumnConfig();
                } else if ((change instanceof AddColumnChange) || (change instanceof CreateIndexChange)) {
                    column = new AddColumnConfig();
                } else if (change instanceof CreateIndexChange) {
                    column = new AddColumnConfig();
                } else {
                    column = new ColumnConfig();
                }
                populateColumnFromAttributes(atts, column);
                if (change instanceof ChangeWithColumns) {
                    ((ChangeWithColumns) change).addColumn(column);
                } else {
                    throw new RuntimeException("Unexpected column tag for " + change.getClass().getName());
                }
            } else if ((change != null) && "whereParams".equals(qName)) {
                if (!(change instanceof AbstractModifyDataChange)) {
                    throw new RuntimeException("Unexpected change: " + change.getClass().getName());
                }
            } else if ((change != null) && (change instanceof AbstractModifyDataChange) && "param".equals(qName)) {
                ColumnConfig param = new ColumnConfig();
                populateColumnFromAttributes(atts, param);
                ((AbstractModifyDataChange) change).addWhereParam(param);
            } else if ((change != null) && "constraints".equals(qName)) {
                ConstraintsConfig constraints = new ConstraintsConfig();
                for (int i = 0; i < atts.getLength(); i++) {
                    String attributeName = atts.getLocalName(i);
                    String attributeValue = atts.getValue(i);
                    setProperty(constraints, attributeName, attributeValue);
                }
                ColumnConfig lastColumn = null;
                if (change instanceof ChangeWithColumns) {
                    List<ColumnConfig> columns = ((ChangeWithColumns) change).getColumns();
                    if ((columns != null) && (columns.size() > 0)) {
                        lastColumn = columns.get(columns.size() - 1);
                    }
                } else {
                    throw new RuntimeException("Unexpected change: " + change.getClass().getName());
                }
                if (lastColumn == null) {
                    throw new RuntimeException("Could not determine column to add constraint to");
                }
                lastColumn.setConstraints(constraints);
            } else if ("param".equals(qName) && (change instanceof CustomChangeWrapper)) {
                if (atts.getValue("value") == null) {
                    paramName = atts.getValue("name");
                    text = new StringBuffer();
                } else {
                    ((CustomChangeWrapper) change).setParam(atts.getValue("name"), atts.getValue("value"));
                }
            } else if ("where".equals(qName)) {
                text = new StringBuffer();
            } else if ("property".equals(qName)) {
                String context = StringUtils.trimToNull(atts.getValue("context"));
                String dbms = StringUtils.trimToNull(atts.getValue("dbms"));
                if (StringUtils.trimToNull(atts.getValue("file")) == null) {
                    changeLogParameters.set(atts.getValue("name"), atts.getValue("value"), context, dbms);
                } else {
                    Properties props = new Properties();
                    InputStream propertiesStream = resourceAccessor.getResourceAsStream(atts.getValue("file"));
                    if (propertiesStream == null) {
                        log.info("Could not open properties file " + atts.getValue("file"));
                    } else {
                        props.load(propertiesStream);

                        for (Map.Entry entry : props.entrySet()) {
                            changeLogParameters.set(entry.getKey().toString(), entry.getValue().toString(),
                                    context, dbms);
                        }
                    }
                }
            } else if ((change instanceof ExecuteShellCommandChange) && "arg".equals(qName)) {
                ((ExecuteShellCommandChange) change).addArg(atts.getValue("value"));
            } else if (change != null) {
                String creatorMethod = "create" + localName.substring(0, 1).toUpperCase() + localName.substring(1);

                Object objectToCreateFrom;
                if (changeSubObjects.size() == 0) {
                    objectToCreateFrom = change;
                } else {
                    objectToCreateFrom = changeSubObjects.peek();
                }

                Method method;
                try {
                    method = objectToCreateFrom.getClass().getMethod(creatorMethod);
                } catch (NoSuchMethodException e) {
                    throw new MigrationFailedException(changeSet, "Could not find creator method " + creatorMethod
                            + " for tag: "
                            + localName);
                }
                Object subObject = method.invoke(objectToCreateFrom);
                setAllProperties(subObject, atts);

                changeSubObjects.push(subObject);
            } else {
                throw new MigrationFailedException(changeSet, "Unexpected tag: " + localName);
            }
        } catch (Exception e) {
            log.severe("Error thrown as a SAXException: " + e.getMessage(), e);
            throw new SAXException(e);
        }
    }
}
