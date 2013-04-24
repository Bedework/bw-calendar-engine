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
import org.apache.tools.ant.Project;
import org.apache.tools.ant.PropertyHelper;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.TaskContainer;
import org.apache.tools.ant.taskdefs.Get;
import org.apache.tools.ant.util.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Ant task to define a dependency. Allows a fairly seamless migration towards
 * maven.
 *
 * @author douglm @ rpi.edu
 */
public class DependencyTask extends Task implements TaskContainer {
//  private static final String defaultLicenseInfoURL =
//      "https://source.jasig.org/licenses/license-mappings.xml";

//  private String licenseInfoURL;

  private String licenceInfoFilename = "${org.bedework.license-mappings}";

  private OutputStream licenceOs;

  private static class LicenseInfoType {
    String groupId;
    String artifactId;
    String license;
    String name;

    LicenseInfoType(final String groupId,
                    final String artifactId,
                    final String license,
                    final String name) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.license = license;
      this.name = name;
    }
  }

  private static Map<String, LicenseInfoType> licenseInfo;

  private static final int MKDIR_RETRY_SLEEP_MILLIS = 10;

  private static final String printLicenceInfo =
      "org.bedework.print.jar.licence.info";

  private static final String srcRepo = "http://dev.bedework.org/downloads/lib";

  private List<Task> children = new ArrayList<Task>();

  private String groupId;

  private String artifactId;

  private String version;

  private String type;

  private String scope;

  private boolean optional;

  @Override
  public void addTask(final Task task) {
    children.add(task);
  }

  public void addConfiguredGroupId(final GroupIdTask t) {
    if (t.getText() != null) {
      groupId = t.getText();
    }
  }

  public void addConfiguredArtifactId(final ArtifactIdTask t) {
    if (t.getText() != null) {
      artifactId = t.getText();
    }
  }

  public void addConfiguredVersion(final VersionTask t) {
    if (t.getText() != null) {
      version = replaced(t.getText());
    }
  }

  public void addConfiguredType(final TypeTask t) {
    if (t.getText() != null) {
      type = t.getText();
    }
  }

  public void addConfiguredScope(final ScopeTask t) {
    if (t.getText() != null) {
      scope = t.getText();
    }
  }

  public void addConfiguredOptional(final OptionalTask t) {
    if (t.getText() != null) {
      optional = Boolean.valueOf(t.getText());
    }
  }

  /** Execute the task
   */
  @Override
  public void execute() throws BuildException {
    if (getProperty(printLicenceInfo) != null) {
      outputLicenceInfo();
    }

    log("Dependency info: " +
        groupId + " :" +
        artifactId + " :" +
        version + " :" +
        type + " :" +
        scope + " :" +
        optional + " :", Project.MSG_INFO);

    try {
      if ((type != null) && !type.equals("jar")) {
        return;
      }

      /* Fetch it if we haven't got it already */

      String libDir = replaced("${lib.dir}");
      mkDir(libDir);

      String jarName = getFileName();

      if (groupId.startsWith("org.bedework.")) {
        // A local project - look in the dist directory for the file.

        String projectName = groupId.substring("org.bedework.".length());
        StringBuilder jarPath =  new StringBuilder("..");
        jarPath.append(File.separatorChar);
        jarPath.append(projectName);
        jarPath.append(File.separatorChar);
        jarPath.append("dist");
        jarPath.append(File.separatorChar);
        jarPath.append(jarName);

        File jarFile = new File(jarPath.toString());

        if (jarFile.exists()) {
          // Copy into our lib
          copyFile(libDir, jarFile);
          return;
        }
      }

      String libCacheDir = replaced("${org.bedework.libcache.dir}");

      File cachedFile = new File(libCacheDir +
                                 File.separatorChar + jarName);

      if ((!"yes".equals(replaced("${org.bedework.offline.build}")))) {
        /* Refresh the cache if needed */
        Get getTask = new Get();

        /*
         *      <get src="@{src}/${org.bedework.getjar.jarname}"
                     dest="@{libcache}/${org.bedework.getjar.jarname}"
                     ignoreerrors="true"
                     verbose="${org.bedework.getjar.noisy}"
                     usetimestamp="true"/>

         */

        getTask.setSrc(new URL(srcRepo + "/" + jarName));
        getTask.setDest(cachedFile);
        getTask.setIgnoreErrors(true);
        getTask.setVerbose(Boolean.valueOf(replaced("${org.bedework.getjar.noisy}")));
        getTask.setUseTimestamp(true);

        getTask.doGet(Project.MSG_DEBUG, null);
      }

      /* Is it in our cache? */

      if (!cachedFile.exists()) {
        // Tough
        log("******************************************************",
            Project.MSG_ERR);
        log("File " + jarName + " is not available",
            Project.MSG_ERR);
        log("******************************************************",
            Project.MSG_ERR);
        return;
      }

      copyFile(libDir, cachedFile);
    } catch (BuildException be) {
      throw be;
    } catch (Throwable t) {
      t.printStackTrace();
      throw new BuildException(t);
    }
  }

  protected void copyFile(final String toDir, final File fromFile) throws BuildException {
    try {
      FileUtils fu = FileUtils.getFileUtils();

      String to = toDir + File.separatorChar + fromFile.getName();

      fu.copyFile(fromFile.getAbsolutePath(), to);
    } catch (BuildException be) {
      throw be;
    } catch (Throwable t) {
      t.printStackTrace();
      throw new BuildException(t);
    }
  }

  public String getGroupId() {
    return groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getVersion() {
    return version;
  }

  public String getType() {
    return type;
  }

  public String getScope() {
    return scope;
  }

  public boolean getOptional() {
    return optional;
  }

  public String getSymbolicName() {
    /* The BND maven plugin behaves as follows:
     * Get the symbolic name as groupId + "." + artifactId, with the following exceptions:

      if artifact.getFile is not null and the jar contains a OSGi Manifest with Bundle-SymbolicName property then that value is returned
      if groupId has only one section (no dots) and artifact.getFile is not null then the first package name with classes is returned. eg. commons-logging:commons-logging -> org.apache.commons.logging
      if artifactId is equal to last section of groupId then groupId is returned. eg. org.apache.maven:maven -> org.apache.maven
      if artifactId starts with last section of groupId that portion is removed. eg. org.apache.maven:maven-core -> org.apache.maven.core
      The computed symbolic name is also stored in the $(maven-symbolicname) property in case you want to add attributes or directives to it.
    */

    return getGroupId() + "." + getArtifactId();
  }

  /** If this dependency represents a file we return its name
   * @return file name
   */
  public String getFileName() {
    StringBuilder sb = new StringBuilder(artifactId);

    if (version != null) {
      sb.append("-");
      sb.append(version);
    }

    if (type != null) {
      sb.append(".");
      sb.append(type);
    } else {
      sb.append(".jar");
    }

    return sb.toString();
  }

  private void outputLicenceInfo() {
    try {
      if (licenseInfo == null) {
        if (!parseLicenseInfo()) {
          System.out.println("Unable to parse license info");
          log("Unable to parse license info", Project.MSG_ERR);
          return;
        }
      }

      mkDir("${bedework.home}/dist");

      File lout = new File(replaced("${bedework.home}/dist/jarlicenses.xml"));
      licenceOs = new FileOutputStream(lout.getAbsolutePath(),
                                       true); // append
      LicenseInfoType lit = licenseInfo.get(artifactId);

      if (lit == null) {
        log("No license info for '" + artifactId + "'", Project.MSG_ERR);
        return;
      }

      outXml("  <jarLicense>");
      if (version != null) {
        outXml("version", version);
      }
      outXml("groupId", lit.groupId);
      outXml("artifactId", lit.artifactId);
      outXml("license", lit.license);
      outXml("from", replaced("${project.name}"));
      outXml("  </jarLicense>");
    } catch (Throwable t) {
      log("Exception: " + t.getMessage(), Project.MSG_ERR);
    }
  }

  private void outXml(final String s) throws BuildException {
    try {
      licenceOs.write(s.getBytes());
      licenceOs.write('\n');
    } catch (Throwable t) {
      log("Exception: " + t.getMessage(), Project.MSG_ERR);
    }
  }

  private void outXml(final String nm, final String val) throws BuildException {
    try {
      licenceOs.write("    <".getBytes());
      licenceOs.write(nm.getBytes());
      licenceOs.write(">".getBytes());
      licenceOs.write(val.getBytes());
      licenceOs.write("</".getBytes());
      licenceOs.write(nm.getBytes());
      licenceOs.write(">\n".getBytes());
    } catch (Throwable t) {
      log("Exception: " + t.getMessage(), Project.MSG_ERR);
    }
  }

  /**
   * create the directory and all parents
   * @throws BuildException if dir is somehow invalid, or creation failed.
   */
  public void mkDir(final String name) throws BuildException {
    File dir = new File(replaced(name));

    if (dir.isFile()) {
      throw new BuildException("Unable to create directory as a file "
          + "already exists with that name: "
          + dir.getAbsolutePath());
    }

    if (!dir.exists()) {
      boolean result = mkdirs(dir);
      if (!result) {
        String msg = "Directory " + dir.getAbsolutePath()
            + " creation was not successful for an unknown reason";
        throw new BuildException(msg, getLocation());
      }
      log("Created dir: " + dir.getAbsolutePath());
    } else {
      log("Skipping " + dir.getAbsolutePath()
          + " because it already exists.", Project.MSG_VERBOSE);
    }
  }

  /**
   * Attempt to fix possible race condition when creating
   * directories on WinXP. If the mkdirs does not work,
   * wait a little and try again.
   */
  private boolean mkdirs(final File f) {
    if (!f.mkdirs()) {
      try {
        Thread.sleep(MKDIR_RETRY_SLEEP_MILLIS);
        return f.mkdirs();
      } catch (InterruptedException ex) {
        return f.mkdirs();
      }
    }
    return true;
  }

  private String getProperty(final String n) {
    return (String)PropertyHelper.getPropertyHelper(getProject()).getProperty(null, n);
  }

  private String replaced(final String s) {
    PropertyHelper props = PropertyHelper.getPropertyHelper(getProject());

    if (s == null) {
      return null;
    }

    return props.replaceProperties(null, s, null);
  }

  protected boolean parseLicenseInfo() throws BuildException {
    try {
      licenseInfo = new HashMap<String, LicenseInfoType>();

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);

      DocumentBuilder builder = factory.newDocumentBuilder();

      Document doc = null;

//      licenseInfoURL = defaultLicenseInfoURL;
      File f = new File(replaced(licenceInfoFilename));
      doc = builder.parse(new FileInputStream(f));

//      doc = builder.parse(licenseInfoURL);

      if (doc == null) {
        log("******* No document ********", Project.MSG_ERR);
        return false;
      }

      for (Element e: getElements(doc.getDocumentElement())) {
        String lname = e.getLocalName();

        //log("Found lname \"" + lname + "\"", Project.MSG_ERR);

        if (lname.equals("artifact")) {
          if (!makeArtifact(e)) {
            log("No artifact", Project.MSG_ERR);
            continue;
          }
        }
      }

      return true;
    } catch (SAXException e) {
      log("SAXException: " + e.getMessage(), Project.MSG_ERR);
      return false;
    } catch (Throwable t) {
      log("Exception: " + t.getMessage(), Project.MSG_ERR);
      return false;
    }
  }

  private boolean makeArtifact(final Element artel) throws Throwable {

    String artifactId = null;
    String groupId = null;
    String license = null;
    String name = null;

    for (Element e: getElements(artel)) {
      String lname = e.getLocalName();
      //log("Found artifact lname \"" + lname + "\"", Project.MSG_ERR);

      if (lname.equals("artifactId")) {
        artifactId = getElementContent(e);
        //log("Found artifactId \"" + artifactId + "\"", Project.MSG_ERR);
        continue;
      }

      if (lname.equals("groupId")) {
        groupId = getElementContent(e);
        continue;
      }

      if (lname.equals("license")) {
        license = getElementContent(e);
        continue;
      }

      if (lname.equals("name")) {
        name = getElementContent(e);
        continue;
      }
    }

    if (artifactId == null) {
      log("No artifact id", Project.MSG_ERR);
      return false;
    }

    //log("Save license info for '" + artifactId + "'", Project.MSG_ERR);

    licenseInfo.put(artifactId, new LicenseInfoType(groupId,
                                                    artifactId,
                                                    license,
                                                    name));

    return true;
  }

  /** All the children must be elements or white space text nodes.
   *
   * @param nd
   * @return Collection   element nodes. Always non-null
   * @throws SAXException
   */
  private Collection<Element> getElements(final Node nd) throws SAXException {
    ArrayList<Element> al = new ArrayList<Element>();

    NodeList children = nd.getChildNodes();

    for (int i = 0; i < children.getLength(); i++) {
      Node curnode = children.item(i);

      if (curnode.getNodeType() == Node.TEXT_NODE) {
        String val = curnode.getNodeValue();

        if (val != null) {
          for (int vi= 0; vi < val.length(); vi++) {
            if (!Character.isWhitespace(val.charAt(vi))) {
              throw new SAXException("Non-whitespace text in element body for " +
                                     nd.getLocalName() +
                                     "\n text=" + val);
            }
          }
        }
      } else if (curnode.getNodeType() == Node.COMMENT_NODE) {
        // Ignore
      } else if (curnode.getNodeType() == Node.ELEMENT_NODE) {
        al.add((Element)curnode);
      } else {
        throw new SAXException("Unexpected child node " + curnode.getLocalName() +
                               " for " + nd.getLocalName());
      }
    }

    return al;
  }

  /** Return the content for the current element. All leading and trailing
   * whitespace and embedded comments will be removed.
   *
   * <p>This is only intended for an element with no child elements.
   *
   * @param el
   * @return element content
   * @throws SAXException
   */
  public static String getElementContent(final Element el) throws SAXException {
    StringBuffer sb = new StringBuffer();

    NodeList children = el.getChildNodes();

    for (int i = 0; i < children.getLength(); i++) {
      Node curnode = children.item(i);

      if (curnode.getNodeType() == Node.TEXT_NODE) {
        sb.append(curnode.getNodeValue());
      } else if (curnode.getNodeType() == Node.CDATA_SECTION_NODE) {
        sb.append(curnode.getNodeValue());
      } else if (curnode.getNodeType() == Node.COMMENT_NODE) {
        // Ignore
      } else {
        throw new SAXException("Unexpected child node " + curnode.getLocalName() +
                               " for " + el.getLocalName());
      }
    }

    return sb.toString().trim();
  }
}
