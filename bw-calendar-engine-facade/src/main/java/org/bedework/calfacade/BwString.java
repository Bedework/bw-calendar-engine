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
import org.bedework.calfacade.base.BwStringBase;
import org.bedework.util.xml.FromXmlCallback;

import java.util.Collection;

/** A String value in bedework. This class is for strings that are not very long
 * usually a max of 2-3k. This is mostly because db systems tend to handle long
 * strings in a different manner. (clobs, no searches etc)
 *
 * <p>Internally there is no difference, this just provides essentially a label
 * for the schema.
 *
 *  @version 1.0
 */
@Dump(elementName="bwstring", keyFields={"lang", "value"})
public class BwString extends BwStringBase {
  /** Constructor
   */
  public BwString() {
    super();
  }

  /** Create a string by specifying all its fields
   *
   * @param lang        String language code
   * @param value       String value
   */
  public BwString(final String lang,
                  final String value) {
    super(lang, value);
  }

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
  public static BwString findLang(final String lang, final Collection<BwString> c) {
    return (BwString)BwStringBase.findLanguage(lang, c);
  }

  /* ====================================================================
   *                   Restore callback
   * ==================================================================== */

  private static FromXmlCallback fromXmlCb;

  @NoDump
  public static FromXmlCallback getRestoreCallback() {
    if (fromXmlCb == null) {
      fromXmlCb = new FromXmlCallback();

      fromXmlCb.addSkips("byteSize",
                         "id",
                         "seq",
                         "size");
    }

    return fromXmlCb;
  }

  @Override
  public Object clone() {
    return new BwString(getLang(),
                        getValue());
  }
}
