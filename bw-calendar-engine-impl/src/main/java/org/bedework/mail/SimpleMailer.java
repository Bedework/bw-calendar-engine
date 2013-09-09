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

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;

import org.apache.log4j.Logger;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Properties;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/** A mailer which provides some minimal functionality.
 * We do not consider many issues such as spam prevention, efficiency in
 * mailing to large lists, etc.
 *
 * @author  Mike Douglass douglm@bedework.edu
 */
public class SimpleMailer implements MailerIntf {
  private boolean debug;

  private MailConfigProperties config;

  private Session sess;

  private transient Logger log;

  @Override
  public void init(final MailConfigProperties config) throws CalFacadeException {
    debug = getLog().isDebugEnabled();
    this.config = config;

    Properties props = new Properties();

    props.put("mail." + config.getProtocol() + ".class", config.getProtocolClass());
    props.put("mail." + config.getProtocol() + ".host", config.getServerUri());
    if (config.getServerPort() != null) {
      props.put("mail." + config.getProtocol() + ".port",
                config.getServerPort());
    }

    //  add handlers for main MIME types
    MailcapCommandMap mc = (MailcapCommandMap)CommandMap.getDefaultCommandMap();
    mc.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html");
    mc.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml");
    mc.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain");
    mc.addMailcap("text/calendar;; x-java-content-handler=com.sun.mail.handlers.text_html");
    mc.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed");
    mc.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822");
    CommandMap.setDefaultCommandMap(mc);

    sess = Session.getInstance(props);
    sess.setDebug(debug);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.mail.MailerIntf#mailEntity(net.fortuna.ical4j.model.Calendar, java.lang.String, java.util.Collection, java.lang.String)
   */
  @Override
  public boolean mailEntity(final Calendar cal,
                            String originator,
                            final Collection<String>recipients,
                            String subject) throws CalFacadeException {
    if (debug) {
      debugMsg("mailEntity called with " + cal);
    }

    if (config.getDisabled()) {
      return false;
    }

    try {
      /* Create a message with the appropriate mime-type
       */
      MimeMessage msg = new MimeMessage(sess);

      if (originator == null) {
        originator = config.getFrom();
      }
      msg.setFrom(new InternetAddress(originator));

      InternetAddress[] tos = new InternetAddress[recipients.size()];

      int i = 0;
      for (String recip: recipients) {
        tos[i] = new InternetAddress(recip);
        i++;
      }

      msg.setRecipients(javax.mail.Message.RecipientType.TO, tos);

      if (subject == null) {
        subject = config.getSubject();
      }

      msg.setSubject(subject);
      msg.setSentDate(new Date());

      CalendarOutputter co = new CalendarOutputter(false);

      Writer wtr =  new StringWriter();
      co.output(cal, wtr);
      String content = wtr.toString();

      msg.setContent(content, "text/calendar");

      Transport tr = sess.getTransport(config.getProtocol());

      tr.connect();
      tr.sendMessage(msg, tos);

      return true;
    } catch (Throwable t) {
      if (debug) {
        t.printStackTrace();
      }

      throw new CalFacadeException(t);
    }
  }

  @Override
  public void addList(final BwCalendar cal) throws CalFacadeException {
    debugMsg("addList called with " + cal.getName());
  }

  @Override
  public void deleteList(final BwCalendar cal) throws CalFacadeException {
    debugMsg("deleteList called with " + cal.getName());
  }

  @Override
  public Collection<String> listLists() throws CalFacadeException {
    debugMsg("listLists called");
    return new ArrayList<String>();
  }

  @Override
  public boolean checkList(final BwCalendar cal) throws CalFacadeException {
    debugMsg("checkList called with " + cal.getName());
    return true;
  }

  @Override
  public void postList(final BwCalendar cal, final Message val) throws CalFacadeException {
    debugMsg("postList called with " + cal.getName() + " and message:");
    debugMsg(val.toString());
  }

  @Override
  public void addMember(final BwCalendar cal, final BwPrincipal member) throws CalFacadeException {
    debugMsg("addUser called with " + cal.getName() + " and member " +
             member.getAccount());
  }

  @Override
  public void removeMember(final BwCalendar cal, final BwPrincipal member) throws CalFacadeException {
    debugMsg("removeUser called with " + cal.getName() + " and member " +
             member.getAccount());
  }

  @Override
  public boolean checkMember(final BwCalendar cal, final BwPrincipal member) throws CalFacadeException {
    debugMsg("checkUser called with " + cal.getName() + " and member " +
             member.getAccount());
    return true;
  }

  @Override
  public void updateMember(final BwCalendar cal, final BwPrincipal member, final String newEmail)
        throws CalFacadeException {
    debugMsg("updateUser called with " + cal.getName() + " and member " +
             member.getAccount() + " and new email " + newEmail);
  }

  @Override
  public Collection<BwPrincipal> listMembers(final BwCalendar cal) throws CalFacadeException {
    debugMsg("listUsers called with " + cal.getName());
    return new ArrayList<BwPrincipal>();
  }

  @Override
  public void post(final Message val) throws CalFacadeException {
    debugMsg("Mailer called with:");
    debugMsg(val.toString());

    if (config.getDisabled()) {
      return;
    }

    try {
      /* Create a message with the appropriate mime-type
       */
      MimeMessage msg = new MimeMessage(sess);

      msg.setFrom(new InternetAddress(val.getFrom()));

      InternetAddress[] tos = new InternetAddress[val.getMailTo().length];

      int i = 0;
      for (String recip: val.getMailTo()) {
        tos[i] = new InternetAddress(recip);
        i++;
      }

      msg.setRecipients(javax.mail.Message.RecipientType.TO, tos);

      msg.setSubject(val.getSubject());
      msg.setSentDate(new Date());

      msg.setContent(val.getContent(), "text/plain");

      Transport tr = sess.getTransport(config.getProtocol());

      tr.connect();
      tr.sendMessage(msg, tos);
    } catch (Throwable t) {
      if (debug) {
        t.printStackTrace();
      }

      throw new CalFacadeException(t);
    }
  }

  private Logger getLog() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  private void debugMsg(final String msg) {
    getLog().debug(msg);
  }
}
