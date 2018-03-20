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
import org.bedework.util.jmx.ConfBase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author douglm
 *
 */
public class BwSysMonitor extends ConfBase implements BwSysMonitorMBean {
  private final DataCounts dataCounts = new DataCounts();

  private final DataValues dataValues = new DataValues();

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
      try (final BedeworkListener bwl = new BedeworkListener()) {
        setStatus(statusRunning);
        bwl.process(false);
      } catch (final NotificationException ne) {
        ne.printStackTrace();
        //error(ne);
      }
      setStatus(statusStopped);
    }
  }

  private ListenerThread processor;

  @Override
  public String getServiceName() {
    return serviceName;
  }

  @Override
  public boolean isRunning() {
    return (processor != null) && processor.isAlive();
  }

  @Override
  public String loadConfig() {
    return "Ok";
  }

  @Override
  public synchronized void start() {
    if (processor != null) {
      error("Already started");
      return;
    }

    setStatus(statusStopped);

    info("************************************************************");
    info(" * Starting " + serviceName);
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
    info(" * Stopping " + serviceName);
    info("************************************************************");

    processor.interrupt();
    try {
      processor.join();
    } catch (final InterruptedException ignored) {
    } catch (final Throwable t) {
      error("Error waiting for processor termination");
      error(t);
    }

    setStatus(statusStopped);
    processor = null;

    info("************************************************************");
    info(" * " + serviceName + " terminated");
    info("************************************************************");
  }

  public List<String> showValues() {
    final List<String> vals = new ArrayList<>();

    dataCounts.getValues(vals);

    dataValues.getValues(vals);

    return vals;
  }

  public List<MonitorStat> getStats() {
    final List<MonitorStat> stats = new ArrayList<>();

    dataCounts.getStats(stats);

    dataValues.getStats(stats);

    return stats;
  }
}
