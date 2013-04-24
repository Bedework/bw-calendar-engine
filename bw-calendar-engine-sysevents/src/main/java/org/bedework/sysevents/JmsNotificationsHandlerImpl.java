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
package org.bedework.sysevents;

import org.bedework.sysevents.events.EntityQueuedEvent;
import org.bedework.sysevents.events.ScheduleUpdateEvent;
import org.bedework.sysevents.events.SysEventBase;
import org.bedework.sysevents.listeners.SysEventListener;

import org.apache.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;

/**
 * This is the implementation of a notifications handler which sends jms
 * messages.
 *
 * @author Mike Douglass douglm - rpi.edu
 */
class JmsNotificationsHandlerImpl extends NotificationsHandler implements
    JmsDefs {
  private transient Logger log;

  /* Default sysevents queue - everything goes here */

  private JmsConnectionHandler conn;

  private MessageProducer sender;

  /*
   * We could use the activemq camel support (I think) to filter out certain
   * events and send them on to another queue.
   */

  private boolean debug;

  JmsNotificationsHandlerImpl() throws NotificationException {
    debug = getLogger().isDebugEnabled();

    conn = new JmsConnectionHandler();

    conn.open(syseventsQueueName);

    sender = conn.getProducer();
  }

  @Override
  public void post(final SysEventBase ev) throws NotificationException {
    if (debug) {
      trace(ev.toString());
    }

    try {
      ObjectMessage msg = conn.getSession().createObjectMessage();

      msg.setObject(ev);
      msg.setStringProperty("syscode", String.valueOf(ev.getSysCode()));
      msg.setStringProperty("indexable",
                            String.valueOf(ev.getSysCode().getIndexable()));
      msg.setStringProperty("changeEvent",
                            String.valueOf(ev.getSysCode().getChangeEvent()));

      if (ev instanceof EntityQueuedEvent) {
        EntityQueuedEvent eqe = (EntityQueuedEvent)ev;

        if (eqe.getInBox()) {
          msg.setStringProperty("inbox", "true");
        } else {
          msg.setStringProperty("outbox", "true");
        }
      } else if (ev instanceof ScheduleUpdateEvent) {
        msg.setStringProperty("scheduleEvent", "true");
      }

      sender.send(msg);
    } catch (JMSException je) {
      throw new NotificationException(je);
    }
  }

  @Override
  public void registerListener(final SysEventListener l,
                               final boolean persistent)
                                                        throws NotificationException {

  }

  @Override
  public void removeListener(final SysEventListener l)
                                                      throws NotificationException {

  }

  /*
   * ====================================================================
   * Protected methods
   * ====================================================================
   */

  protected void info(final String msg) {
    getLogger().info(msg);
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }

  /*
   * Get a logger for messages
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }
}
