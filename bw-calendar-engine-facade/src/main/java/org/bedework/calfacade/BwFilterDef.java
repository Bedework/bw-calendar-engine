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

import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.DescriptionEntity;
import org.bedework.calfacade.base.DisplayNameEntity;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/**
 * A filter selects events (and possibly other entities) that fulfill
 * certain criteria.  For example, "All events that have the category
 * 'Lecture'".
 *
 * <p>All filters must be expressible as a db search expresssion. Entity
 * filters select events that own a given entity or own an entity within a
 * set. This translates to <br/>
 *    event.location = given-location or <br/>
 *    event.location in given-location-set <br/>
 *
 * <p>The test may be negated to give != and not in.
 *
 * <p>Some filters can have any number of children such as an OrFilter.
 *
 * @author Mike Douglass
 * @version 1.1
 */
@Dump(elementName="filter", keyFields={"owner", "name"})
public class BwFilterDef extends BwShareableContainedDbentity<BwFilterDef>
        implements DescriptionEntity<BwLongString>,
        DisplayNameEntity, Comparator<BwFilterDef> {
  /** The internal name of the filter
   */
  private String name;

  /** */
  public static final int maxNameLength = 100;

  /** The display name(s) of the filter
   */
  private Collection<BwString> displayNames;

  /** The definition
   */
  private String definition;

  /** Some sort of description - may be null
   */
  private Set<BwLongString> descriptions;

  /* This field is not persisted */
  private FilterBase filters;

  /* ====================================================================
   *                   Bean methods
   * ==================================================================== */

  /** Set the name
   *
   * @param val    String name
   */
  public void setName(final String val) {
    name = val;
  }

  /** Get the name
   *
   * @return String   name
   */
  public String getName() {
    return name;
  }

  /** Set the filters represented by the definition
   *
   * @param val   BwFilter object
   */
  public void setFilters(final FilterBase val) {
    filters = val;
  }

  /** Get the filters represented by the definition
   *
   * @return BwFilter    the filters
   */
  @NoDump
  public FilterBase getFilters() {
    return filters;
  }

  /** Set the definition
   *
   * @param val    String definition
   */
  public void setDefinition(final String val) {
    definition = val;
  }

  /** Get the definition
   *
   * @return String   definition
   */
  public String getDefinition() {
    return definition;
  }

  /* ====================================================================
   *               DisplayNameEntity interface methods
   * ==================================================================== */

  @Override
  public void setDisplayNames(final Collection<BwString> val) {
    displayNames = val;
  }

  @Override
  @Dump(collectionElementName = "displayName")
  public Collection<BwString> getDisplayNames() {
    return displayNames;
  }

  @Override
  @NoDump
  public int getNumDisplayNames() {
    final Collection<BwString> rs = getDisplayNames();
    if (rs == null) {
      return 0;
    }

    return rs.size();
  }

  @Override
  public void addDisplayName(final String lang, final String val) {
    addDisplayName(new BwString(lang, val));
  }

  @Override
  public void addDisplayName(final BwString val) {
    Collection<BwString> rs = getDisplayNames();
    if (rs == null) {
      rs = new TreeSet<>();
      setDisplayNames(rs);
    }

    if (!rs.contains(val)) {
      rs.add(val);
    }
  }

  @Override
  public boolean removeDisplayName(final BwString val) {
    final Collection<BwString> rs = getDisplayNames();
    if (rs == null) {
      return false;
    }

    return rs.remove(val);
  }

  @Override
  public void updateDisplayNames(final String lang, final String val) {
    final BwString s = findDisplayName(lang);
    if (val == null) {
      // Removing
      if (s!= null) {
        removeDisplayName(s);
      }
    } else if (s == null) {
      addDisplayName(lang, val);
    } else if ((CalFacadeUtil.cmpObjval(val, s.getValue()) != 0)) {
      // XXX Cannot change value in case this is an override collection.

      //s.setValue(val);
      removeDisplayName(s);
      addDisplayName(lang, val);
    }
  }

  @Override
  public BwString findDisplayName(final String lang) {
    return BwString.findLang(lang, getDisplayNames());
  }

  @Override
  public void setDisplayName(final String val) {
    updateDisplayNames(null, val);
  }

  @Override
  @NoDump
  public String getDisplayName() {
    final BwString s = findDisplayName(null);
    if (s == null) {
      return null;
    }
    return s.getValue();
  }

  /* ====================================================================
   *               DescriptionEntity interface methods
   * ==================================================================== */

  @Override
  public void setDescriptions(final Set<BwLongString> val) {
    descriptions = val;
  }

  @Override
  @Dump(collectionElementName = "description")
  public Set<BwLongString> getDescriptions() {
    return descriptions;
  }

  @Override
  @NoDump
  public int getNumDescriptions() {
    final Collection<BwLongString> rs = getDescriptions();
    if (rs == null) {
      return 0;
    }

    return rs.size();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.DescriptionEntity#addDescription(java.lang.String, java.lang.String)
   */
  @Override
  public void addDescription(final String lang, final String val) {
    addDescription(new BwLongString(lang, val));
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.DescriptionEntity#addDescription(org.bedework.calfacade.BwString)
   */
  @Override
  public void addDescription(final BwLongString val) {
    Set<BwLongString> rs = getDescriptions();
    if (rs == null) {
      rs = new TreeSet<>();
      setDescriptions(rs);
    }

    rs.add(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.DescriptionEntity#removeDescription(org.bedework.calfacade.BwString)
   */
  @Override
  public boolean removeDescription(final BwLongString val) {
    final Collection<BwLongString> rs = getDescriptions();
    if (rs == null) {
      return false;
    }

    return rs.remove(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.DescriptionEntity#updateDescriptions(java.lang.String, java.lang.String)
   */
  @Override
  public void updateDescriptions(final String lang, final String val) {
    final BwLongString s = findDescription(lang);
    if (val == null) {
      // Removing
      if (s!= null) {
        removeDescription(s);
      }
    } else if (s == null) {
      addDescription(lang, val);
    } else if ((CalFacadeUtil.cmpObjval(val, s.getValue()) != 0)) {
      // XXX Cannot change value in case this is an override collection.

      //s.setValue(val);
      removeDescription(s);
      addDescription(lang, val);
    }
  }

  @Override
  public BwLongString findDescription(final String lang) {
    return BwLongString.findLang(lang, getDescriptions());
  }

  @Override
  public void setDescription(final String val) {
    updateDescriptions(null, val);
  }

  @Override
  @NoDump
  public String getDescription() {
    final BwLongString s = findDescription(null);
    if (s == null) {
      return null;
    }
    return s.getValue();
  }

  /* ====================================================================
   *                   FixNamesEntity methods
   * ==================================================================== */

  void fixNames() {
    setColPath("filters", null);

    setHref(Util.buildPath(false, getColPath(), getName()));
  }

  @Override
  public String getColPath(){
    if (super.getColPath() == null) {
      fixNames();
    }
    return super.getColPath();
  }

  @Override
  public String getHref(){
    if (super.getHref() == null) {
      fixNames();
    }
    return super.getHref();
  }

  /** Add our stuff to the StringBuilder
   *
   * @param ts    StringBuilder for result
   */
  @Override
  protected void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);
    ts.newLine();
    ts.append("name", getName());
    ts.append("description", getDescription());
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int compare(final BwFilterDef f1, final BwFilterDef f2) {
    return f1.compareTo(f2);
  }

  @Override
  public int compareTo(final BwFilterDef that) {
    final int cmp = CalFacadeUtil.cmpObjval(getOwnerHref(),
                                            that.getOwnerHref());
    if (cmp != 0) {
      return cmp;
    }

    return CalFacadeUtil.cmpObjval(getName(), that.getName());
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }
}
