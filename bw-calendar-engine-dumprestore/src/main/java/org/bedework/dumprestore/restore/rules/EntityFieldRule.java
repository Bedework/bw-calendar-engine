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
package org.bedework.dumprestore.restore.rules;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.dumprestore.restore.RestoreGlobals;

import edu.rpi.cmt.timezones.Timezones;
import edu.rpi.sss.util.Util;

import net.fortuna.ical4j.model.TimeZone;

import org.xml.sax.Attributes;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * @author Mike Douglass   douglm rpi.edu
 * @version 1.0
 */
public abstract class EntityFieldRule extends RestoreRule {
  EntityFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  protected transient String tagName;
  protected transient String fldval;

  /**
   * @param name
   * @throws Throwable
   */
  public abstract void field(String name) throws Throwable;

  /**
   * @param name
   * @throws Exception
   */
  public void fieldStart(final String name) throws Exception {
  }

  protected boolean principalTags(final BwPrincipal entity,
                     final String name) throws Exception {
    if (name.equals("id") || name.equals("seq")) {
      return true;
    }

    if (name.equals("sponsor-access")) {                 // PRE3.3
      entity.setContactAccess(stringFld());
      return true;
    }

    if (name.equals("account")) {
      entity.setAccount(stringFld());
      return true;
    }

    if (name.equals("principalRef")) {
      entity.setPrincipalRef(Util.buildPath(true, stringFld()));
      return true;
    }

    if (name.equals("created")) {
      entity.setCreated(timestampFld());
      return true;
    }

    if (name.equals("logon")) {
      entity.setLogon(timestampFld());
      return true;
    }

    if (name.equals("lastAccess")) {
      entity.setLastAccess(timestampFld());
      return true;
    }

    if (name.equals("lastModify")) {
      entity.setLastModify(timestampFld());
      return true;
    }

    if (name.equals("category-access")) {           // PRE3.5
      entity.setCategoryAccess(stringFld());
      return true;
    }

    if (name.equals("location-access")) {           // PRE3.5
      entity.setLocationAccess(stringFld());
      return true;
    }

    if (name.equals("contact-access")) {           // PRE3.5
      entity.setContactAccess(stringFld());
      return true;
    }

    if (name.equals("categoryAccess")) {
      entity.setCategoryAccess(stringFld());
      return true;
    }

    if (name.equals("locationAccess")) {
      entity.setLocationAccess(stringFld());
      return true;
    }

    if (name.equals("contactAccess")) {
      entity.setContactAccess(stringFld());
      return true;
    }

    return false;
  }

  protected boolean groupTags(final BwGroup entity,
                              final String name) throws Exception {
    if (principalTags(entity, name)) {
      return true;
    }

    if (name.equals("member-key")) {
      // done
      return true;
    }

    return false;
  }

  protected boolean shareableContainedEntityTags(final BwShareableContainedDbentity entity,
                     final String name) throws Exception {
    if (shareableEntityTags(entity, name)) {
      return true;
    }

    if (name.equals("colPath") ||
        name.equals("calendar-path")) {     // PRE3.5
      String path = stringFld();

      if ((path != null) && (path.length() > 0)) {
        entity.setColPath(Util.buildPath(true, path));
      } // Otherwise assume root calendar
      return true;
    }

    return false;
  }

  protected boolean shareableEntityTags(final BwShareableDbentity entity,
                                        final String name) throws Exception {
    if (ownedEntityTags(entity, name)) {
      return true;
    }

    if (name.equals("creatorHref")) {
      entity.setCreatorHref(principalHrefFld());
      return true;
    }

    if (name.equals("creator-key")) {
      // Done already by CreatorRule.end
      return true;
    }

    if (name.equals("access")) {
      entity.setAccess(stringFld());
      return true;
    }

    return false;
  }

  protected boolean ownedEntityTags(final BwOwnedDbentity entity,
                                    final String name) throws Exception {
    if (name.equals("id")) {  // pre 3.5 - won't work
      return true;
    }

    if (name.equals("seq")) { // ignore
      return true;
    }

    if (name.equals("ownerHref")) {
      entity.setOwnerHref(principalHrefFld());
      return true;
    }

    if (name.equals("owner-key")) {
      // Done already by OwnerRule.end
      return true;
    }

    if (name.equals("public")) {
      entity.setPublick(booleanFld());
      return true;
    }

    return false;
  }

  @Override
  public void begin(final String ns, final String name, final Attributes attrs) throws Exception {
    fieldStart(name);
  }

  @Override
  public void body(final String namespace, final String name, final String text)
      throws Exception {
    /*
    if (globals.debug) {
      trace("Save field " + name);
    }
    */

    tagName = name;
    fldval = text;
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    if (globals.inOwnerKey) {
      /* Skip any owner-key tags here */
      return;
    }

    try {
      field(name);
    } catch (Throwable t) {
      error("Exception processing element " + name);
      if (top() == null) {
        error("Top is null");
      } else {
        error("Top class: " + top().getClass().getCanonicalName());
      }

      if (t instanceof Exception) {
        throw (Exception)t;
      }
      throw new Exception(t);
    }
  }

  protected String fixedDateTimeFld() throws Exception {
    String dtVal = stringFld();
    if ((dtVal.length() == 8) ||
          ((dtVal.charAt(13) == '0') && (dtVal.charAt(14) == '0'))) {
      return dtVal;
    }

    String prefix = dtVal.substring(0, 13);

    if (dtVal.length() == 16) {
       return prefix + "00Z";
     }

    return prefix + "00";
  }

  protected BwDateTime dateTimeFld(final DateTimeValues dtv) throws Exception {
    try {
      String tzid = dtv.tzid;

      if (tzid != null) {
        /* If this is a system timezone it's fine - otherwise try to convert to a
         * system timezone.
         */
        TimeZone tz = Timezones.getTz(tzid);

        if (tz == null) {
          globals.unmatchedTzids.add(tzid);

          tzid = null;
        } else if (!tzid.equals(tz.getID())){
          globals.convertedTzids++;
          tzid = tz.getID();
        }
      }

      BwDateTime dtim = BwDateTime.makeBwDateTime(dtv.dateType, dtv.dtval, tzid);

      if (!dtv.date.equals(dtim.getDate())) {
        warn("At event " + (globals.counts[globals.events] + 1) +
             ": UTC mismatch - file=" + dtv.date +
             " calculated=" + dtim);
      }

      return dtim;
    } catch (Throwable t) {
      if (t instanceof Exception) {
        throw (Exception)t;
      }
      throw new Exception(t);
    }
  }

  protected BwAdminGroup adminGroupFld() throws Exception {
    if (fldval == null) {
      throw new Exception("No value for " + tagName);
    }

    try {
      return globals.rintf.getAdminGroup(fldval);
    } catch (Throwable t) {
      if (t instanceof Exception) {
        throw (Exception)t;
      }
      throw new Exception(t);
    }
  }

  protected BwCalendar calendarFld() throws Exception {
    if (fldval == null) {
      throw new Exception("No value for " + tagName);
    }

    try {
      return globals.rintf.getCalendar(fldval);
    } catch (Throwable t) {
      if (t instanceof Exception) {
        throw (Exception)t;
      }
      throw new Exception(t);
    }
  }

  protected String principalHrefFld() throws Exception {
    if (fldval == null) {
      throw new Exception("No value for " + tagName);
    }

    /* At this point we should be mapping the href if we are restoring data
     * from different principal root paths
     */

    return Util.buildPath(true, fldval);
  }

  protected int intFld() throws Exception {
    if (fldval == null) {
      throw new Exception("No value for " + tagName);
    }

    return Integer.parseInt(fldval);
  }

  protected Integer integerFld() throws Exception {
    if (fldval == null) {
      throw new Exception("No value for " + tagName);
    }

    return Integer.valueOf(fldval);
  }

  protected long longFld() throws Exception {
    if (fldval == null) {
      throw new Exception("No value for " + tagName);
    }

    return Long.parseLong(fldval);
  }

  protected BigDecimal bigDecimalFld() throws Exception {
    if (fldval == null) {
      throw new Exception("No value for " + tagName);
    }

    return new BigDecimal(stringFld());
  }

  protected float floatFld() throws Exception {
    if (fldval == null) {
      throw new Exception("No value for " + tagName);
    }

    return Float.parseFloat(fldval);
  }

  protected Timestamp timestampFld() throws Exception {
    if ((fldval == null) || (fldval.length() == 0)) {
      throw new Exception("No value for " + tagName);
    }

    return  Timestamp.valueOf(fldval);
  }

  protected BwString bwStringFld(final String prefix, final String name,
                                 BwString fld) throws Exception {
    if (fld == null) {
      fld = new BwString();
    }

    if (name.equals(prefix + "-lang")) {
      fld.setLang(stringFld());
    } else if (name.equals(prefix + "-value")) {
      fld.setValue(stringFld());
    } else {
      unknownTag(name);
    }

    return  fld;
  }

  protected String stringFld() throws Exception {
    return  fldval;
  }

  protected boolean booleanFld() throws Exception {
    if (fldval == null) {
      throw new Exception("No value for " + tagName);
    }

    return  "true".equals(fldval);
  }

  protected char charFld() throws Exception {
    if (fldval == null) {
      throw new Exception("No value for " + tagName);
    }

    if (fldval.length() != 1) {
      throw new Exception("Bad value for fld" + fldval + " for tag " + tagName);
    }

    return fldval.charAt(0);
  }
}
