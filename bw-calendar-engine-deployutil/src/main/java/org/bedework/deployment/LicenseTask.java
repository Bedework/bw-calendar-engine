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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Ant task to return jar license information
 *
 * <p>Task attributes are <ul>
 * <li>name             The jar name</li>
 * <li>version          Optional version string</li>
 * <li>prefix           Property name prefix for resulting properties
 *                      Default is "org.bedework.licenseinfo."</li>
 * <li>licenseInfo      URL for license info file.
 *                      Default is "https://source.jasig.org/licenses/license-mappings.xml"</li>
 * </ul>
 *
 * <p>Prefix will be automatically appended with "." if needed.
 *
 * <p>Generated properties are all prefixed by the prefix attribute and are:<ul>
 * <li>name             supplied name or value of name element</li>
 * <li>version          version if supplied</li>
 * <li>groupId          From license info</li>
 * <li>artifactId       From license info</li>
 * <li>license          From license info</li>
 * </ul>
 *
 * <p>Body is ant
 *
 * @author douglm @ bedework.edu
 */
public class LicenseTask extends Task {
  private static final String defaultLicenseInfoURL =
                    "https://source.jasig.org/licenses/license-mappings.xml";

  private String licenseInfoURL;

  private String licenseInfoFile;

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

  private static Map<String, LicenseInfoType> licenseInfo = null;

  private String name;

  private String version;

  private static final String defaultPrefix = "org.bedework.licenseinfo.";

  private String prefix;

  /** Set the names
   *
   * @param val   String
   */
  public void setName(final String val) {
    name = val;
  }

  /** Set the jar version
   *
   * @param val   String
   */
  public void setVersion(final String val) {
    version = val;
  }

  /** Set the generated property prefix
   *
   * @param val   String
   */
  public void setPrefix(final String val) {
    prefix = val;
  }

  /** Set the license info URL
   *
   * @param val   String
   */
  public void setlicenseInfoUrl(final String val) {
    licenseInfoURL = val;
  }

  /** Set the license info File
   *
   * @param val   String
   */
  public void setlicenseInfoFile(final String val) {
    licenseInfoFile = val;
  }

  /** Execute the task
   */
  @Override
  public void execute() throws BuildException {
    try {
      if (name == null) {
        throw new BuildException("Must supply jar name.");
      }

      if (prefix == null) {
        prefix = defaultPrefix;
      }

      if (!prefix.endsWith(".")) {
        prefix += ".";
      }

      if (licenseInfo == null) {
        if (!parseLicenseInfo()) {
          System.out.println("Unable to parse license info");
          log("Unable to parse license info", Project.MSG_ERR);
          return;
        }
      }

      //log("licenseInfo " + licenseInfo, Project.MSG_ERR);

      LicenseInfoType lit = licenseInfo.get(name);

      if (lit == null) {
        log("No license info for " + name, Project.MSG_ERR);
        makeProp("name", name);
        makeProp("version", version);
        makeProp("groupId", null);
        makeProp("artifactId", null);
        makeProp("license", null);
        return;
      }

      String nameVal = lit.name;
      if (nameVal == null) {
        nameVal = name;
      }

      makeProp("name", nameVal);
      makeProp("version", version);
      makeProp("groupId", lit.groupId);
      makeProp("artifactId", lit.artifactId);
      makeProp("license", lit.license);
    } catch (BuildException be) {
      throw be;
    } catch (Throwable t) {
      t.printStackTrace();
      throw new BuildException(t);
    }
  }

  protected boolean parseLicenseInfo() throws BuildException {
    try {
      licenseInfo = new HashMap<String, LicenseInfoType>();

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);

      DocumentBuilder builder = factory.newDocumentBuilder();

      Document doc = null;

      if (licenseInfoFile != null) {
        doc = builder.parse(new FileInputStream(licenseInfoFile));
      } else if (licenseInfoURL == null) {
        licenseInfoURL = defaultLicenseInfoURL;
        doc = builder.parse(licenseInfoURL);
      }

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

    licenseInfo.put(artifactId, new LicenseInfoType(groupId,
                                                    artifactId,
                                                    license,
                                                    name));

    return true;
  }

  protected void makeProp(final String name, final String value) throws BuildException {
    PropertyHelper props = PropertyHelper.getPropertyHelper(getProject());

    if (value == null) {
      props.setProperty(null, prefix + name, "", false);
      return;
    }

    props.setProperty(null, prefix + name, value, false);
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
