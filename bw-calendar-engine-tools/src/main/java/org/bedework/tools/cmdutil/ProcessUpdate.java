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
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.UserAuth;

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

      addInfo("update loc <guid> <update>" +
                      "   where <update> is one of\n" +
                      "     addkey <name> <val>\n" +
                      "     updkey <name> <val>\n" +
                      "     delkey <name>\n" +
                      "     setfld <name> (<val>|null\n\n" +
                      "   <name> and <val> are quoted strings\n"
      );

      addInfo("update auth <userid> <update>" +
                      "   where <update> is one of\n" +
                      "     approver true|false\n" +
                      "     content true|false\n"
      );

      return true;
    }

    if ("collection".equals(wd)) {
      return updateCollection();
    }

    if ("loc".equals(wd)) {
      return updateLocation();
    }

    if ("auth".equals(wd)) {
      return updateAuthUser();
    }

    return false;
  }

  @Override
  String command() {
    return "update";
  }

  @Override
  String description() {
    return "Update collection or location";
  }

  private boolean updateCollection() {
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
            final Boolean val = boolFor(upname);
            if (val == null) {
              return true;
            }

            col.setIsTopicalArea(val);
            break;
          }

          case "summary": {
            final String val = qstringFor(upname);
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
    } catch (final Throwable t) {
      error(t);
      return false;
    } finally {
      close();
    }
  }

  private boolean updateLocation() {
    try {
      open();

      final BwLocation loc = getPersistentLoc();

      if (loc == null) {
        return true;
      }

      while (true) {
        final String upname = word();
        if (upname == null) {
          break;
        }

        switch (upname) {
          case "addkey": {
            final String name = qstringFor("key name");
            final String val = qstringFor("key value");

            loc.addKey(name, val);
            break;
          }

          case "updkey": {
            final String name = qstringFor("key name");
            final String val = qstringFor("key value");

            loc.updKey(name, val);
            break;
          }

          case "delkey": {
            final String name = qstringFor("key name");

            loc.delKey(name);
            break;
          }

          case "geo": {
            final String val = qstringFor("geo");

            loc.setGeouri(val);
            break;
          }
        }
      }

      getSvci().getLocationsHandler().update(loc);

      return true;
    } catch (final Throwable t) {
      error(t);
      return false;
    } finally {
      close();
    }
  }

  private boolean updateAuthUser() {
    try {
      open();

      final String userid = quotedVal();

      if (userid == null) {
        error("Expected a userid");
        return false;
      }

      final BwAuthUser au = getAuthUser(userid);


      while (true) {
        final String upname = word();
        if (upname == null) {
          break;
        }

        switch (upname) {
          case "approver": {
            if (!setFlag(au, word(), UserAuth.approverUser)) {
              return false;
            }

            break;
          }

          case "content": {
            if (!setFlag(au, word(), UserAuth.contentAdminUser)) {
              return false;
            }

            break;
          }

          default: {
            info("Bad flag name: " + upname);
            return false;
          }
        }
      }

      getSvci().getUserAuth().updateUser(au);

      return true;
    } catch (final Throwable t) {
      error(t);
      return false;
    } finally {
      close();
    }
  }

  private boolean setFlag(final BwAuthUser au, final String sw, final int flag) {
    if (sw.equals("false")) {
      au.setUsertype(au.getUsertype() & ~flag);
      return true;
    }

    if (sw.equals("true")) {
      au.setUsertype(au.getUsertype() | flag);
      return true;
    }

    info("Bad value for flag: " + sw);
    return false;
  }
}
