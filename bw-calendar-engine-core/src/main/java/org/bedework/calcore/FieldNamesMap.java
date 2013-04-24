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
package org.bedework.calcore;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/** Table we use to handle the list of desired properties.
 *
 * @author Mike Douglass
 */
public class FieldNamesMap extends HashMap<String, FieldNamesEntry> {
  /**
   */
  public static class FieldnamesList extends ArrayList<FieldNamesEntry> {
    /**
     */
    public FieldnamesList() {
      super();
    }

    /**
     * @param size
     */
    public FieldnamesList(final int size) {
      super(size);
    }

    @Override
    public boolean add(final FieldNamesEntry val) {
      if (contains(val)) {
        return false;
      }

      super.add(val);
      return true;
    }

    @Override
    public boolean addAll(final Collection<? extends FieldNamesEntry> val) {
      boolean changed = false;
      for (FieldNamesEntry fent: val) {
        changed |= add(fent);
      }

      return changed;
    }
  }

  /** The following may not be in the table but are required for access and
   * validity checks
   */
  public static final FieldnamesList reqFlds = new FieldnamesList();

  static {
    reqFlds.add(new FieldNamesEntry("", "access", false));
    reqFlds.add(new FieldNamesEntry("", "colPath", false));
    reqFlds.add(new FieldNamesEntry("CREATED", "created", false));
    reqFlds.add(new FieldNamesEntry("", "creatorHref", false));
    reqFlds.add(new FieldNamesEntry("", "ctoken", false));
    reqFlds.add(new FieldNamesEntry("", "dtend", false));
    reqFlds.add(new FieldNamesEntry("", "dtstamp", false));
    reqFlds.add(new FieldNamesEntry("", "dtstart", false));
    reqFlds.add(new FieldNamesEntry("", "duration", false));
    reqFlds.add(new FieldNamesEntry("", "endType", false));
    reqFlds.add(new FieldNamesEntry("", "entityType", false));
    reqFlds.add(new FieldNamesEntry("", "id", false));
    reqFlds.add(new FieldNamesEntry("", "name", false));
    reqFlds.add(new FieldNamesEntry("", "noStart", false));
    reqFlds.add(new FieldNamesEntry("", "ownerHref", false));
    reqFlds.add(new FieldNamesEntry("UID", "uid", false));
  };

  /** Fields required for the annotation class.
   */
  public static final FieldnamesList annotationRequired = new FieldnamesList();

  static {
    annotationRequired.add(new FieldNamesEntry("", "override", false));
    annotationRequired.add(new FieldNamesEntry("", "target", false));
    annotationRequired.add(new FieldNamesEntry("", "master", false));
  }

  private static final FieldNamesMap theMap = new FieldNamesMap();

  /**
   * @param pname - property name
   * @return the entry or null
   */
  public static FieldNamesEntry getEntry(final String pname) {
    return theMap.get(pname.toUpperCase());
  }

  /** Gets an initialized table.
   *
   */
  private FieldNamesMap() {
    super();

    put(new FieldNamesEntry("ATTACH", "attachments", true));

    put(new FieldNamesEntry("ATTENDEE", "attendees", true));

    put(new FieldNamesEntry("CATEGORIES", "categories", true));

    put(new FieldNamesEntry("CLASS", "classification", false));

    put(new FieldNamesEntry("COMMENT", "comments", true));

    put(new FieldNamesEntry("COMPLETED", "completed", false));

    put(new FieldNamesEntry("CONTACT", "contacts", true));

    put(new FieldNamesEntry("CREATED", "created", false));

    put(new FieldNamesEntry("DESCRIPTION", "descriptions", true));

    put(new FieldNamesEntry("DTEND", "dtend", false));

    put(new FieldNamesEntry("DTSTAMP", "dtstamp", false));

    put(new FieldNamesEntry("DTSTART", "dtstart", false));

    put(new FieldNamesEntry("DUE", "dtend", false));

    put(new FieldNamesEntry("DURATION", "duration", false));

    put(new FieldNamesEntry("EXDATE", "exdates", true));

    put(new FieldNamesEntry("EXRULE", "exrules", true));

    put(new FieldNamesEntry("GEO", "geo", false));

    put(new FieldNamesEntry("LAST-MODIFIED", "lastmod", false));

    put(new FieldNamesEntry("LOCATION", "location", false));

    put(new FieldNamesEntry("ORGANIZER", "organizer", false));

    put(new FieldNamesEntry("PERCENT-COMPLETE", "percentComplete", false));

    put(new FieldNamesEntry("PRIORITY", "priority", false));

    put(new FieldNamesEntry("RDATE", "rdates", true));

    put(new FieldNamesEntry("RECURRENCE-ID", "recurrenceId", false));

    put(new FieldNamesEntry("RELATED-TO", "relatedTo", false));

    put(new FieldNamesEntry("REQUEST-STATUS", "requestStatuses", true));

    put(new FieldNamesEntry("RESOURCES", "resources", true));

    put(new FieldNamesEntry("RRULE", "rrules", "rrule", true));

    put(new FieldNamesEntry("SEQUENCE", "sequence", false));

    put(new FieldNamesEntry("STATUS", "status", false));

    put(new FieldNamesEntry("SUMMARY", "summaries", "summary", true));

    put(new FieldNamesEntry("TRANSP", "transparency", false));

    put(new FieldNamesEntry("UID", "uid", false));

    put(new FieldNamesEntry("URL", "link", false));

    put(new FieldNamesEntry("VALARM", "alarms", true));

    put(new FieldNamesEntry("X-", "xproperties", true));

    /* Non - ical */

    put(new FieldNamesEntry("ACCESS", "access", false));

    put(new FieldNamesEntry("COLPATH", "colPath", false));

    put(new FieldNamesEntry("COST", "cost", false));

    put(new FieldNamesEntry("CREATED", "created", false));

    put(new FieldNamesEntry("CREATOR", "creatorHref", false));

    put(new FieldNamesEntry("DELETED", "deleted", false));

    put(new FieldNamesEntry("OWNER", "ownerHref", false));

    put(new FieldNamesEntry("TOMBSTONED", "tombstoned", false));

    put(new FieldNamesEntry("{DAV:}GETETAG", "lastmod", false));
  }

  /**
   * @param val
   */
  private void put(final FieldNamesEntry val) {
    put(val.getPname(), val);
  }
}
