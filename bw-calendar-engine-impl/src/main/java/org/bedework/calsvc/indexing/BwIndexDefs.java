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

/** Haven't yet figured out how we'll internationalize queries. I think internal
 * lucene field names will have to be fixed and defined below and front end
 * implementors will need to provide a mapping.
 *
 * <p>We can possibly provide a subclass of the parser to take a mapping table
 * of allowable external names to internal names.
 *
 * <p>In any case, this class defines the names of all the fields we index.
 *
 * @author Mike Douglass douglm  bedework.edu
 *
 */
public class BwIndexDefs {
  private BwIndexDefs() {
    // There'll be no instantiation here
  }

  /* ------------------------ Index directory names ----------------------- */

  /** */
  public static final String newIndexname = "new";

  /** */
  public static final String currentIndexname = "current";

  /** */
  public static final String oldIndexname = "old";
  /* Field names for fields which contain item type and key information.
   */

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

  /** */
  public static final String eventTypeEvent = "event";

  /** */
  public static final String eventTypetask = "task";

}
