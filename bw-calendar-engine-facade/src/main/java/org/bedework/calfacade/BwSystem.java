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
package org.bedework.calfacade;

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.PropertiesEntity;
import org.bedework.util.misc.ToString;
import org.bedework.util.xml.tagdefs.NamespaceAbbrevs;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.namespace.QName;

/** System settings for an instance of bedework as represented by a single
 * database. These settings may be changed by the super user but most should
 * not be changed after system initialisation.
 *
 * @author Mike Douglass       douglm@bedework.edu
 */
@Dump(elementName="system")
@NoDump({"byteSize"})
public class BwSystem extends BwDbentity<BwSystem>
    implements PropertiesEntity, Comparator<BwSystem> {
  /* A name for the system */
  private String name;

  static final String bedeworkContextsPname = "bedework:contexts";

  private Set<BwProperty> properties;

  /** Set the system's name
   *
   * @param val    String system name
   */
  public void setName(final String val) {
    name = val;
  }

  /** Get the system's name.
   *
   * @return String   system's name
   */
  public String getName() {
    return name;
  }

  /* ====================================================================
   *                   Property methods
   * ==================================================================== */

  @Override
  public void setProperties(final Set<BwProperty> val) {
    properties = val;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.PropertiesEntity#getProperties()
   */
  @Override
  @Dump(collectionElementName = "property", compound = true)
  public Set<BwProperty> getProperties() {
    return properties;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.PropertiesEntity#getProperties(java.lang.String)
   */
  @Override
  public Set<BwProperty> getProperties(final String name) {
    TreeSet<BwProperty> ps = new TreeSet<BwProperty>();

    if (getNumProperties() == 0) {
      return null;
    }

    for (BwProperty p: getProperties()) {
      if (p.getName().equals(name)) {
        ps.add(p);
      }
    }

    return ps;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.PropertiesEntity#removeProperties(java.lang.String)
   */
  @Override
  public void removeProperties(final String name) {
    Set<BwProperty> ps = getProperties(name);

    if (ps == null) {
      return;
    }

    for (BwProperty p: ps) {
      removeProperty(p);
    }
  }

  @Override
  @NoDump
  public int getNumProperties() {
    Collection<BwProperty> c = getProperties();
    if (c == null) {
      return 0;
    }

    return c.size();
  }

  @Override
  public BwProperty findProperty(final String name) {
    Collection<BwProperty> props = getProperties();

    if (props == null) {
      return null;
    }

    for (BwProperty prop: props) {
      if (name.equals(prop.getName())) {
        return prop;
      }
    }

    return null;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.PropertiesEntity#addProperty(org.bedework.calfacade.BwProperty)
   */
  @Override
  public void addProperty(final BwProperty val) {
    Set<BwProperty> c = getProperties();
    if (c == null) {
      c = new TreeSet<BwProperty>();
      setProperties(c);
    }

    if (!c.contains(val)) {
      c.add(val);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.PropertiesEntity#removeProperty(org.bedework.calfacade.BwProperty)
   */
  @Override
  public boolean removeProperty(final BwProperty val) {
    Set<BwProperty> c = getProperties();
    if (c == null) {
      return false;
    }

    return c.remove(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.PropertiesEntity#copyProperties()
   */
  @Override
  public Set<BwProperty> copyProperties() {
    if (getNumProperties() == 0) {
      return null;
    }
    TreeSet<BwProperty> ts = new TreeSet<BwProperty>();

    for (BwProperty p: getProperties()) {
      ts.add(p);
    }

    return ts;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.PropertiesEntity#cloneProperties()
   */
  @Override
  public Set<BwProperty> cloneProperties() {
    if (getNumProperties() == 0) {
      return null;
    }
    TreeSet<BwProperty> ts = new TreeSet<BwProperty>();

    for (BwProperty p: getProperties()) {
      ts.add((BwProperty)p.clone());
    }

    return ts;
  }

  /* ====================================================================
   *                   Property convenience methods
   * ==================================================================== */

  /** Set the single valued named property
   *
   * @param name
   * @param val
   */
  public void setProperty(final String name, final String val) {
    BwProperty prop = findProperty(name);

    if (prop == null) {
      prop = new BwProperty(name, val);
      addProperty(prop);
    } else {
      prop.setValue(val);
    }
  }

  /** Get the single valued named property
   *
   * @param name
   * @return String calendar color
   */
  public String getProperty(final String name) {
    BwProperty prop = findProperty(name);

    if (prop == null) {
      return null;
    }

    return prop.getValue();
  }

  /**
   * @return set of contexts - empty if none defined
   */
  @NoDump
  public Set<SubContext> getContexts() {
    Set<SubContext> cs = new TreeSet<SubContext>();
    Set<BwProperty> cps = getProperties(bedeworkContextsPname);

    if (cps == null) {
      return cs;
    }

    for (BwProperty cp: cps) {
      cs.add(new SubContext(cp));
    }

    return cs;
  }

  /**
   * @param sc new sub-context
   */
  public void addContext(final SubContext sc) {
    addProperty(sc.getProp());
  }

  /**
   * @param sc sub-context
   */
  public void removeContext(final SubContext sc) {
    removeProperty(sc.getProp());
  }

  /**
   * @param name
   * @return Sub-context matching the given name or null.
   */
  public SubContext findContext(final String name) {
    Set<BwProperty> cps = getProperties(bedeworkContextsPname);

    if (cps == null) {
      return null;
    }

    for (BwProperty cp: cps) {
      if (name.equals(SubContext.extractContextName(cp.getValue()))) {
        return new SubContext(cp);
      }
    }

    return null;
  }

  /* ====================================================================
   *                   More property convenience methods
   * The above should be set like this as it namespaces the properties
   * ==================================================================== */

  /**
   * @param name
   * @param val
   */
  public void setQproperty(final QName name, final String val) {
    setProperty(NamespaceAbbrevs.prefixed(name), val);
  }

  /**
   * @param name
   * @return value or null
   */
  public String getQproperty(final QName name) {
    return getProperty(NamespaceAbbrevs.prefixed(name));
  }

  /* ====================================================================
   *                   db entity methods
   * ==================================================================== */

  /** Set the href
   *
   * @param val    String href
   */
  public void setHref(final String val) {
  }

  public String getHref() {
    return null;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int compare(final BwSystem o1, final BwSystem o2) {
    if (o1 == o2) {
      return 0;
    }

    return o1.getName().compareTo(o2.getName());
  }

  @Override
  public int compareTo(final BwSystem o2) {
    return compare(this, o2);
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);
    ts.newLine();
    ts.append("name", getName());

    ts.append("properties", getProperties());

    return ts.toString();
  }

  @Override
  public Object clone() {
    BwSystem clone = new BwSystem();

    clone.setName(getName());

    for (BwProperty p: getProperties()) {
      clone.addProperty(p);
    }

    return clone;
  }
}
