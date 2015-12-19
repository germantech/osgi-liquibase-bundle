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
package br.com.germantech.liquibase.impl;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import org.osgi.framework.Bundle;

import br.com.germantech.liquibase.IFile;
import liquibase.logging.LogFactory;
import liquibase.logging.Logger;
import liquibase.util.file.FilenameUtils;

public class BundleFile implements IFile {

	private final Bundle bundle;
	private String filePath;
	private URL resource;
	private Logger logger = LogFactory.getInstance().getLog();
	
	
	public BundleFile(Bundle bundle, String fileName) {
		this(bundle, null, fileName);
	}
	
	public BundleFile(Bundle bundle, IFile parentPath, String fileName) {
		super();
		this.bundle = bundle;
		this.filePath = fileName;
		String fullPath = null;
		// It can go wrong
		try {
			IFile parent = parentPath == null ? new EmptyFile() : parentPath;
			fullPath = parent.getCanonicalPath()+fileName;
			
			logger.debug(String.format("@[%s] BundleFile created with fileName [%s] and parent file [%s]. Resulting path: [%s]", hashCode(), fileName, parent.getCanonicalPath(), fullPath));
			
			// Try to translate the path
			String resolvedFileName = FilenameUtils.normalize(fullPath);
			this.resource = bundle.getResource(resolvedFileName);
			
			logger.debug(String.format("@[%s] BundleFile.resource = [%s]. Path used [%s], resolved: [%s], plugin: [%s]", hashCode(), resource, fullPath, resolvedFileName, bundle.getSymbolicName()));
		} catch (IOException e) {
			LogFactory.getInstance().getLog().info(String.format("@[%s] File not found [%s]", hashCode(), fullPath), e);
		}
		
	}

	@Override
	public boolean exists() {
		// TODO What if we have a relative path?
		return resource != null;
	}

	@Override
	public boolean isDirectory() {
		return resource != null && resource.getPath().endsWith("/");
	}

	@Override
	public String getCanonicalPath() {
		// This guy should return the "resolved" path, in case the resource
		// was created with a relative path
		return resource == null ? "" : resource.getPath();
	}

	@Override
	public String getName() {
		return FilenameUtils.getName(filePath);
	}

	@Override
	public List<IFile> listFiles() {
		if(isDirectory()) {
			Enumeration<String> entries = bundle.getEntryPaths(filePath);
        	// Nothing was found
        	if(entries == null)
        		return Collections.emptyList();
        	
        	// Create files
        	Vector<IFile> files = new Vector<IFile>();
        	
        	while (entries.hasMoreElements()) {
        		URL entry = bundle.getEntry(entries.nextElement());
        		if(entry != null)
        			files.add(new BundleFile(bundle, entry.getPath()));
			}
        	
			return files;
		} else
			return Collections.emptyList();
	}

	@Override
	public IFile getParentFile() {
		String fullPath = FilenameUtils.getFullPath(filePath);
		logger.debug(String.format("@[%s].getParentFile for [%s] = [%s]", hashCode(), filePath, fullPath));
		return new BundleFile(bundle, fullPath);
	}

	@Override
	public String getPath() {
		return removeTrailingSlash(getCanonicalPath());
	}
	
	@Override
	public String getAbsolutePath() {
		return removeTrailingSlash(getCanonicalPath());
	}

	private String removeTrailingSlash(String str) {
		return str.replaceAll("/$", "");
	}
	
}
