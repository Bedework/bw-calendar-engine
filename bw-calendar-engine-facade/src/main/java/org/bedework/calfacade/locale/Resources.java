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

import java.io.Serializable;
import java.util.Collection;
import java.util.ResourceBundle;
import java.util.Locale;

import org.apache.log4j.Logger;

/** Object to provide internationalized resources for the calendar suite.
 *
 * @author Mike Douglass   douglm rpi.edu
 */
public class Resources implements Serializable {
  /** */
  public static final String DESCRIPTION = "description";

  /** */
  public static final String EMAIL = "email";

  /** */
  public static final String END = "end";

  /** */
  public static final String PHONENBR = "phoneNbr";

  /** */
  public static final String START = "start";

  /** */
  public static final String SUBADDRESS = "subaddress";

  /** */
  public static final String SUMMARY = "summary";

  /** */
  public static final String URL = "url";

  /* ---------------------- access control resources -------------------- */

  /** */
  private static final String bundleBase = "org.bedework.locale.resources.BwResources";

  private static Collection<Locale> supportedLocales;

  /** Bundle for the default locale */
  private ResourceBundle bundle = ResourceBundle.getBundle(bundleBase);

  /** Constructor
   *
   */
  public Resources() {}

  /**
   * @param loc
   */
  public Resources(Locale loc) {
    //defaultLocale = loc;
    bundle = ResourceBundle.getBundle(bundleBase, loc);
  }

  /** We support a resource bundle which defines string values for a number of
   * locales. This method carries out a one-time grope around the bundle to
   * determine what locales we support.
   *
   * @return Collection<Locale>
   */
  public static Collection<Locale> getSpportedLocales() {

    return null;
  }

  /**
   * @param key
   * @return value for key
   */
  public String getString(String key) {
    try {
      return bundle.getString(key);
    } catch (Throwable t) {
      Logger.getLogger(Resources.class).warn("Exception getting resource " + key, t);
      return null;
    }
  }

  /**
   * @param key
   * @param loc
   * @return value for key and locale
   */
  public static String getString(String key, Locale loc) {
    try {
      return ResourceBundle.getBundle(bundleBase, loc).getString(key);
    } catch (Throwable t) {
      Logger.getLogger(Resources.class).warn("Exception getting resource " + key, t);
      return null;
    }
  }
}
