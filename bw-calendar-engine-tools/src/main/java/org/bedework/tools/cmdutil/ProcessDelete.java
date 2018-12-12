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
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author douglm
 *
 */
public class ProcessDelete extends CmdUtilHelper {
  ProcessDelete(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    final String wd = word();

    if (wd == null) {
      return false;
    }

    if ("help".equals(wd)) {
      addInfo("delete collection \"<path>\" [recursive]\n" +
                      "   delete given collection");

      addInfo("delete event \"<path>\" \"<name>\" \n" +
                      "   delete given event");

      addInfo("delete location uid=\"<uid>\" \n" +
                      "   delete given location");

      addInfo("delete view \"<name>\" \n" +
                      "   delete given view");

      addInfo("delete vmbr \"<name>\" \"<path>\" \n" +
                      "   delete entry from given view");

      addInfo("delete all views\n" +
                      "   delete all views for current user");

      return true;
    }

    if ("collection".equals(wd)) {
      return deleteCollection();
    }

    if ("event".equals(wd)) {
      return deleteEvent();
    }

    if ("location".equals(wd)) {
      return deleteLocation();
    }

    if ("view".equals(wd)) {
      return deleteView(quotedVal());
    }

    if ("vmbr".equals(wd)) {
      return deleteViewMember(quotedVal());
    }

    if ("all".equals(wd)) {
      return deleteAll(word());
    }

    return false;
  }

  @Override
  String command() {
    return "delete";
  }

  @Override
  String description() {
    return "delete collection or event";
  }

  private boolean deleteCollection() throws Throwable {
    BwCalendar cal = null;

    try {
      open();

      cal = getCal();

      if (cal == null) {
        return false;
      }

      final boolean emptyIt = "recursive".equals(word());

      getSvci().getCalendarsHandler().delete(cal, emptyIt, false);

      return true;
    } catch (final CalFacadeAccessException cae) {
      pstate.addError("No access to collection " + cal.getPath());
      return false;
    } finally {
      close();
    }
  }

  private boolean deleteEvent() throws Throwable {
    final String path = wordOrQuotedVal();

    if (path == null) {
      error("Expected a path");
      return false;
    }

    final String name = wordOrQuotedVal();

    if (name == null) {
      error("Expected a name");
      return false;
    }

    try {
      open();

      final EventInfo ei = getEvent(path, name);

      if (ei == null) {
        return false;
      }

      getSvci().getEventsHandler().delete(ei, false);

      return true;
    } catch (final CalFacadeAccessException cae) {
      pstate.addError("No access to event " + path + "/" + name);
      return false;
    } finally {
      close();
    }
  }

  private boolean deleteLocation() throws Throwable {
    final String wd = word();
    if (!"uid".equals(wd) || !testToken('=')) {
      addError("Expected \"uid\"=");
      return true;
    }
    
    final String uid = qstringFor("delete location");
    if (uid == null) {
      addError("Must supply uid");
      return true;
    }

    try {
      open();

      final BwLocation loc = getSvci().getLocationsHandler().getPersistent(uid);

      if (loc == null) {
        addError("Location " + uid +
                         " not found");
        return false;
      }

      getSvci().getLocationsHandler().delete(loc);
      return true;
    } finally {
      close();
    }
  }

  private boolean deleteView(final String name) throws Throwable {
    if (debug()) {
      debug("About to delete view " + name);
    }

    if (name == null) {
      addError("Must supply name");
      return false;
    }

    try {
      open();

      final BwView view = getSvci().getViewsHandler().find(name);

      if (view == null) {
        addError("View " + name + 
                         " not found");
        return false;
      }

      return getSvci().getViewsHandler().remove(view);
    } finally {
      close();
    }
  }

  private boolean deleteViewMember(final String name) throws Throwable {
    if (name == null) {
      addError("Must supply name");
      return false;
    }

    if (debug()) {
      debug("About to delete view member" + name);
    }

    final String path = quotedVal();

    if (path == null) {
      addError("Must supply path");
      return false;
    }

    try {
      open();

      getSvci().getViewsHandler().removeCollection(name, path);

      return true;
    } finally {
      close();
    }
  }

  private boolean deleteAll(final String type) throws Throwable {
    if (!"views".equals(type)) {
      error("Only allow views for delete all");
      return true;
    }
    
    try {
      open();

      final Collection<BwView> theViews = getSvci().getViewsHandler().getAll();

      if (Util.isEmpty(theViews)) {
        info("No views for current user");
        return false;
      }

      final List<BwView> views = new ArrayList<>(theViews);
      
      for (final BwView view: views) {
        if (getSvci().getViewsHandler().remove(view)) {
          info("Removed view " + view.getName());
        } else {
          warn("Unable to remove view " + view.getName());
        }
      }
    } finally {
      close();
    }
    
    return true;
  }
}
