/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calsvc.jmx;

import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

import java.util.Set;
import java.util.TreeSet;

/**
 * User: mike Date: 7/10/20 Time: 17:25
 */
public class AutoKiller implements Logged {
  final SystemProperties sysProps;

  private String rootUser;
  private CalSvcI svci;

  private boolean running;

  private static final Set<String> dontKill = new TreeSet<>();

  static {
    dontKill.add(CalSvcIPars.logIdIndexer);
    dontKill.add(CalSvcIPars.logIdRestore);
  }

  private class AutoKillThread extends Thread {
    int ctr;
    boolean showedTrace;

    int terminated;

    int failedTerminations;

    AutoKillThread() {
      super("Bedework AutoKill");
    }

    @Override
    public void run() {
      while (running) {
        final int waitMins = sysProps.getAutoKillMinutes();

        try {
          if (waitMins == 0) {
            // Display every 10 mins
            ctr++;
            if (ctr == 10) {
              info(listOpenIfs());
              ctr = 0;
            }
          } else {
            if (debug()) {
              debug("About to check interfaces");
            }

            checkIfs(waitMins * 60);
          }
        } catch (final Throwable t) {
          if (!showedTrace) {
            error(t);
            showedTrace = true;
          } else {
            error(t.getMessage());
          }
        }

        if (running) {
          // Wait a bit before restarting
          try {
            synchronized (this) {
              final int wait;

              if (waitMins == 0) {
                // Autokill disabled - go to sleep for a minute
                wait = 60 * 1000;
              } else {
                wait = waitMins * 60 * 1000;
              }
              this.wait(wait);
            }
          } catch (final Throwable t) {
            error(t.getMessage());
          }
        }
      }
    }

    private void checkIfs(final int waitSecs) {
      try {
        getSvci();

        if (svci != null) {
          for (final IfInfo ifInfo: svci.getActiveIfInfos()) {
            if (ifInfo.getSeconds() > waitSecs) {
              if (ifInfo.getDontKill()) {
                warn("Skipping dontKill task: " + ifInfo.getId());
                continue;
              }

              if (dontKill.contains(ifInfo.getLogid())) {
                warn("Skipping long running task: " + ifInfo.getId());
                continue;
              }

              try {
                if (debug()) {
                  debug("About to shut down interface " +
                                ifInfo.getId());
                }
                svci.kill(ifInfo);
                terminated++;
              } catch (final Throwable t) {
                warn("Failed to terminate " +
                             ifInfo.getId());
                error(t);
                failedTerminations++;
              }
            }
          }
        }
      } catch (final Throwable t) {
        error(t);
      } finally {
        closeSvci();
      }
    }
  }

  private AutoKillThread autoKiller;

  AutoKiller(final SystemProperties sysProps) {
    this.sysProps = sysProps;
    final String[] rootUsers = sysProps.getRootUsers().split(",");

    if ((rootUsers.length > 0) && (rootUsers[0] != null)) {
      rootUser = rootUsers[0];
    }
  }

  void start() {
    autoKiller = new AutoKillThread();
    autoKiller.start();
  }

  public int getTerminated() {
    return autoKiller.terminated;
  }

  public int getFailedTerminations() {
    return autoKiller.failedTerminations;
  }

  public String listOpenIfs() {
    final StringBuilder sb = new StringBuilder();

    try {
      getSvci();

      if (svci != null) {
        for (final IfInfo ifInfo: svci.getActiveIfInfos()) {
          sb.append(ifInfo.getId());
          sb.append("\t");
          sb.append(ifInfo.getLastStateTime());
          sb.append("\t");
          sb.append(ifInfo.getState());
          sb.append("\t");
          sb.append(ifInfo.getSeconds());
          sb.append("\n");
        }
      }
    } catch (final Throwable t) {
      error(t);
    } finally {
      closeSvci();
    }

    return sb.toString();
  }

  /** Get an svci object and return it. Also embed it in this object.
   *
   * @return svci object
   * @throws CalFacadeException
   */
  private CalSvcI getSvci() throws CalFacadeException {
    if ((svci != null) && svci.isOpen()) {
      return svci;
    }

    final CalSvcIPars pars = CalSvcIPars.getServicePars("AutoKiller",
                                                        rootUser,
                                                        true,   // publicAdmin
                                                        true);   // Allow super user
    svci = new CalSvcFactoryDefault().getSvc(pars);

    svci.open();
    svci.beginTransaction();

    return svci;
  }

  /**
   */
  private void closeSvci() {
    if ((svci == null) || !svci.isOpen()) {
      return;
    }

    try {
      svci.endTransaction();
    } catch (final Throwable t) {
      try {
        svci.close();
      } catch (final Throwable ignored) {
      }
    }

    try {
      svci.close();
    } catch (final Throwable ignored) {
    }
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
