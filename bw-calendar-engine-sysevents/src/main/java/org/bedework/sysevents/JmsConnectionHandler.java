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

import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.util.misc.Util;

import org.apache.log4j.Logger;

import java.io.InputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/** This is a class to ease setting up of JMS connections..
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
public class JmsConnectionHandler implements JmsDefs {
  private boolean debug;

  /** Location of the properties file */
  private static final String propertiesFile =
      "/sysevents.properties";

  private static SystemProperties sysProps;

  private static volatile Properties pr;

  private static final Object lockit = new Object();

  private transient Logger log;

//  private boolean debug;

  private Connection connection;

  private Queue ourQueue;

  private Session session;

  private MessageConsumer consumer;

  /**
   */
  public JmsConnectionHandler() {
    debug = getLogger().isDebugEnabled();
  }

  /** Open a connection to the named queue ready to create a producer or
   * consumer.
   *
   * @param queueName the queue
   * @throws NotificationException
   */
  public void open(final String queueName) throws NotificationException {
    try {
      final ConnectionFactory connFactory;

      final Properties pr = getPr();
      final Context ctx = new InitialContext(pr);
      /*
      try {
        Context jcectx = (Context)ctx.lookup("java:comp/env/");

        // Still here - use that
        if (jcectx != null) {
          ctx = jcectx;
        }
      } catch (NamingException nfe) {
        // Stay with root
      }
      */

      try {
        connFactory = (ConnectionFactory)ctx.lookup(
                    pr.getProperty("org.bedework.connection.factory.name"));

//        connFactory = (ConnectionFactory)ctx.lookup(connFactoryName);

        connection = connFactory.createConnection();

      } catch (final Throwable t) {
        if (debug) {
          error(t);
        }
        throw new NotificationException(t);
      }

      try {
        /* Session is not transacted,
        * uses AUTO_ACKNOWLEDGE for message
        * acknowledgement
        */
        session = connection.createSession(useTransactions, ackMode);
        final String qn = pr.getProperty("org.bedework.jms.queue.prefix") +
                queueName;

        try {
          ourQueue =  (Queue)new InitialContext().lookup(qn);
        } catch (final NamingException ne) {
          // Try again with our own context
          ourQueue =  (Queue)ctx.lookup(qn);
        }
      } catch (final Throwable t) {
        if (debug) {
          error(t);
        }
        throw new NotificationException(t);
      }
    } catch (final NotificationException ne) {
      throw ne;
    } catch (final Throwable t) {
      if (debug) {
        error(t);
      }
      throw new NotificationException(t);
    }
  }

  /**
   *
   */
  public void close() {
    try {
      if (session != null) {
        session.close();
      }
    } catch (final Throwable t) {
      warn(t.getMessage());
    }
  }

  /**
   * @return jms session
   */
  public Session getSession() {
    return session;
  }

  /**
   * @return a message producer
   * @throws NotificationException
   */
  public MessageProducer getProducer() throws NotificationException {
    try {
      final MessageProducer sender = session.createProducer(ourQueue);

      connection.start();

      return sender;
    } catch (final JMSException je) {
      throw new NotificationException(je);
    }
  }

  /**
   * @return a message consumer
   * @throws NotificationException
   */
  public MessageConsumer getConsumer() throws NotificationException {
    try {
      consumer = session.createConsumer(ourQueue);

      connection.start();

      return consumer;
    } catch (final JMSException je) {
      throw new NotificationException(je);
    }
  }

  /**
   * @return next message
   * @throws NotificationException
   */
  public Message receive() throws NotificationException {
    try {
      return consumer.receive();
    } catch (final JMSException je) {
      throw new NotificationException(je);
    }
  }

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

  /* Get a logger for messages
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  private static Properties getPr() throws NotificationException {
    synchronized (lockit) {
      if (pr != null) {
        return pr;
      }

      InputStream is = null;

      try {
        sysProps = new CalSvcFactoryDefault().getSystemConfig()
                .getSystemProperties();

        /* Load properties file */

        pr = new Properties();

        if (!Util.isEmpty(sysProps.getSyseventsProperties())) {
          final StringBuilder sb = new StringBuilder();

          @SuppressWarnings("unchecked")
          final List<String> ps = sysProps.getSyseventsProperties();

          for (final String p: ps) {
            sb.append(p);
            sb.append("\n");
          }

          pr.load(new StringReader(sb.toString()));

          return pr;
        }

        /* Do it using the file */
        try {
          // The jboss?? way - should work for others as well.
          final ClassLoader cl = Thread.currentThread().getContextClassLoader();
          is = cl.getResourceAsStream(propertiesFile);
        } catch (final Throwable ignored) {}

        if (is == null) {
          // Try another way
          is = JmsConnectionHandler.class.getResourceAsStream(propertiesFile);
        }

        if (is == null) {
          throw new NotificationException("Unable to load properties file \"" +
                                          propertiesFile + "\"");
        }

        pr.load(is);

        return pr;
      } catch (final NotificationException cee) {
        throw cee;
      } catch (final Throwable t) {
        Logger.getLogger(JmsConnectionHandler.class).error("getEnv error", t);
        throw new NotificationException(t.getMessage());
      } finally {
        if (is != null) {
          try {
            is.close();
          } catch (final Throwable ignored) {}
        }
      }
    }
  }
}
