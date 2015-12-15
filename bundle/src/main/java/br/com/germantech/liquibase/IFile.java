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
package br.com.germantech.liquibase;

import java.io.IOException;
import java.util.List;

public interface IFile {
	boolean exists();
	boolean isDirectory();
	String getCanonicalPath() throws IOException;
	String getPath();
	String getName();
	List<IFile> listFiles();
	IFile getParentFile();
	/**
	 * Returns the absolute path of the file.
	 * @return absolute path, without leading slash if it's a directory.
	 */
	String getAbsolutePath();
}
