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

import org.bedework.sysevents.NotificationException;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.args.Args;

/** Listener class which logs system events sent via JMS.
 *
 * @author Mike Douglass
 */
public class LogListener extends JmsSysEventListener {
  private boolean debug;

  /**
   */
  public LogListener() {
  }

  @Override
  public void action(final SysEvent ev) throws NotificationException {
    if (ev == null) {
      return;
    }

    try {
      getLogger().info(ev.toString());
    } catch (Throwable t) {
      throw new NotificationException(t);
    }
  }

  void listen() throws Throwable {
    open(syseventsLogQueueName);

    process(false);
  }

  boolean processArgs(final Args args) throws Throwable {
    if (args == null) {
      return true;
    }

    while (args.more()) {
      if (args.ifMatch("")) {
        continue;
      }

      if (args.ifMatch("-debug")) {
        debug = true;
      } else if (args.ifMatch("-ndebug")) {
        debug = false;
      } else if (args.ifMatch("-appname")) {
        args.next(); // Not used at the moment
      } else {
        error("Illegal argument: " + args.current());
        usage();
        return false;
      }
    }

    return true;
  }

  void usage() {
    System.out.println("Usage:");
    System.out.println("args   -debug");
    System.out.println("       -ndebug");
    System.out.println("       -appname <name>");
    System.out.println("");
  }

  /**
   * @param args
   */
  public static void main(final String[] args) {
    try {
      LogListener ll = new LogListener();

      if (!ll.processArgs(new Args(args))) {
        return;
      }

      if (ll.debug) {
        ll.trace("About to start process");
      }

      ll.listen();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }
}
