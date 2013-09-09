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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

/** Ant task to build the applications based on a list of names
 *
 * <p>Task attributes are <ul>
 * <li>names            Comma separated list of application names</li>
 * <li>prefix           Property name prefix for generated properties</li>
 * <li>appPrefix        Property name prefix for applications</li>
 * <li>projectPrefix    Property name prefix for project properties</li>
 * </ul>
 *
 * <p>Prefixes will be automatically appended with "." if needed.
 *
 * <p>Generated properties are all prefixed by the prefix attribute and are:<ul>
 * <li>name             name of the application - from the list of names</li>
 * <li>projectName      Name of the project - from the "X.project" property</li>
 * <li>project.path     Path to project - value of the property projectPrefix + projectName</li>
 * <li>app.sou          Path to source for application - from the "X.sou.dir" property</li>
 * </ul>
 *
 * <p>
 *   if appPrefix="org.bedework.app" and project name is myproject we expect a
 *   bunch of properties of the form "org.bedework.app.myapp.xxx".
 * </p>
 *
 * <p>
 *   If projectPrefix="org.bedework.project" and org.bedework.app.myapp.project=myproject
 *   we expect a property with the name "org.bedework.project.myproject" which
 *   provides the location of the project. This allows us to locate internal
 *   project resources.
 * </p>
 *
 * <p>Body is ant
 *
 * @author douglm @ bedework.edu
 */
public class ForEachAppTask extends ForAppTask {
  private String names;

  /** Set the names
   *
   * @param val   String
   */
  public void setNames(final String val) {
    names = val;
  }

  /** Execute the task
   */
  @Override
  public void execute() throws BuildException {
    try {
      List nameList = getList(names);

      if (nameList.size() == 0) {
        throw new BuildException("Must supply deployment names.");
      }

      Iterator nit = nameList.iterator();

      while (nit.hasNext()) {
        String name = (String)nit.next();

        doProps(name);
      }
    } catch (BuildException be) {
      throw be;
    } catch (Throwable t) {
      t.printStackTrace();
      throw new BuildException(t);
    }
  }

  /* Turn a comma separated list into a List
   */
  private List getList(final String val) throws BuildException {
    List<String> l = new LinkedList<String>();

    if ((val == null) || (val.length() == 0)) {
      return l;
    }

    StringTokenizer st = new StringTokenizer(val, ",", true);
    while (st.hasMoreTokens()) {
      String token = st.nextToken().trim();

      // No empty strings

      if (token.equals("") || token.equals(",")) {
        throw new BuildException("List has an empty element.");
      }

      l.add(token);

      // List must not end with ,
      if (st.hasMoreTokens()) {
        token = st.nextToken();
        if (!st.hasMoreTokens() || !token.equals(",")) {
          throw new BuildException("List ends with ','");
        }
      }
    }

    return l;
  }
}
