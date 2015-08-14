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
package org.bedework.calsvc;

import org.bedework.calfacade.configs.NotificationProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.http.BasicHttpClient;
import org.bedework.util.misc.Logged;
import org.bedework.util.misc.Util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

/** Handles interactions with the bedework notification engine.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class NotificationClient extends Logged {
  final NotificationProperties np;

  private static final ObjectMapper om = new ObjectMapper();

  private BasicHttpClient cl;

  /**
   * Constructor
   *
   */
  NotificationClient(final NotificationProperties np) {
    this.np = np;
  }

  private static class NotifyMessage {
    String system;
    String token;
    List<String> hrefs = new ArrayList<>();
  }

  private static class SubscribeMessage {
    String system;
    String token;
    String href;
    List<String> emailAddresses = new ArrayList<>();
  }

  void informNotifier(final String principalHref) throws CalFacadeException {
    final NotifyMessage nm = new NotifyMessage();

    nm.system = np.getNotifierId();
    nm.token = np.getNotifierToken();
    nm.hrefs.add(principalHref);

    sendRequest(nm, "notification/");
  }

  void subscribe(final String principalHref,
                 final List<String> emails) throws CalFacadeException {
    final SubscribeMessage sm = new SubscribeMessage();

    sm.system = np.getNotifierId();
    sm.token = np.getNotifierToken();
    sm.href= principalHref;
    sm.emailAddresses = emails;

    sendRequest(sm, "subscribe/");
  }

  void unsubscribe(final String principalHref,
                   final List<String> emails) throws CalFacadeException {
    final SubscribeMessage sm = new SubscribeMessage();

    sm.system = np.getNotifierId();
    sm.token = np.getNotifierToken();
    sm.href= principalHref;

    if (!Util.isEmpty(emails)) {
      sm.emailAddresses = emails;
    }

    sendRequest(sm, "unsubscribe/");
  }

  private void sendRequest(final Object req,
                           final String path) throws CalFacadeException {
    if (np.getNotifierURI() == null) {
      return;  // Assume not enabled
    }

    try {
      synchronized (this) {
        final BasicHttpClient cl = getClient();

        cl.setBaseURI(new URI(np.getNotifierURI()));

        final StringWriter sw = new StringWriter();

        om.writeValue(sw, req);

        final byte[] content = sw.toString().getBytes();

        final int status = cl.sendRequest("POST",
                                          path,
                                          null, // hdrs
                                          "application/json",
                                          content.length,
                                          content);

        if (status != HttpServletResponse.SC_OK) {
          warn("Unable to post notification");
        }
      }
    } catch (final Throwable t) {
      if (debug) {
        error(t);
      }
      error("Unable to contact notification engine " +
                    t.getLocalizedMessage());
    }
  }

  private BasicHttpClient getClient() throws CalFacadeException {
    if (cl != null) {
      return cl;
    }

    try {
      cl = new BasicHttpClient(30 * 1000,
                               false);  // followRedirects

      return cl;
    } catch (final Throwable t) {
      if (debug) {
        error(t);
      }
      throw new CalFacadeException(t);
    }
  }
}