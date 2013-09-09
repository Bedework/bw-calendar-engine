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
import org.apache.tools.ant.TaskContainer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Ant task to define a set of dependencies. Allows a fairly seamless migration towards
 * maven.
 *
 * @author douglm @ bedework.edu
 */
public class DependenciesTask extends Task implements TaskContainer {
  private List<Task> children = new ArrayList<Task>();

  private List<DependencyTask> dependencies = new ArrayList<DependencyTask>();

  @Override
  public void addTask(final Task task) {
    children.add(task);
  }

  public void addConfiguredDependency(final DependencyTask t) {
    children.add(t);
    dependencies.add(t);
  }

  /** Execute the task
   */
  @Override
  public void execute() throws BuildException {
    for (Iterator i = children.iterator(); i.hasNext();) {
      Task nestedTask = (Task) i.next();
      nestedTask.perform();
    }

    if (dependencies.isEmpty()) {
      return;
    }

    StringBuilder sb = new StringBuilder();

    for (DependencyTask d: dependencies) {
      String scope = d.getScope();

      if ((scope == null) || // same as "compile"
          "compile".equals(scope) ||
          "runtime".equals(scope)) {
        if (sb.length() > 0) {
          sb.append(",");
        }

        String groupId = d.getGroupId();

        if (groupId.equals("javax") ||
            groupId.equals("java") ||
            groupId.startsWith("javax.") ||
            groupId.startsWith("java.")) {
          continue;
        }

        sb.append(d.getSymbolicName());

        if (d.getVersion() != null) {
          sb.append(";version=");
          sb.append(d.getVersion());
        }

        if (d.getOptional()) {
          sb.append(";resolution:=optional");
        }
      }
    }

    if (sb.length() == 0) {
      return;
    }

    makeProp("org.bedework.project.dependencies", sb.toString());
  }

  protected String getProperty(final String n) {
    return (String)PropertyHelper.getPropertyHelper(getProject()).getProperty(null, n);
  }

  protected String replaced(final String s) {
    PropertyHelper props = PropertyHelper.getPropertyHelper(getProject());

    if (s == null) {
      return null;
    }

    return props.replaceProperties(null, s, null);
  }

  protected void makeProp(final String name, final String value) throws BuildException {
    PropertyHelper props = PropertyHelper.getPropertyHelper(getProject());

    if (value == null) {
      props.setProperty(null, name, "", false);
      return;
    }

    props.setProperty(null, name, value, false);
  }
}
