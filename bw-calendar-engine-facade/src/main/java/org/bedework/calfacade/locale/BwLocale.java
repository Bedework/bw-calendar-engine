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
package org.bedework.calfacade.locale;

import org.bedework.calfacade.exc.CalFacadeException;

import java.util.Collection;
import java.util.Locale;

/** Make the current locale for bedework available.
 *
 * @author Mike Douglass
 */
public class BwLocale {
  private static ThreadLocal<BwLocale> threadCb =
    new ThreadLocal<BwLocale>();

  private Locale currentLocale;

  /**
   * @param val
   */
  public static void setLocale(final Locale val) {
    getLocales().setCurrentLocale(val);
  }

  /** Will return value as set or jvm default
   *
   * @return Locale
   */
  public static Locale getLocale() {
    return getLocales().getCurrentLocale();
  }

  /**
   * @param val
   */
  public void setCurrentLocale(final Locale val) {
    currentLocale = val;
  }

  /** Will return value as set or jvm default
   *
   * @return Locale
   */
  public Locale getCurrentLocale() {
    if (currentLocale == null) {
      return Locale.getDefault();
    }

    return currentLocale;
  }

  /**
   * @return BwLocale object
   */
  public static BwLocale getLocales() {
    BwLocale loc = threadCb.get();

    if (loc == null) {
      loc = new BwLocale();
      threadCb.set(loc);
    }

    return loc;
  }

  /** See if we can find a locale in the list that matches the given locale.
   * We return either an exact match or the first match where the languages are
   * equal.
   *
   * @param locales
   * @param locale
   * @return Locale
   */
  public static Locale matchLocales(final Collection<Locale> locales,
                              final Locale locale) {
    Locale possible = null;

    for (Locale l: locales) {
      if (l.equals(locale)) {
        return l;
      }

      /* Try language - note locale strings are interned so '==' works here */

      if ((possible == null) && (l.getLanguage() == locale.getLanguage())) {
        possible = l;
      }
    }

    return possible;
  }

  /** make a locale from the standard underscore separated parts - no idea why
   * this isn't in Locale
   *
   * @param val
   * @return a Locale
   * @throws CalFacadeException
   */
  public static Locale makeLocale(final String val) throws CalFacadeException {
    String lang = null;
    String country = ""; // NOT null for Locale
    String variant = "";

    try {
      if (val == null) {
        throw new CalFacadeException(CalFacadeException.badLocale, "NULL");
      }

      if (val.length() == 2) {
        lang = val;
      } else {
        int pos = val.indexOf('_');
        if (pos != 2) {
          throw new CalFacadeException(CalFacadeException.badLocale, val);
        }

        lang = val.substring(0, 2);
        pos = val.indexOf("_", 3);
        if (pos < 0) {
          if (val.length() != 5) {
            throw new CalFacadeException(CalFacadeException.badLocale, val);
          }

          country = val.substring(3);
        } else {
          country = val.substring(3, 5);

          if (val.length() > 6) {
            variant = val.substring(6);
          }
        }
      }

      return new Locale(lang, country, variant);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(CalFacadeException.badLocale, val);
    }
  }

  /** Get th elanguage from the current locale
   *
   * @return Language String
   */
  public static String getLang() {
    Locale loc = getLocales().getCurrentLocale();

    String l = loc.getLanguage();
    if (l == null) {
      l = Locale.ENGLISH.getLanguage();
    }

    return l;
  }

}
