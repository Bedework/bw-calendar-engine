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
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/** Ant task to build the application.xml file for a ear.
 *
 * Task attributes are <ul>
 * <li>outFile  The application.xml file we are creating</li>
 * <li>displayName  Optional display name</li>
 * <li>warDir    Directory containing war files or expanded wars with names
 *               ending in ".war"</li>
 * <li>contexts  property file defining context roots</li>
 * </ul>
 *
 * <p>Body is a fileset giving jar files to add.
 *
 * @author douglm @ bedework.edu
 */
public class ApplicationXmlTask extends MatchingTask {
  private List<FileSet> filesets = new LinkedList<FileSet>();

  private List<String> wars = new LinkedList<String>();

  private List<String> jars = new LinkedList<String>();

  private File warDir;

  private String displayName;

  private String contexts;

  private File outFile;
  private Writer wtr;
  private Properties contextProps;

  /** Add a fileset
   *
   * @param set
   */
  public void addFileset(final FileSet set) {
    filesets.add(set);
  }

  /** Set the display name
   *
   * @param val   String
   */
  public void setDisplayName(final String val) {
    displayName = val;
  }

  /** Set the contexts file name
   *
   * @param val   String
   */
  public void setContexts(final String val) {
    contexts = val;
  }

  /** Set the application.xml output file
   *
   * @param val   File
   */
  public void setOutFile(final File val) {
    outFile = val;
  }

  /** Set the directory containing wars
   *
   * @param val   File
   */
  public void setWarDir(final File val) {
    warDir = val;
  }

  /** Executes the task
   */
  @Override
  public void execute() throws BuildException {
    try {
      getModules();

      FileUtils.getFileUtils().createNewFile(outFile, true);

      wtr = new FileWriter(outFile);

      if ((contexts != null) && new File(contexts).exists()) {
        FileInputStream propFile = new FileInputStream(contexts);
        contextProps = new Properties();
        contextProps.load(propFile);
      }

      writeLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      writeLine("");
      writeLine("<application>");
      if (displayName != null) {
        writeLine("  <display-name>" + displayName + "</display-name>");
      }

      for (String nm: wars) {
        writeLine("");
        writeLine("  <module>");
        writeLine("    <web>");
        writeLine("      <web-uri>" + nm + "</web-uri>");

        String warName = nm.substring(0, nm.lastIndexOf(".war"));
        String contextRoot = null;

        if (contextProps != null) {
          contextRoot = contextProps.getProperty(warName + ".context");

          if (contextRoot == null) {
            throw new BuildException("No context root defined for " + warName);
          }

          if (contextRoot.length() == 0) {
            contextRoot = "/";
          }
        }

        if (contextRoot == null) {
          contextRoot = "/" + warName;
        }

        writeLine("      <context-root>" + contextRoot + "</context-root>");
        writeLine("    </web>");
        writeLine("  </module>");
      }

      for (String nm: jars) {
        writeLine("");
        writeLine("  <module>");
        writeLine("    <java>" + nm + "</java>");
        writeLine("  </module>");
      }

      writeLine("</application>");

      wtr.close();
    } catch (BuildException be) {
      be.printStackTrace();
      throw be;
    } catch (Throwable t) {
      t.printStackTrace();
      throw new BuildException(t);
    }
  }

  /* Scan the filesets and extract files that end with ".jar" and directories
   * or files that end with ".war"
   *
   */
  private void getModules() throws BuildException {
    FilenameFilter fltr = new FilenameFilter() {
      @Override
      public boolean accept(final File dir, final String name) {
        return name.endsWith(".war");
      }
    };

    if (warDir == null) {
      throw new BuildException("No wardir supplied");
    }

    String[] warnames = warDir.list(fltr);

    if (warnames == null) {
      throw new BuildException("No wars found at " + warDir);
    }

    for (int wi = 0; wi < warnames.length; wi++) {
      wars.add(warnames[wi]);
    }

    for (FileSet fs: filesets) {
      DirectoryScanner ds = fs.getDirectoryScanner(getProject());

      String[] dsFiles = ds.getIncludedFiles();

      for (int dsi = 0; dsi < dsFiles.length; dsi++) {
        String fname = dsFiles[dsi];

        if (fname.endsWith(".jar")) {
          jars.add(fname);
        } else if (fname.endsWith(".war")) {
          wars.add(fname);
        }
      }
    }
  }

  private void writeLine(final String ln) throws Throwable {
    wtr.write(ln);
    wtr.write("\n");
  }

}
