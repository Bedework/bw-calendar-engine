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

import javax.jms.CompletionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;

/**
 * This is the implementation of a notifications handler which sends jms
 * messages.
 *
 * @author Mike Douglass douglm - rpi.edu
 */
class JmsNotificationsHandlerImpl extends NotificationsHandler implements
    JmsDefs, CompletionListener {
  /* Tried camel to multiplex but ran into many problems. We'll just open
     multiple connections for the moment.
   */
  
  /* Default sysevents queue - everything goes here */

  static class JmsConn {
    private final JmsConnectionHandler conn;

    private final MessageProducer sender;

    private final CompletionListener cl;
    
    JmsConn(final String queueName,
            final CompletionListener cl)  {
      try {
        this.cl = cl;

        conn = new JmsConnectionHandler();

        conn.open(queueName);

        sender = conn.getProducer();
        sender.setDisableMessageID(true);
      } catch (final Throwable t) {
        throw new RuntimeException(t);
      }
    }
    
    public void post(final SysEventBase ev) {
      try {
        final ObjectMessage msg = conn.getSession().createObjectMessage();

        msg.setObject(ev);

        for (final Attribute attr: ev.getMessageAttributes()) {
          msg.setStringProperty(attr.name, attr.value);
        }

        long start = System.currentTimeMillis();
        sender.send(msg, cl);
        sends++;
        sendTime += System.currentTimeMillis() - start;
      } catch (final JMSException je) {
        throw new RuntimeException(je);
      }
    }
    
    public void close() {
      conn.close();
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

  JmsNotificationsHandlerImpl() {
    syslog = new JmsConn(syseventsLogQueueName, this);
    monitor = new JmsConn(monitorQueueName, this);
    changes = new JmsConn(changesQueueName, this);
    indexer = new JmsConn(crawlerQueueName, this);
    scheduleIn = new JmsConn(schedulerInQueueName, this);
    scheduleOut = new JmsConn(schedulerOutQueueName, this);
  }

  @Override
  public void onCompletion(final Message message) {
    if (debug()) {
      debug("Completion message ");
    }
  }

  @Override
  public void onException(final Message message, final Exception e) {
    warn("Exception " + e.getMessage() +
                 " for message: " + message);
  }

  private static long sends = 0;
  private static long sendTime = 0;

  @Override
  public void post(final SysEventBase ev) {
    if (debug()) {
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
      
      boolean changeEvent = false;
      
      for (final Attribute attr: ev.getMessageAttributes()) {
        if (!"changeEvent".equals(attr.name)) {
          continue;
        }
        
        changeEvent = Boolean.parseBoolean(attr.value);
        break;
      }
      
      if (changeEvent) {
        changes.post(ev); 
      }

      // indexer - reindexing events
      if (ev.getSysCode() == SysEventBase.SysCode.REINDEX_EVENT) {
        indexer.post(ev);
      }
      
      if (ev instanceof EntityQueuedEvent) {
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
                               final boolean persistent) {

  }

  @Override
  public void removeListener(final SysEventListener l) {

  }

  @Override
  public void close() {
    if (syslog != null) {
      close(syslog);
    }
    
    if (monitor != null) {
      close(monitor);
    }

    if (changes != null) {
      close(changes);
    }

    if (indexer != null) {
      close(indexer);
    }

    if (scheduleIn != null) {
      close(scheduleIn);
    }

    if (scheduleOut != null) {
      close(scheduleOut);
    }
  }
  
  private void close(final JmsConn conn) {
    conn.close();
  }
}
