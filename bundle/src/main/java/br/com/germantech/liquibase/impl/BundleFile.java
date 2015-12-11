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

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import org.osgi.framework.Bundle;

import br.com.germantech.liquibase.IFile;
import liquibase.util.file.FilenameUtils;

public class BundleFile implements IFile {

	private final Bundle bundle;
	private String filePath;
	private URL resource;
	
	public BundleFile(Bundle bundle, String fileName) {
		super();
		this.bundle = bundle;
		this.filePath = fileName;
		this.resource = bundle.getResource(fileName);
	}

	@Override
	public boolean exists() {
		return resource != null;
	}

	@Override
	public boolean isDirectory() {
		return resource != null && resource.getPath().endsWith("/");
	}

	@Override
	public String getCanonicalPath() {
		return resource.getPath();
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

}
