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
package org.bedework.sysevents.listeners;

import org.bedework.sysevents.JmsConnectionHandler;
import org.bedework.sysevents.JmsDefs;
import org.bedework.sysevents.NotificationException;
import org.bedework.sysevents.events.SysEvent;

import org.apache.log4j.Logger;

import java.io.InvalidClassException;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;

/** Listener class which receives messages from JMS.
 *
 * @author Mike Douglass
 */
public abstract class JmsSysEventListener
    implements MessageListener, ExceptionListener, JmsDefs {
  private transient Logger log;

  private JmsConnectionHandler conn;

  private MessageConsumer consumer;

  private boolean running = true;

  /**
   * @param queueName
   * @throws NotificationException
   */
  public void open(final String queueName) throws NotificationException {
    conn = new JmsConnectionHandler();

    conn.open(queueName);

    consumer = conn.getConsumer();
  }

  /** Close and release resources.
   *
   */
  public void close() {
    if (consumer != null) {
      try {
        consumer.close();
      } catch (Throwable t) {
        warn(t.getMessage());
      }
    }

    conn.close();
  }

  /** For asynch we do the onMessage listener style. Otherwise we wait
   * synchronously for incoming messages.
   *
   * @param asynch
   * @throws NotificationException
   */
  public void process(final boolean asynch) throws NotificationException {
    if (asynch) {
      try {
        consumer.setMessageListener(this);
        return;
      } catch (JMSException je) {
        throw new NotificationException(je);
      }
    }

    while (running) {
      for (;;) {
        Message m = conn.receive();
        onMessage(m);
      }
    }
  }

  /* (non-Javadoc)
   * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
   */
  public void onMessage(final Message message) {
    try {
      if (message instanceof ObjectMessage) {
        SysEvent ev = (SysEvent)((ObjectMessage)message).getObject();

        action(ev);
      }
    } catch (NotificationException ne) {
      ne.printStackTrace();
    } catch (JMSException je) {
      Throwable t = je.getCause();

      if (t instanceof InvalidClassException) {
        /* Probably an old message - just ignore it. */
        warn("Ignoring message of unknown class");
      }
      error(je);
    }
  }

  public synchronized void onException(final JMSException ex) {
  }

  /** Called whenever a matching event occurs.
   *
   * @param ev
   * @throws NotificationException
   */
  public abstract void action(SysEvent ev) throws NotificationException;

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  protected void info(final String msg) {
    getLogger().info(msg);
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }

  protected void warn(final String msg) {
    getLogger().warn(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }

  protected void error(final String msg) {
    getLogger().error(msg);
  }

  /* Get a logger for messages
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }
}
