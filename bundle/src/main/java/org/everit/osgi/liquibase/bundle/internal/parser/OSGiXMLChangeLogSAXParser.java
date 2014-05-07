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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.exception.ChangeLogParseException;
import liquibase.logging.LogFactory;
import liquibase.parser.core.xml.LiquibaseEntityResolver;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;
import liquibase.resource.ResourceAccessor;
import liquibase.resource.UtfBomStripperInputStream;
import liquibase.util.file.FilenameUtils;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

public class OSGiXMLChangeLogSAXParser extends XMLChangeLogSAXParser {

    private SAXParserFactory saxParserFactory;

    private static ThreadLocal<Set<String>> processedChangeLogsOnThread = new ThreadLocal<Set<String>>();

    public static Set<String> getProcessedChangeLogsOnThread() {
        return processedChangeLogsOnThread.get();
    }

    public static String getSchemaVersion() {
        return "3.1";
    }

    public OSGiXMLChangeLogSAXParser() {
        saxParserFactory = SAXParserFactory.newInstance();

        if (System.getProperty("java.vm.version").startsWith("1.4")) {
            saxParserFactory.setValidating(false);
            saxParserFactory.setNamespaceAware(false);
        } else {
            saxParserFactory.setValidating(true);
            saxParserFactory.setNamespaceAware(true);
        }
    }

    @Override
    public int getPriority() {
        return PRIORITY_DEFAULT + 1;
    }

    @Override
    public DatabaseChangeLog parse(final String physicalChangeLogLocation,
            final ChangeLogParameters changeLogParameters, final ResourceAccessor resourceAccessor)
            throws ChangeLogParseException {

        InputStream inputStream = null;

        boolean processedChangeLogsInitialized = (processedChangeLogsOnThread.get() != null);
        if (!processedChangeLogsInitialized) {
            processedChangeLogsOnThread.set(new HashSet<String>());
        }

        try {
            SAXParser parser = saxParserFactory.newSAXParser();
            try {
                parser.setProperty("http://java.sun.com/xml/jaxp/properties/schemaLanguage",
                        "http://www.w3.org/2001/XMLSchema");
            } catch (SAXNotRecognizedException e) {
                // ok, parser must not support it
            } catch (SAXNotSupportedException e) {
                // ok, parser must not support it
            }

            XMLReader xmlReader = parser.getXMLReader();
            LiquibaseEntityResolver resolver = new LiquibaseEntityResolver(this);
            resolver.useResoureAccessor(resourceAccessor, FilenameUtils.getFullPath(physicalChangeLogLocation));
            xmlReader.setEntityResolver(resolver);
            xmlReader.setErrorHandler(new ErrorHandler() {
                @Override
                public void error(final SAXParseException exception) throws SAXException {
                    LogFactory.getLogger().severe(exception.getMessage());
                    throw exception;
                }

                @Override
                public void fatalError(final SAXParseException exception) throws SAXException {
                    LogFactory.getLogger().severe(exception.getMessage());
                    throw exception;
                }

                @Override
                public void warning(final SAXParseException exception) throws SAXException {
                    LogFactory.getLogger().warning(exception.getMessage());
                    throw exception;
                }
            });

            inputStream = resourceAccessor.getResourceAsStream(physicalChangeLogLocation);
            if (inputStream == null) {
                throw new ChangeLogParseException(physicalChangeLogLocation + " does not exist");
            }

            OSGiXMLChangeLogSAXHandler contentHandler = new OSGiXMLChangeLogSAXHandler(physicalChangeLogLocation,
                    resourceAccessor, changeLogParameters);
            xmlReader.setContentHandler(contentHandler);
            xmlReader.parse(new InputSource(new UtfBomStripperInputStream(inputStream)));

            return contentHandler.getDatabaseChangeLog();
        } catch (ChangeLogParseException e) {
            throw e;
        } catch (IOException e) {
            throw new ChangeLogParseException("Error Reading Migration File: " + e.getMessage(), e);
        } catch (SAXParseException e) {
            throw new ChangeLogParseException("Error parsing line " + e.getLineNumber() + " column "
                    + e.getColumnNumber() + " of " + physicalChangeLogLocation + ": " + e.getMessage(), e);
        } catch (SAXException e) {
            Throwable parentCause = e.getException();
            while (parentCause != null) {
                if (parentCause instanceof ChangeLogParseException) {
                    throw ((ChangeLogParseException) parentCause);
                }
                parentCause = parentCause.getCause();
            }
            String reason = e.getMessage();
            String causeReason = null;
            if (e.getCause() != null) {
                causeReason = e.getCause().getMessage();
            }

            // if (reason == null && causeReason==null) {
            // reason = "Unknown Reason";
            // }
            if (reason == null) {
                if (causeReason != null) {
                    reason = causeReason;
                } else {
                    reason = "Unknown Reason";
                }
            }

            throw new ChangeLogParseException("Invalid Migration File: " + reason, e);
        } catch (Exception e) {
            throw new ChangeLogParseException(e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // probably ok
                }
            }
            if (!processedChangeLogsInitialized) {
                processedChangeLogsOnThread.set(null);
            }
        }
    }

    @Override
    public boolean supports(final String changeLogFile, final ResourceAccessor resourceAccessor) {
        return changeLogFile.endsWith("xml");
    }
}
