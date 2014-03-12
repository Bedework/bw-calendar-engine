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
package org.bedework.calsvc.jmx;

import org.bedework.caldav.util.sharing.AccessType;
import org.bedework.caldav.util.sharing.SetType;
import org.bedework.caldav.util.sharing.ShareType;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.util.jmx.ConfBase;

/**
 * @author douglm
 *
 */
public class NotificationConf extends ConfBase<NotificationPropertiesImpl>
        implements NotificationConfMBean {
  /** Name of the property holding the location of the config data */
  public static final String confuriPname = "org.bedework.bwengine.confuri";

  private CalSvcI svci;

  /**
   */
  public NotificationConf() {
    super(getServiceName("notifications"));

    setConfigName("notifications");

    setConfigPname(confuriPname);
  }

  /**
   * @param name the undecorated name of the service
   * @return service name for the mbean with this name
   */
  public static String getServiceName(final String name) {
    return "org.bedework.bwengine:service=" + name;
  }

  /* ========================================================================
   * Attributes
   * ======================================================================== */

  @Override
  public void setOutboundEnabled(final boolean val) {
    getConfig().setOutboundEnabled(val);
  }

  @Override
  public boolean getOutboundEnabled() {
    return getConfig().getOutboundEnabled();
  }

  @Override
  public void setNotifierId(final String val) {
    getConfig().setNotifierId(val);
  }

  @Override
  public String getNotifierId() {
    return getConfig().getNotifierId();
  }

  @Override
  public void setNotifierToken(final String val) {
    getConfig().setNotifierToken(val);
  }

  @Override
  public String getNotifierToken() {
    return getConfig().getNotifierToken();
  }

  @Override
  public void setNotificationDirHref(final String val) {
    getConfig().setNotificationDirHref(val);
  }

  @Override
  public String getNotificationDirHref() {
    return getConfig().getNotificationDirHref();
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public String sendInvite(final String cua,
                           final String href) {
    try {
      final ShareType share = new ShareType();

      final SetType set = new SetType();

      set.setHref(cua);
      set.setSummary("Test Collection");

      final AccessType at = new AccessType();
      at.setRead(true);
      set.setAccess(at);

      share.getSet().add(set);

      try (CalSvcI svci = getSvci(getNotifierId())) {
        final BwCalendar col = svci.getCalendarsHandler().get(href);

        if (col == null) {
          return "No such collection";
        }

        svci.getSharingHandler().share(col, share);
      }

      svci.endTransaction();

      return "ok";
    } catch (final Throwable t) {
      error(t);
      return t.getLocalizedMessage();
    }
  }

  @Override
  public String loadConfig() {
    return loadConfig(NotificationPropertiesImpl.class);
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /** Get an svci object and return it. Also embed it in this object.
   *
   * @param account of user to run as
   * @return svci object
   * @throws org.bedework.calfacade.exc.CalFacadeException
   */
  private CalSvcI getSvci(final String account) throws CalFacadeException {
    if ((svci != null) && svci.isOpen()) {
      return svci;
    }

    if (svci == null) {
      final CalSvcIPars pars = CalSvcIPars.getServicePars(account,
                                                          false,   // publicAdmin
                                                          true);   // Allow super user
      svci = new CalSvcFactoryDefault().getSvc(pars);
    }

    svci.open();
    svci.beginTransaction();

    return svci;
  }
}
