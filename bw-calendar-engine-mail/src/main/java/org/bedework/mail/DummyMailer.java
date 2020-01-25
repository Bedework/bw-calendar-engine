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

package org.bedework.mail;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.mail.MailConfigProperties;
import org.bedework.calfacade.mail.MailerIntf;
import org.bedework.calfacade.mail.Message;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

import net.fortuna.ical4j.model.Calendar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/** A dummy mailer which just writes to the log.
 *
 * @author  Mike Douglass douglm@rpi.edu
 */
public class DummyMailer implements Logged, MailerIntf {
  //private boolean debug;

  private MailConfigProperties config;

  @Override
  public void init(final MailConfigProperties config) {
    this.config = config;
  }

  @Override
  public boolean mailEntity(final Calendar cal,
                            final String originator,
                            final Collection<String>recipients,
                            final String subject) throws CalFacadeException {
    if (config.getDisabled()) {
      return false;
    }

    debug("mailEntity called with " + Arrays.toString(recipients.toArray()));
    debug(cal.toString());

    return true;
  }

  @Override
  public void addList(final BwCalendar cal) throws CalFacadeException {
    debug("addList called with " + cal.getName());
  }

  @Override
  public void deleteList(final BwCalendar cal) throws CalFacadeException {
    debug("deleteList called with " + cal.getName());
  }

  @Override
  public Collection<String> listLists() throws CalFacadeException {
    debug("listLists called");
    return new ArrayList<String>();
  }

  @Override
  public boolean checkList(final BwCalendar cal) throws CalFacadeException {
    debug("checkList called with " + cal.getName());
    return true;
  }

  @Override
  public void postList(final BwCalendar cal, final Message val) throws CalFacadeException {
    debug("postList called with " + cal.getName() + " and message:");
    debug(val.toString());
  }

  @Override
  public void addMember(final BwCalendar cal, final BwPrincipal member) throws CalFacadeException {
    debug("addUser called with " + cal.getName() + " and member " +
             member.getAccount());
  }

  @Override
  public void removeMember(final BwCalendar cal, final BwPrincipal member) throws CalFacadeException {
    debug("removeUser called with " + cal.getName() + " and member " +
             member.getAccount());
  }

  @Override
  public boolean checkMember(final BwCalendar cal, final BwPrincipal member) throws CalFacadeException {
    debug("checkUser called with " + cal.getName() + " and member " +
             member.getAccount());
    return true;
  }

  @Override
  public void updateMember(final BwCalendar cal, final BwPrincipal member, final String newEmail)
        throws CalFacadeException {
    debug("updateUser called with " + cal.getName() + " and member " +
        member.getAccount() + " and new email " + newEmail);
  }

  @Override
  public Collection<BwPrincipal> listMembers(final BwCalendar cal) throws CalFacadeException {
    debug("listUsers called with " + cal.getName());
    return new ArrayList<BwPrincipal>();
  }

  @Override
  public void post(final Message val) throws CalFacadeException {
    debug("Mailer called with:");
    debug(val.toString());
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
