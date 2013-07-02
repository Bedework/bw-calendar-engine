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
package org.bedework.calsvc.indexing;

import edu.rpi.cct.misc.indexing.IndexLuceneImpl.FieldInfo;

/** Haven't yet figured out how we'll internationalize queries. I think internal
 * lucene field names will have to be fixed and defined below and front end
 * implementors will need to provide a mapping.
 *
 * <p>We can possibly provide a subclass of the parser to take a mapping table
 * of allowable external names to internal names.
 *
 * <p>In any case, this class defines the names of all the fields we index.
 *
 * @author Mike Douglass douglm  rpi.edu
 *
 */
public class BwIndexLuceneDefs {
  private BwIndexLuceneDefs() {
    // There'll be no instantiation here
  }

  /* ------------------------ Index directory names ----------------------- */

  /** */
  public static final String newIndexname = "new";

  /** */
  public static final String currentIndexname = "current";

  /** */
  public static final String oldIndexname = "old";


  /* ---------------------------- Calendar fields ------------------------- */

  /** Key field for a calendar - must be stored */
  public static final FieldInfo calendarPath =
    new FieldInfo("calendarPath", true, false, 1);

  /* ------------------Event/todo/journal fields ------------------------- */

  /** */
  public static final FieldInfo comment =
    new FieldInfo("comment", true);

  /** */
  public static final FieldInfo contact =
    new FieldInfo("contact", true);

  /** */
  public static final FieldInfo dueDate =
    new FieldInfo("due", false, 3);

  /** */
  public static final FieldInfo endDate =
    new FieldInfo("end", false);

  /** */
  public static final FieldInfo location =
    new FieldInfo("location", true);

  /** */
  public static final FieldInfo resources =
    new FieldInfo("resources", true);

  /** */
  public static final FieldInfo startDate =
    new FieldInfo("start", false);

  /* ---------------------------- Common fields ------------------------- */

  /** */
  public static final FieldInfo calendar =
    new FieldInfo("calendar", true);

  /** */
  public static final FieldInfo category =
    new FieldInfo("category", true);

  /** */
  public static final FieldInfo created =
    new FieldInfo("created", false);

  /** */
  public static final FieldInfo creator =
    new FieldInfo("creator", false);

  /** */
  public static final FieldInfo description =
    new FieldInfo("description", true);

  /** */
  public static final FieldInfo lastmod =
    new FieldInfo("lastmod", false);

  /** */
  public static final FieldInfo owner =
    new FieldInfo("owner", false);

  /** */
  public static final FieldInfo summary =
    new FieldInfo("summary", true);

  /** */
  public static final FieldInfo recurrenceid =
    new FieldInfo("recurrenceid", false);

  /** */
  public static final FieldInfo defaultFieldInfo =
    new FieldInfo("default", true);

  /* Field names for fields which contain item type and key information.
   */

  /** Field name defining type of item - must be stored */
  public static final FieldInfo itemTypeInfo =
    new FieldInfo("itemType", true, false, 1);

  /** */
  public static final FieldInfo[] fields = {
    itemTypeInfo,

    // ----------------- Calendar
    calendarPath,

    // ----------------- Event/todo/journal
    calendar,
    comment,
    contact,
    dueDate,
    endDate,
    location,
    recurrenceid,

    // ----------------- Common
    category,
    created,
    creator,
    description,
    lastmod,
    owner,
    startDate,
    summary,

    defaultFieldInfo,
  };

  private static final String[] termNames = new String[fields.length];
  static {
    for (int i = 0; i < fields.length; i++) {
      termNames[i] = fields[i].getName();
    }
  }


  /**
   * @return String[]
   */
  public static String[] getTermNames() {
    return termNames;
  }

  /* Item types. We index various item types and these strings define each
   * type.
   */

  /** */
  public static final String itemTypeCalendar = "calendar";

  /** */
  public static final String itemTypeCategory = "category";

  /** */
  public static final String itemTypeEvent = "event";

  /** */
  public static final String itemTypeEventMaster = "eventMaster";

  /** Key field for calendar */
  public static final FieldInfo keyCalendar = calendarPath;

  /** Key for an event - must be stored */
  public static final FieldInfo keyEvent =
    new FieldInfo(itemTypeEvent, true, false, 1);

  /** Key for an event master - also must be stored */
  public static final FieldInfo keyEventMaster =
    new FieldInfo(itemTypeEventMaster, true, false, 1);

  /** */
  public static final String eventTypeEvent = "event";

  /** */
  public static final String eventTypetask = "task";

}
