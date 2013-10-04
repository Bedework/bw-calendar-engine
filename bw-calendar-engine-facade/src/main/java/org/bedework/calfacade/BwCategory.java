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
import org.bedework.calfacade.base.CollatableEntity;
import org.bedework.calfacade.base.SizedEntity;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.QuotaUtil;
import org.bedework.util.misc.ToString;

import java.util.Comparator;

/** A category in Bedework. This value object does no consistency or validity
 * checking
 *.
 *  @version 1.0
 */
@Dump(elementName="category", keyFields={"uid"})
public class BwCategory extends BwEventProperty<BwCategory>
        implements CollatableEntity, Comparator<BwCategory>,
                   SizedEntity {
  private BwString word;
  private BwString description;

  /* Not persisted in the db */

  private String name;

  /** Constructor
   */
  public BwCategory() {
    super();
  }

  /** Set the word
   *
   * @param val    BwString word
   */
  public void setWord(final BwString val) {
    word = val;
  }

  /** Get the word
   *
   * @return BwString   word
   */
  public BwString getWord() {
    return word;
  }

  /** Delete the category's keyword - this must be called rather than setting
   * the value to null.
   *
   */
  public void deleteWord() {
    addDeletedEntity(getWord());
    setWord(null);
  }

  /** Set the category's description
   *
   * @param val    BwString category's description
   */
  public void setDescription(final BwString val) {
    description = val;
  }

  /** Get the category's description
   *
   *  @return BwString   category's description
   */
  public BwString getDescription() {
    return description;
  }

  /**
   * @return category with uid filled in.
   */
  public static BwCategory makeCategory() {
    return (BwCategory)new BwCategory().initUid();
  }

  /** Delete the category's description - this must be called rather than setting
   * the value to null.
   *
   */
  public void deleteDescription() {
    addDeletedEntity(getDescription());
    setDescription(null);
  }

  /** Set the category's name (indexer)
   *
   * @param val    String name
   */
  public void setName(final String val) {
    name = val;
  }

  /** Get the category's name
   *
   *  @return name
   */
  public String getName() {
    return name;
  }

  /* ====================================================================
   *                   EventProperty methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwEventProperty#getFinderKeyValue()
   */
  @Override
  @NoDump
  public BwString getFinderKeyValue() {
    return getWord();
  }

  /* ====================================================================
   *                   CollatableEntity methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.CollatableEntity#getCollateValue()
   */
  @Override
  @NoDump
  public String getCollateValue() {
    return getWord().getValue();
  }

  /* ====================================================================
   *                   Action methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.BwDbentity#afterDeletion()
   */
  @Override
  public void afterDeletion() {
    addDeletedEntity(getWord());
    addDeletedEntity(getDescription());
  }

  /* ====================================================================
   *                        Convenience methods
   * ==================================================================== */

  /** Size to use for quotas.
   *
   * @return int
   */
  @Override
  @NoDump
  public int getSize() {
    return super.length() +
           QuotaUtil.size(getWord()) +
           QuotaUtil.size(getDescription());
  }

  /**
   * @param val
   */
  public void setWordVal(final String val) {
    BwString s = getWord();
    if (val == null) {
      if (s != null) {
        addDeletedEntity(s);
        setWord(null);
      }
      return;
    }

    if (s == null) {
      s = new BwString();
      setWord(s);
    }
    s.setValue(val);
  }

  /**
   * @return String
   */
  @NoDump
  public String getWordVal() {
    BwString s = getWord();
    if (s == null) {
      return null;
    }

    return s.getValue();
  }

  /**
   * @param val
   */
  public void setDescriptionVal(final String val) {
    BwString s = getDescription();
    if (val == null) {
      if (s != null) {
        addDeletedEntity(s);
        setDescription(null);
      }
      return;
    }

    if (s == null) {
      s = new BwString();
      setWord(s);
    }
    s.setValue(val);
  }

  /**
   * @return String
   */
  @NoDump
  public String getDescriptionVal() {
    BwString s = getDescription();
    if (s == null) {
      return null;
    }

    return s.getValue();
  }

  /** Size to use for quotas.
   *
   * @return int
   */
  @Override
  public int length() {
    return super.length() +
           getWord().length() +
           getDescription().length();
  }

  public boolean updateFrom(final BwCategory cat) {
    boolean changed = false;

    if (!getWord().equals(cat.getWord())) {
      setWord(cat.getWord());
      changed = true;
    }

    if (!getDescription().equals(cat.getDescription())) {
      setDescription(cat.getDescription());
      changed = true;
    }

    return changed;
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  @Override
  public int compare(final BwCategory o1, final BwCategory o2) {
    return o1.compareTo(o2);
  }

  @Override
  public int compareTo(final BwCategory that) {
    if (that == null) {
      return -1;
    }

    return CalFacadeUtil.cmpObjval(getUid(), that.getUid());
  }

  @Override
  public int hashCode() {
    return getUid().hashCode();
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);
    ts.append("word", getWord());

    return ts.toString();
  }

  @Override
  public Object clone() {
    BwCategory cat = new BwCategory();

    super.copyTo(cat);

    cat.setWord((BwString)getWord().clone());

    if (getDescription() != null) {
      cat.setDescription((BwString)getDescription().clone());
    }

    return cat;
  }
}
