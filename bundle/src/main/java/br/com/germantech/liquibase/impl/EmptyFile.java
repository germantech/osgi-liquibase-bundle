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
import java.util.Collections;
import java.util.List;

import br.com.germantech.liquibase.IFile;

public class EmptyFile implements IFile {
	
	public EmptyFile() {}

	@Override
	public List<IFile> listFiles() {
		return Collections.emptyList();
	}

	@Override
	public boolean exists() {
		return false;
	}

	@Override
	public boolean isDirectory() {
		return false;
	}

	@Override
	public String getCanonicalPath() throws IOException {
		return "";
	}

	@Override
	public String getName() {
		return "";
	}

	@Override
	public IFile getParentFile() {
		return null;
	}
	
	@Override
	public String getAbsolutePath() {
		return "";
	}

	@Override
	public String getPath() {
		return null;
	}

}
