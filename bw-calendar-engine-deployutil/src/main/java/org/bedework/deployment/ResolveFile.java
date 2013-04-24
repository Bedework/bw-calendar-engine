/* ********************************************************************
    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:
        
    http://www.apache.org/licenses/LICENSE-2.0
        
    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package org.bedework.deployment;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.PropertyHelper;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.util.FileUtils;

import java.io.File;

/** Ant task to resolve a file name. This is just a wrapper to the ant FileUtils
 * which calls resolveFile. Surely this can be done another way?
 *
 * <p>Task attributes are <ul>
 * <li>base             base to resolve against - must be an absolute path</li>
 * <li>file             Filename which may or may not be absolute</li>
 * <li>name             Name of new property</li>
 * </ul>
 *
 * @author douglm @ rpi.edu
 */
public class ResolveFile extends Task {
  protected String name;
  protected File base;
  protected File file;

  /**
   * helper for path -> URI and URI -> path conversions.
   */
  private static FileUtils fu = FileUtils.getFileUtils();

  /**
   * The name of the property to set.
   * @param name property name
   */
  public void setName(final String name) {
      this.name = name;
  }

  /**
   * @return String
   */
  public String getName() {
      return name;
  }

  /**
   * Base to resolve against.
   *
   * @param val filename
   */
  public void setBase(final File val) {
      base = val;
  }

  /**
   * @return File
   */
  public File getBase() {
      return base;
  }

  /**
   * Filename of file to resolve
   * @param val filename
   *
   * @ant.attribute group="noname"
   */
  public void setFile(final String val) {
      file = new File(val);
  }

  /** Execute the task
   */
  @Override
  public void execute() throws BuildException {
    try {
      if (getProject() == null) {
        throw new IllegalStateException("project has not been set");
      }

      if (name == null) {
        throw new BuildException("You must specify the name attribute");
      }

      if (base == null) {
        throw new BuildException("You must specify the base attribute");
      }

      if (!base.isAbsolute()) {
        throw new BuildException("The base attribute value must be an absolute path.");
      }

      if (file == null) {
        throw new BuildException("You must specify the file attribute");
      }

      if (!file.isAbsolute()) {
        file = fu.resolveFile(base, file.getPath());
      }

      PropertyHelper props = PropertyHelper.getPropertyHelper(getProject());

      props.setProperty(null, name, file.getAbsolutePath(), false);
    } catch (BuildException be) {
      throw be;
    } catch (Throwable t) {
      t.printStackTrace();
      throw new BuildException(t);
    }
  }
}
