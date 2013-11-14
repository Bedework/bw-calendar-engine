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
package org.bedework.sysevents.monitor;

import org.bedework.calfacade.MonitorStat;
import org.bedework.sysevents.NotificationException;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.listeners.JmsSysEventListener;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author douglm
 *
 */
public class BwSysMonitor implements BwSysMonitorMBean {
  private transient Logger log;

  private DataCounts dataCounts = new DataCounts();

  private DataValues dataValues = new DataValues();

  private class BedeworkListener extends JmsSysEventListener {
    BedeworkListener() throws NotificationException {
      open(monitorQueueName);
    }

    @Override
    public void action(final SysEvent ev) throws NotificationException {
      if (ev == null) {
        return;
      }

      dataCounts.update(ev);

      dataValues.update(ev);
    }
  }

  private class ListenerThread extends Thread {
    @Override
    public void run() {
      try {
        BedeworkListener bwl = new BedeworkListener();
        bwl.process(false);
      } catch (NotificationException ne) {
        ne.printStackTrace();
        //error(ne);
      }
    }
  }

  private ListenerThread processor;

  public String getName() {
    /* This apparently must be the same as the name attribute in the
     * jboss service definition
     */
    return "org.bedework:service=BwSysMonitor";
  }

  /* an example say's we need this  - we'll see
  public MBeanInfo getMBeanInfo() throws Exception {
    InitialContext ic = new InitialContext();
    RMIAdaptor server = (RMIAdaptor) ic.lookup("jmx/rmi/RMIAdaptor");

    ObjectName name = new ObjectName(MBEAN_OBJ_NAME);

    // Get the MBeanInfo for this MBean
    MBeanInfo info = server.getMBeanInfo(name);
    return info;
  }
  */

  @Override
  public boolean isStarted() {
    return (processor != null) && processor.isAlive();
  }

  @Override
  public synchronized void start() {
    if (processor != null) {
      error("Already started");
      return;
    }

    info("************************************************************");
    info(" * Starting " + getName());
    info("************************************************************");

    processor = new ListenerThread();
    processor.start();
  }

  @Override
  public synchronized void stop() {
    if (processor == null) {
      error("Already stopped");
      return;
    }

    info("************************************************************");
    info(" * Stopping " + getName());
    info("************************************************************");

    processor.interrupt();
    try {
      processor.join();
    } catch (InterruptedException ie) {
    } catch (Throwable t) {
      error("Error waiting for processor termination");
      error(t);
    }

    processor = null;

    info("************************************************************");
    info(" * " + getName() + " terminated");
    info("************************************************************");
  }

  public List<String> showValues() {
    List<String> vals = new ArrayList<String>();

    dataCounts.getValues(vals);

    dataValues.getValues(vals);

    return vals;
  }

  public List<MonitorStat> getStats() {
    List<MonitorStat> stats = new ArrayList<MonitorStat>();

    dataCounts.getStats(stats);

    dataValues.getStats(stats);

    return stats;
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

  protected void error(final Throwable t) {
    getLogger().error(t);
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
