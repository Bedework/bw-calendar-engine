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
package org.bedework.calsvc.scheduling;

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.Participant;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.ScheduleResult.ScheduleRecipientResult;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.CalSvc;
import org.bedework.calsvc.scheduling.hosts.HostInfo;
import org.bedework.calsvc.scheduling.hosts.IscheduleClient;
import org.bedework.calsvc.scheduling.hosts.Response;
import org.bedework.calsvc.scheduling.hosts.Response.ResponseElement;
import org.bedework.convert.IcalTranslator;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.ScheduleStates;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Rather than have a single class steering calls to a number of smaller classes
 * we will build up a full implementation by progressively implementing abstract
 * classes.
 *
 * <p>That allows us to split up some rather complex code into appropriate peices.
 *
 * <p>This piece handles the iSchedule low-level methods
 *
 * @author douglm
 *
 */
public abstract class IScheduleHandler extends FreeAndBusyHandler {
  protected static class UserInbox extends ScheduleRecipientResult {
    final BwEvent ev;

    //String account;
    BwPrincipal<?> principal;

    /* Attendee objects in the organizers event and overrides */
    private final List<Participant> atts = new ArrayList<>();

    String inboxPath; // null for our own account

    /* Non-null if this is an external recipient we can process in real-time */
    private HostInfo host;

    public UserInbox(final BwEvent ev,
                     final String recipient) {
      super();
      this.ev = ev;
      this.recipient = recipient;
    }

    public void addAttendee(final Participant val) {
      /*
      for (final BwAttendee att: atts) {
        if (val.unsaved()) {
          if (val == att) {
            return;  // already there
          }
        } else if (val.getId() == att.getId()) {
          return;  // already there
        }
      }*/

      atts.add(val);
    }

    public void setAttendeeScheduleStatus(final String val) {
      for (final var att: atts) {
        att.setScheduleStatus(val);
      }
    }

    public void setHost(final HostInfo val) {
      host = val;
    }

    public HostInfo getHost() {
      return host;
    }
  }

  private IscheduleClient calDav;

  IScheduleHandler(final CalSvc svci) {
    super(svci);
  }

  /* External iSchedule requests */
  protected void sendExternalRequest(final ScheduleResult sr,
                                     final EventInfo ei,
                                     final Collection<UserInbox> inboxes) {
    /* Each entry in inboxes should have the same hostinfo */

    HostInfo hi = null;
    final BwEvent ev = ei.getEvent();
    final boolean freeBusyRequest = ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy;

    Set<String> recipients = null;

    /*
    class Request {
      HostInfo hi;

      String url;

      String content;
    }
    */

    final HashMap<String, UserInbox> uimap = new HashMap<>();

    /* For realtime or caldav we make up a single meeting request or freebusy request.
     * For freebusy url we just execute one url at a time
     */
    for (final UserInbox ui: inboxes) {
      if (hi == null) {
        // First time
        hi = ui.host;

        if (hi.getSupportsBedework() ||
            hi.getSupportsCaldav() ||
            hi.getSupportsISchedule()) {
          recipients = new TreeSet<>();
        }
      }

      if (recipients == null) {
        // request per recipient - only freebusy
        if (debug()) {
          debug("freebusy request to " + hi.getFbUrl() + " for " + ui.recipient);
        }
      } else {
        recipients.add(ui.recipient);
        uimap.put(ui.recipient, ui);
      }
    }

    if (recipients == null) {
      // No ischedule requests
      return;
    }

    if (debug()) {
      final String meth;
      if (freeBusyRequest) {
        meth = "freebusy";
      } else {
        meth = "meeting";
      }

      trace(meth + " request to " + hi.getFbUrl() + " for " + recipients);
    }

    final EventInfo cei = copyEventInfo(ei, getPrincipal());
    cei.getEvent().setRecipients(recipients);

    final Response r;
    if (freeBusyRequest) {
      try {
        r = getCalDavClient().getFreeBusy(hi, cei);
      } catch (final CalFacadeException cfe) {
        error(cfe);
        return;
      }

      /* Each recipient in the list of user inboxes should have a
       * corresponding response element.
       */

      for (final ResponseElement re: r.getResponses()) {
        final UserInbox ui = uimap.get(re.getRecipient());

        if (ui == null) {
          continue;
        }

        if (re.getCalData() == null) {
          ui.setStatus(ScheduleStates.scheduleUnprocessed);
          continue;
        }

        ui.freeBusy = re.getCalData().getEvent();
        ui.setStatus(ScheduleStates.scheduleOk);
        sr.externalRcs.remove(ui.recipient);
      }

      return;
    }

    // Meeting request

    try {
      r = getCalDavClient().scheduleMeeting(hi, cei);
    } catch (final CalFacadeException cfe) {
      error(cfe);
      return;
    }

    for (final ResponseElement re: r.getResponses()) {
      final UserInbox ui = uimap.get(re.getRecipient());

      if (ui == null) {
        continue;
      }

      ui.setStatus(ScheduleStates.scheduleOk);
      sr.externalRcs.remove(ui.recipient);
    }
  }

  private IscheduleClient getCalDavClient() {
    class PrivateKeysGetter extends IscheduleClient.PrivateKeys {
      @Override
      public PrivateKey getKey(final String domain,
                               final String service) {
        try {
          return getEncrypter().getPrivateKey();
        } catch (final Throwable t) {
          throw new CalFacadeException(t);
        }
      }
    }
    if (calDav == null) {
      calDav = new IscheduleClient(
              getSvc(),
              new IcalTranslator(getSvc().getIcalCallback()),
              new PrivateKeysGetter(),
              getSvc().getDirectories().getDefaultDomain());
    }

    return calDav;
  }
}
