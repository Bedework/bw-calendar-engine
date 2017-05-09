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
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.svc.EventInfo;

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
      pstate.addInfo("delete collection \"<path>\" [recursive]\n" +
                             "   delete given collection");

      pstate.addInfo("delete event \"<path>\" \"<name>\" \n" +
                             "   delete given event");

      return true;
    }

    if ("collection".equals(wd)) {
      return deleteCollection();
    }

    if ("event".equals(wd)) {
      return deleteEvent();
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
}
