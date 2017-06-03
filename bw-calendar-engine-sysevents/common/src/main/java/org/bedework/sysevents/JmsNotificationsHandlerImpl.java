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
import org.bedework.sysevents.events.HttpEvent;
import org.bedework.sysevents.events.MillisecsEvent;
import org.bedework.sysevents.events.ScheduleUpdateEvent;
import org.bedework.sysevents.events.SysEventBase;
import org.bedework.sysevents.events.SysEventBase.Attribute;
import org.bedework.sysevents.events.TimedEvent;
import org.bedework.sysevents.listeners.SysEventListener;

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
  /* Tried camel to multiplex but ran into many problems. We'll just open 
     multiple connections for the moment.
   */
  
  /* Default sysevents queue - everything goes here */

  static class JmsConn {
    private JmsConnectionHandler conn;

    private MessageProducer sender;
    
    JmsConn(final String queueName) throws NotificationException {
      conn = new JmsConnectionHandler();

      conn.open(queueName);

      sender = conn.getProducer();
    }
    
    public void post(final SysEventBase ev) throws NotificationException {
      try {
        final ObjectMessage msg = conn.getSession().createObjectMessage();

        msg.setObject(ev);

        for (final Attribute attr: ev.getMessageAttributes()) {
          msg.setStringProperty(attr.name, attr.value);
        }

        long start = System.currentTimeMillis();
        sender.send(msg);
        sends++;
        sendTime += System.currentTimeMillis() - start;
      } catch (final JMSException je) {
        throw new NotificationException(je);
      }
    }
  }

  private final JmsConn syslog;
  private final JmsConn monitor;
  private final JmsConn changes;
  private final JmsConn indexer;
  private final JmsConn scheduleIn;
  private final JmsConn scheduleOut;
  
  /*
   * We could use the activemq camel support (I think) to filter out certain
   * events and send them on to another queue.
   */

  JmsNotificationsHandlerImpl() throws NotificationException {
    syslog = new JmsConn(syseventsLogQueueName);
    monitor = new JmsConn(monitorQueueName);
    changes = new JmsConn(changesQueueName);
    indexer = new JmsConn(crawlerQueueName);
    scheduleIn = new JmsConn(schedulerInQueueName);
    scheduleOut = new JmsConn(schedulerOutQueueName);
  }

  private static long sends = 0;
  private static long sendTime = 0;

  @Override
  public void post(final SysEventBase ev) throws NotificationException {
    if (debug) {
      debug(ev.toString());
    }

    long start = System.currentTimeMillis();
    
    send: {
      if (ev instanceof MillisecsEvent) {
        monitor.post(ev);
        
        if ((ev instanceof TimedEvent) || (ev instanceof HttpEvent)) {
          break send;
        }
      }
      
      syslog.post(ev);
      changes.post(ev); // TODO - add some filtering option
      // indexer - not needed?
      
      if (ev instanceof EntityQueuedEvent) {
        final EntityQueuedEvent eqe = (EntityQueuedEvent)ev;
        
        if (((EntityQueuedEvent)ev).getInBox()) {
          scheduleIn.post(ev);
        } else {
          scheduleOut.post(ev);
          break send;
        }
      }
      
      if (ev instanceof ScheduleUpdateEvent) {
        scheduleIn.post(ev);
      }
    } // send

    sends++;
    sendTime += System.currentTimeMillis() - start;

    if ((sends % 100) == 0) {
      debug("Sends: " + sends + " avg: " + sendTime / sends);
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
}
