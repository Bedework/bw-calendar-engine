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
package org.bedework.tools.cmdutil;

import org.bedework.calfacade.BwCalendar;

/**
 * @author douglm
 *
 */
public class ProcessUpdate extends CmdUtilHelper {
  ProcessUpdate(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    final String wd = word();

    if (wd == null) {
      return false;
    }

    if ("help".equals(wd)) {
      addInfo("update collection <path> <update>" +
                      "   where <update> is one of\n" +
                      "     topicalArea=true|false\n"
      );
      
      return true;
    }

    if ("collection".equals(wd)) {
      return updateCollection();
    }

    return false;
  }

  @Override
  String command() {
    return "update";
  }

  @Override
  String description() {
    return "Update collection";
  }

  private boolean updateCollection() throws Throwable {
    try {
      open();

      final BwCalendar col = getCal();

      if (col == null) {
        warn("No path or no calendar");
        return true;
      }

      while (true) {
        final String upname = word();
        if (upname == null) {
          break;
        }

        assertToken('=');
        
        switch (upname) {
          case "topicalArea": {
            Boolean val = boolFor(upname);
            if (val == null) {
              return true;
            }

            col.setIsTopicalArea(val);
            break;
          }

          case "summary": {
            String val = qstringFor(upname);
            if (val == null) {
              return true;
            }

            col.setSummary(val);
            break;
          }
        }
      }
      
      getSvci().getCalendarsHandler().update(col);

      return true;
    } finally {
      close();
    }
  }
}
