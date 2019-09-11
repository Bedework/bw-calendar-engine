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
package org.bedework.calsvc.notifications;

import org.bedework.calfacade.configs.NotificationProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.http.PooledHttpClient;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.StringWriter;
import java.net.URI;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

/** Handles interactions with the bedework notification engine.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
public class NotificationClient implements Logged {
  final NotificationProperties np;

  private static final ObjectMapper om = new ObjectMapper();

  private PooledHttpClient cl;

  /**
   * Constructor
   *
   */
  public NotificationClient(final NotificationProperties np) {
    this.np = np;
  }

  /**
   *
   * @param principalHref owner of changed notifications
   */
  public void informNotifier(final String principalHref,
                             final String resourceName) {
    final NotifyMessage nm = new NotifyMessage(np.getNotifierId(),
                                               np.getNotifierToken());

    nm.setHref(principalHref);
    nm.setResourceName(resourceName);

    sendRequest(nm, "notification/");
  }

  /**
   *
   * @param principalHref identify who
   * @param emails non-empty list
   * @param userToken per-user token
   */
  public void subscribe(final String principalHref,
                        final List<String> emails,
                        final String userToken) {
    final SubscribeMessage sm =
            new SubscribeMessage(np.getNotifierId(),
                                 np.getNotifierToken(),
                                 userToken,
                                 principalHref,
                                 emails);

    sendRequest(sm, "subscribe/");
  }

  /**
   *
   * @param principalHref identify who
   * @param emails null to remove entire subscription
   */
  public void unsubscribe(final String principalHref,
                   final List<String> emails) {
    final SubscribeMessage sm = new SubscribeMessage(np.getNotifierId(),
                                                     np.getNotifierToken(),
                                                     null,
                                                     principalHref,
                                                     emails);

    sendRequest(sm, "unsubscribe/");
  }

  private void sendRequest(final Object req,
                           final String path) {
    if (np.getNotifierURI() == null) {
      return;  // Assume not enabled
    }

    synchronized (this) {
      try {
        final StringWriter sw = new StringWriter();

        om.writeValue(sw, req);

        final int status = cl.postJson(path,
                                       sw.toString());

        if (status != HttpServletResponse.SC_OK) {
          warn("Unable to post notification");
        }
      } catch (final Throwable t) {
        if (debug()) {
          error(t);
        }
        error("Unable to contact notification engine " +
                      t.getLocalizedMessage());
      } finally {
        try {
          getClient().release();
        } catch (final Throwable t) {
          warn("Error on close " +
                       t.getLocalizedMessage());
        }
      }
    }
  }

  private PooledHttpClient getClient() throws CalFacadeException {
    if (cl != null) {
      return cl;
    }

    try {
      cl = new PooledHttpClient(new URI(np.getNotifierURI()));

      return cl;
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }
      throw new CalFacadeException(t);
    }
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
