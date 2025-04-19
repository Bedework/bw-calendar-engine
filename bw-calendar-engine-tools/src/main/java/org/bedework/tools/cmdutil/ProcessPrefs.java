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

import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.util.misc.Util;

import java.util.Set;
import java.util.TreeSet;

/**
 * @author douglm
 *
 */
public class ProcessPrefs extends CmdUtilHelper {
  ProcessPrefs(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    String wd = word();

    if (wd == null) {
      return false;
    }

    if ("help".equals(wd)) {
      addInfo("""
                  prefs <preference>*\s
                     set given prefs for current user
                  
                  <preference> is one or more of
                      defaultCategory = "word"
                     defaultViewMode = "list"
                     viewPeriod = "dayView"
                     defaultTzid = "America/New_York"
                     pageSize = "10"
                     preferredEndType = "duration"
                     preferredView = "All"
                     hour24 = "false"
                  """);

      return true;
    }

    try {
      open();

      final BwPreferences prefs = getPrefs();
      if (prefs ==null) {
        error("Unable to fetch prefs for current user");
        return true; // No change
      }

      final Set<String> catUids = new TreeSet<>();
      
      while (wd != null) {
        switch (wd) {
          case "defaultImageDirectory":
            final BwCollection cal = getCal();
            if (cal == null) {
              error("Unknown collection");
              return true;
            }
            prefs.setDefaultImageDirectory(cal.getPath());
            break;

          case "preferredView":
              /*if (cl.getView(str) == null) {
                form.getErr().emit(ClientError.unknownView, str);
                return forwardNotFound;
              }*/
    
            prefs.setPreferredView(quotedVal());
            break;
    
          /*
            case"maxEntitySize")) {
              prefs.setMaxEntitySize(maxEntitySize);
            }
    
            case"viewPeriod")) {
              prefs.setPreferredViewPeriod(validViewPeriod(str));
            }
    
            str = validViewMode(request.getReqPar("defaultViewMode"));
            if (str != null) {
              prefs.setDefaultViewMode(str);
            }
    
            case"defaultTzid")) {
              if (Timezones.getTz(str) != null) {
                prefs.setDefaultTzid(str);
                tzChanged = true;
              }
            }*/

          case "defaultCategory":
            final String catwd = wordOrQuotedVal();
            if (catwd == null) {
              error("Expect category uid");
              return true;
            }

            if (catwd.equals("removeall")) {
              // Remove all
              prefs.removeProperties(
                      BwPreferences.propertyDefaultCategory);
              break;
            }

            final BwCategory cat = getCat(null, catwd);
            if (cat == null) {
              error("Unknown category " + catwd);
              return true;
            }

            catUids.add(cat.getUid());
            break;

          case "hour24":
            prefs.setHour24(Boolean.parseBoolean(word()));
            break;

          case "skin":
            prefs.setSkinName(wordOrQuotedVal());
            break;

          case "skinStyle":
            prefs.setSkinStyle(wordOrQuotedVal());
            break;

          case "email":
            prefs.setEmail(quotedVal());
            break;

            /*
            case"newCalPath")) {
              BwCollection cal = cl.getCollection(str);
              if (cal == null) {
                form.getErr().emit(ClientError.unknownCollection, str);
                return forwardNotFound;
              }
              prefs.setDefaultCalendarPath(cal.getPath());
            }
    
            case"pageSize")) {
              if (pageSize < 0) {
                form.getErr().emit(ValidationError.invalidPageSize);
                return forwardBadPref;
              } else {
                prefs.setPageSize(pageSize);
              }
            }
    
    
          case"workDays")) {
            // XXX validate
            prefs.setWorkDays(str);
          }
    
          case"workdayStart")) {
            if ((startMinutes < 0) || (startMinutes > ((24 * 60) - 1))) {
              form.getErr().emit(ValidationError.invalidPrefWorkDayStart);
              return forwardBadPref;
            }
          } else {
            startMinutes = prefs.getWorkdayStart();
          }
    
          case"workdayEnd")) {
            if ((endMinutes < 0) || (endMinutes > ((24 * 60) - 1))) {
              form.getErr().emit(ValidationError.invalidPrefWorkDayEnd);
              return forwardBadPref;
            }
          } else {
            endMinutes = prefs.getWorkdayEnd();
          }
    
          if (startMinutes > endMinutes) {
            form.getErr().emit(ValidationError.invalidPrefWorkDays);
            return forwardBadPref;
          }
    
          prefs.setWorkdayStart(startMinutes);
          prefs.setWorkdayEnd(endMinutes);
          */

          case "preferredEndType":
            final String str = word();
            if (BwPreferences.preferredEndTypeDuration.equals(str) ||
                    BwPreferences.preferredEndTypeDate.equals(str)) {
              prefs.setPreferredEndType(str);
            } else {
              error("Invalid end type " + str);
              return true;
            }
            break;
    
            /*
            case"scheduleAutoRespond")) {
              prefs.setScheduleAutoRespond(bool());
            }
    
            case"scheduleAutoCancelAction")) {
              if ((ival < 0) || (ival > BwPreferences.scheduleMaxAutoCancel)) {
                form.getErr().emit(ValidationError.invalidPrefAutoCancel);
                return forwardBadPref;
              }
    
              prefs.setScheduleAutoCancelAction(ival);
            }
    
            case"scheduleDoubleBook")) {
              prefs.setScheduleDoubleBook(bool);
            }
    
            case"scheduleAutoProcessResponses")) {
              if ((ival < 0) || (ival > BwPreferences.scheduleMaxAutoProcessResponses)) {
                form.getErr().emit(ValidationError.invalidPrefAutoProcess);
                return forwardBadPref;
              }
    
              prefs.setScheduleAutoProcessResponses(ival);
            }
            */
        } // switch
        
        wd = word();
      }

      if (!Util.isEmpty(catUids)) {
        prefs.setDefaultCategoryUids(catUids);
      }
      
      getSvci().getPrefsHandler().update(prefs);
    } finally {
      close();
    }

    return true;
  }

  @Override
  String command() {
    return "prefs";
  }

  @Override
  String description() {
    return "set user we are acting as";
  }
}
