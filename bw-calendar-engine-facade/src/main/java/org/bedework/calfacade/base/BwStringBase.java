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
package org.bedework.calfacade.base;

import org.bedework.calfacade.annotations.ical.IcalProperty;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.locale.BwLocale;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.QuotaUtil;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.parameter.Language;

import java.util.Collection;

/** A base class for String values in bedework. This allows for i18n etc.
 * It also allows for moving all long strings to one or two tables.
 *
 * RFC 2445 has the following properties with a language param:
 * <pre>
 * ATTENDEE    (cn param)
 * CATEGORIES
 * COMMENT
 * CONTACT
 * DESCRIPTION
 * LOCATION
 * ORGANIZER   (cn param)
 * REQUEST-STATUS
 * RESOURCES
 * SUMMARY
 * TZNAME
 * </pre>
 * x-properties can also take the language param.
 *.
 *  @version 1.0
 */
public class BwStringBase extends BwDbentity<BwStringBase>
    implements SizedEntity {
  private String lang;

  private String value;

  /** Constructor
   */
  public BwStringBase() {
    super();
  }

  /** Create a string by specifying all its fields
   *
   * @param lang        String language code
   * @param value       String value
   */
  public BwStringBase(final String lang,
                      final String value) {
    super();
    this.lang = lang;
    this.value = value;
  }

  /** Set the lang
   *
   * @param val    String lang
   */
  @IcalProperty(pindex = PropertyInfoIndex.LANG)
  public void setLang(final String val) {
    lang = val;
  }

  /** Get the lang
   *
   * @return String   lang
   */
  public String getLang() {
    return lang;
  }

  /** Set the value
   *
   * @param val    String value
   */
  @IcalProperty(pindex = PropertyInfoIndex.VALUE)
  public void setValue(final String val) {
    value = val;
  }

  /** Get the value
   *
   *  @return String   value
   */
  public String getValue() {
    return value;
  }

  /* ====================================================================
   *                        Dump Entity methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.DumpEntity#hasDumpValue()
   */
  @Override
  public boolean hasDumpValue() throws CalFacadeException {
    return (getValue() != null) && (getValue().length() > 0);
  }

  /* ====================================================================
   *                        Convenience methods
   * ==================================================================== */

  /** Search the collection for a string that matches the given language code.
   *<p>A supplied lang of null implies the default language code.
   *
   * <p>If the supplied language equals any language in the collection we return
   * with that.
   *
   * <p>Otherwise if if matches the first part of a qualified code we take that,
   * e.g lan="en" could match "en_US"
   *
   * <p>Otherwise we return the first one we found.
   *
   * @param lang language code
   * @param c collection of strings
   * @return BwString or null if no strings.
   */
  protected static BwStringBase findLanguage(final String lang,
                                             final Collection<? extends BwStringBase> c) {
    if (c == null) {
      return null;
    }

    BwStringBase matched = null;
    BwStringBase def = null;
    int len = 0;
    if (lang != null) {
      len = lang.length();
    }

    for (final BwStringBase s: c) {
      if (def == null) {
        // Make sure we get something
        def = s;
      }

      final String slang = s.getLang();

      if (lang == null) {
        if ((slang == null) ||
            (CalFacadeUtil.cmpObjval(BwLocale.getLang(), slang) == 0)) {
          return s;
        }
      } else {
        if (CalFacadeUtil.cmpObjval(lang, slang) == 0) {
          return s;
        }

        if ((matched == null) &&
            (len > 0) &&
            ((len < slang.length()) && slang.startsWith(lang))) {
          matched = s;
          def = s;
        }
      }
    }

    return def;
  }

  /** Figure out what's different and update it. This should reduce the number
   * of spurious changes to the db.
   *
   * @param from the before value
   * @return true if we changed something.
   */
  public boolean update(final BwStringBase from) {
    boolean changed = false;

    if (CalFacadeUtil.cmpObjval(getLang(), from.getLang()) != 0) {
      setLang(from.getLang());
      changed = true;
    }

    if (CalFacadeUtil.cmpObjval(getValue(), from.getValue()) != 0) {
      setValue(from.getValue());
      changed = true;
    }

    return changed;
  }

  /** If there is a language attached return as a parameter else return null
   *
   * @return Language or null
   */
  public Parameter getLangPar() {
    if (getLang() == null) {
      return null;
    }

    return new Language(getLang());
  }

  /** Check this is properly trimmed
   *
   * @return boolean true if changed
   */
  public boolean checkNulls() {
    boolean changed = false;

    String str = Util.checkNull(getLang());
    if (CalFacadeUtil.compareStrings(str, getLang()) != 0) {
      setLang(str);
      changed = true;
    }

    str = Util.checkNull(getValue());
    if (CalFacadeUtil.compareStrings(str, getValue()) != 0) {
      setValue(str);
      changed = true;
    }

    return changed;
  }

  /** Size to use for quotas.
   *
   * @return int
   */
  @Override
  @JsonIgnore
  public int getSize() {
    return super.length() +
           QuotaUtil.size(getLang()) +
           QuotaUtil.size(getValue());
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  @Override
  public int compareTo(final BwStringBase that) {
    if (that == this) {
      return 0;
    }

    if (that == null) {
      return -1;
    }

    final int res = CalFacadeUtil.cmpObjval(getLang(), that.getLang());

    if (res != 0) {
      return res;
    }

    return CalFacadeUtil.cmpObjval(getValue(), that.getValue());
  }

  @Override
  public int hashCode() {
    int hc = 7;

    if (getLang() != null) {
      hc *= getLang().hashCode();
    }

    if (getValue() != null) {
      hc *= getValue().hashCode();
    }

    return hc;
  }

  @Override
  protected void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);
    ts.append("lang", getLang())
      .append("value", getValue());
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }

  @Override
  public Object clone() {
    return new BwStringBase(getLang(),
                        getValue());
  }
}
