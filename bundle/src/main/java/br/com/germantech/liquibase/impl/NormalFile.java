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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import br.com.germantech.liquibase.IFile;

public class NormalFile implements IFile {
	
	private final File file;

	public NormalFile(File file) {
		super();
		this.file = file;
	}

	@Override
	public List<IFile> listFiles() {
		File[] files = file.listFiles();
		if(files != null) {
			List<IFile> iFiles = new ArrayList<IFile>();
			for (File file : files) {
				iFiles.add(new NormalFile(file));
			}
			return iFiles;
		}
		return Collections.emptyList();
	}

	@Override
	public boolean exists() {
		return file.exists();
	}

	@Override
	public boolean isDirectory() {
		return file.isDirectory();
	}

	@Override
	public String getCanonicalPath() throws IOException {
		return file.getCanonicalPath();
	}

	@Override
	public String getName() {
		return file.getName();
	}

}
