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
package org.bedework.calsvc.scheduling.hosts;

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.scheduling.hosts.Response.ResponseElement;
import org.bedework.calsvci.CalSvcI;
import org.bedework.convert.IcalTranslator;
import org.bedework.convert.Icalendar;
import org.bedework.util.calendar.IcalendarUtil;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.XmlUtil;
import org.bedework.util.xml.tagdefs.CaldavTags;
import org.bedework.util.xml.tagdefs.IscheduleTags;
import org.bedework.util.xml.tagdefs.WebdavTags;

import net.fortuna.ical4j.model.Calendar;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.security.PrivateKey;
import java.util.Collection;
import java.util.Iterator;

import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.bedework.util.http.HttpUtil.findMethod;
import static org.bedework.util.http.HttpUtil.getFirstHeaderValue;
import static org.bedework.util.http.HttpUtil.getStatus;
import static org.bedework.util.http.HttpUtil.setContent;

/** Handle interactions with ischedule servers.
 *
 * @author Mike Douglass
 */
public class IscheduleClient implements Logged {
  private final CalSvcI svci;

  private final transient IcalTranslator trans;

  private static CloseableHttpClient cio;

  private PrivateKeys pkeys;

  private final String domain;

  /** Provided to the client class to allow access to private key.
   *
   */
  public static abstract class PrivateKeys {
    /** Fetch a private key for a request to the given domain and service.
     * The response will be null if there is no key and the interaction should
     * be unsigned. An exception will be thrown if we should not attempt to
     * communicate with the site or for some other error.
     *
     * @param domain
     * @param service
     * @return key or null for unsigned.
     */
    public abstract PrivateKey getKey(final String domain,
                                      final String service);
  }

  /** Constructor
   *
   * @param trans
   * @param pkeys - null for no signing
   * @param domain
   */
  public IscheduleClient(final CalSvcI svci,
                         final IcalTranslator trans,
                         final PrivateKeys pkeys,
                         final String domain) {
    this.svci = svci;
    this.trans = trans;
    this.pkeys = pkeys;
    this.domain = domain;

    if (cio != null) {
      return;
    }
    synchronized (this) {
      if (cio != null) {
        return;
      }

      final HttpClientBuilder clb = HttpClients.custom();

      /* Might need this for authenticated ischedule
      if (user != null) {
        final CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                new UsernamePasswordCredentials(user,
                                                pw));

        clb.setDefaultCredentialsProvider(credsProvider);
      }
      */

      cio = clb.create().disableRedirectHandling().build();
    }
  }

  /** Get the freebusy for the recipients specified in the event object,
   * e.g. start, end, organizer etc.
   *
   * @param hi
   * @param ei
   * @return Response
   * @throws CalFacadeException
   */
  public Response getFreeBusy(final HostInfo hi,
                              final EventInfo ei) {
    discover(hi);
    final IscheduleOut iout = makeFreeBusyRequest(hi, ei);

    final Response resp = new Response();

    send(iout, hi, resp);

    if (resp.getResponseCode() != HttpServletResponse.SC_OK) {
      return resp;
    }

    /* We expect something like...
       *
       *    <C:schedule-response xmlns:D="DAV:"
                  xmlns:C="urn:ietf:params:xml:ns:caldav">
     <C:response>
       <C:recipient>mailto:bernard@example.com</C:recipient>
       <C:request-status>2.0;Success</C:request-status>
       <C:calendar-data>BEGIN:VCALENDAR
     VERSION:2.0
     PRODID:-//Example Corp.//CalDAV Server//EN
     METHOD:REPLY
     BEGIN:VFREEBUSY
     DTSTAMP:20040901T200200Z
     ORGANIZER:mailto:lisa@example.com
     DTSTART:20040902T000000Z
     DTEND:20040903T000000Z
     UID:34222-232@example.com
     ATTENDEE;CN=Bernard Desruisseaux:mailto:bernard@
      example.com
     FREEBUSY;FBTYPE=BUSY-UNAVAILABLE:20040902T000000Z/
      20040902T090000Z,20040902T170000Z/20040903T000000Z
     END:VFREEBUSY
     END:VCALENDAR
     </C:calendar-data>
     </C:response>
     <C:response>
       <C:recipient>mailto:cyrus@example.com</C:recipient>
       <C:request-status>2.0;Success</C:request-status>
       <C:calendar-data>BEGIN:VCALENDAR
     VERSION:2.0
     PRODID:-//Example Corp.//CalDAV Server//EN
     METHOD:REPLY
     BEGIN:VFREEBUSY
     DTSTAMP:20040901T200200Z
     ORGANIZER:mailto:lisa@example.com
     DTSTART:20040902T000000Z
     DTEND:20040903T000000Z
     UID:34222-232@example.com
     ATTENDEE;CN=Cyrus Daboo:mailto:cyrus@example.com
     FREEBUSY;FBTYPE=BUSY-UNAVAILABLE:20040902T000000Z/
      20040902T090000Z,20040902T170000Z/20040903T000000Z
     FREEBUSY;FBTYPE=BUSY:20040902T120000Z/20040902T130000Z
     END:VFREEBUSY
     END:VCALENDAR
     </C:calendar-data>
     </C:response>
     </C:schedule-response>
       */

    return resp;
  }

  /** Schedule a meeting with the recipients specified in the event object,
   *
   * @param hi
   * @param ei
   * @return Response
   * @throws CalFacadeException
   */
  public Response scheduleMeeting(final HostInfo hi,
                                  final EventInfo ei) {
    discover(hi);
    final IscheduleOut iout = makeMeetingRequest(hi, ei);

    final Response resp = new Response();

    send(iout, hi, resp);

    if (resp.getResponseCode() != HttpServletResponse.SC_OK) {
      return resp;
    }

    return resp;
  }

  /** See if we have a url for the service. If not discover the real one.
   *
   * @param hi
   */
  private void discover(final HostInfo hi) {
    if (hi.getIScheduleUrl() != null) {
      return;
    }

    /* For the moment we'll try to find it via .well-known. We may have to
     * use DNS SRV lookups
     */
//    String domain = hi.getHostname();

  //  int lpos = domain.lastIndexOf(".");
    //int lpos2 = domain.lastIndexOf(".", lpos - 1);

//    if (lpos2 > 0) {
  //    domain = domain.substring(lpos2 + 1);
    //}

    int rcode = 0;

    try {
      /*
      // XXX ioptest fix - remove
      String url;
      if ("example.com".equals(hi.getHostname())) {
        url = "http://" + hi.getHostname() + ":8008/.well-known/ischedule";
      } else if ("ken.name".equals(hi.getHostname())) {
        url = "http://" + hi.getHostname() + ":8008/.well-known/ischedule";
      } else {
        url = "https://" + hi.getHostname() + "/.well-known/ischedule";
      }
      */

      final String scheme;
      final int port;

      if (hi.getPort() == 0) {
        port = 80;
      } else {
        port = hi.getPort();
      }

      if (hi.getSecure()) {
        scheme = "https://";
      } else {
        scheme = "http://";
      }

      URI uri = new URIBuilder()
              .setScheme(scheme)
              .setHost(hi.getHostname())
              .setPort(port)
              .setPath(".well-known/ischedule")
              .addParameter("action", "capabilities")
              .build();

      final HttpRequestBase req = findMethod("GET", uri);

      for (int redirects = 0; redirects < 10; redirects++) {
        try (final CloseableHttpResponse resp = cio.execute(req)) {
          rcode = getStatus(resp);

          if ((rcode == HttpServletResponse.SC_MOVED_PERMANENTLY) ||
                  (rcode == HttpServletResponse.SC_MOVED_TEMPORARILY) ||
                  (rcode == HttpServletResponse.SC_TEMPORARY_REDIRECT)) {
            //boolean permanent = rcode == HttpServletResponse.SC_MOVED_PERMANENTLY;

            final String location =
                    getFirstHeaderValue(resp,
                                        "location");
            if (location != null) {
              if (debug()) {
                debug("Got redirected to " + location +
                              " from " + uri);
              }

              final int qpos = location.indexOf("?");

              final String noreq;
              if (qpos < 0) {
                noreq = location;
              } else {
                noreq = location.substring(0, qpos);
              }
              uri = new URIBuilder(noreq)
                      .addParameter("action", "capabilities")
                      .build();

              // Try again
              continue;
            }
          }
        }

        if (rcode != HttpServletResponse.SC_OK) {
            // The response is invalid and did not provide the new location for
            // the resource.  Report an error or possibly handle the response
            // like a 404 Not Found error.
          if (debug()) {
            error("Got response " + rcode +
                  ", host " + hi.getHostname() +
                  " and url " + uri);

            /*
            if (cio.getResponseContentLength() != 0) {
              InputStream is = cio.getResponseBodyAsStream();

              LineNumberReader in
                      = new LineNumberReader(new InputStreamReader(is));

              error("Content: ==========================");
              while (true) {
                String l = in.readLine();
                if (l == null) {
                  break;
                }

                error(l);
              }
              error("End content: ==========================");
            }*/
          }

          throw new CalFacadeException("Got response " + rcode +
                                       ", host " + hi.getHostname() +
                                       " and url " + uri);
        }

        /* Should have a capabilities record. */

        hi.setIScheduleUrl(uri.toString());
        return;
      }

      if (debug()) {
        error("Too many redirects: Got response " + rcode +
              ", host " + hi.getHostname() +
              " and url " + uri);
      }

      throw new CalFacadeException("Too many redirects on " + uri);
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }

      throw new CalFacadeException(t);
    }
  }


  /** Parse the content, and return the DOM representation.
   *
   * @param resp       response from server
   * @return Document  Parsed body or null for no body
   * @exception CalFacadeException Some error occurred.
   */
  private Document parseContent(final Response resp) {
    try {
      final CloseableHttpResponse hresp = resp.getHttpResponse();

      final HttpEntity entity = hresp.getEntity();
      final long len = entity.getContentLength();
      if (len == 0) {
        return null;
      }

      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

      final DocumentBuilder builder = factory.newDocumentBuilder();

      final InputStream in = entity.getContent();

      return builder.parse(new InputSource(new InputStreamReader(in)));
    } catch (SAXException e) {
      throw new CalFacadeException(e.getMessage());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void parseResponse(final HostInfo hi,
                             final Response resp) {
    try {
      final Document doc = parseContent(resp);
      if (doc == null){
        throw new CalFacadeException(CalFacadeException.badResponse);
      }

      final QName sresponseTag;
      final QName responseTag;
      final QName recipientTag;
      final QName requestStatusTag;
      final QName calendarDataTag;
      final QName errorTag;
      final QName descriptionTag;

      if (hi.getSupportsISchedule()) {
        sresponseTag = IscheduleTags.scheduleResponse;
        responseTag = IscheduleTags.response;
        recipientTag = IscheduleTags.recipient;
        requestStatusTag = IscheduleTags.requestStatus;
        calendarDataTag = IscheduleTags.calendarData;
        errorTag = IscheduleTags.error;
        descriptionTag = IscheduleTags.responseDescription;
      } else {
        sresponseTag = CaldavTags.scheduleResponse;
        responseTag = CaldavTags.response;
        recipientTag = CaldavTags.recipient;
        requestStatusTag = CaldavTags.requestStatus;
        calendarDataTag = CaldavTags.calendarData;
        errorTag = WebdavTags.error;
        descriptionTag = WebdavTags.responseDescription;
      }

      final Element root = doc.getDocumentElement();

      if (!XmlUtil.nodeMatches(root, sresponseTag)) {
        throw new CalFacadeException(CalFacadeException.badResponse);
      }

      for (final Element el: getChildren(root)) {
        final ResponseElement fbel = new ResponseElement();

        if (!XmlUtil.nodeMatches(el, responseTag)) {
          throw new CalFacadeException(CalFacadeException.badResponse);
        }

        /* ========================================================
        11.2.  CALDAV/ISCHEDULE:response XML Element

        Name:  response
        Namespace:  urn:ietf:params:xml:ns:caldav   or
                    urn:ietf:params:xml:ns:ischedule   or

        Purpose:  Contains a single response for a POST method request.
        Description:  See Section 6.1.4.
        Definition:

        <!ELEMENT response (recipient,
                            request-status,
                            calendar-data?,
                            error?,
                            response-description?)>
           ================================================================ */

        final Iterator<Element> respels = getChildren(el).iterator();

        Element respel = respels.next();

        if (!XmlUtil.nodeMatches(respel, recipientTag)) {
          throw new CalFacadeException(CalFacadeException.badResponse);
        }

        fbel.setRecipient(getElementContent(respel));

        respel = respels.next();

        if (!XmlUtil.nodeMatches(respel, requestStatusTag)) {
          throw new CalFacadeException(CalFacadeException.badResponse);
        }

        fbel.setReqStatus(getElementContent(respel));

        if (respels.hasNext()) {
          respel = respels.next();

          if (XmlUtil.nodeMatches(respel, calendarDataTag)) {
            final String calData = getElementContent(respel);

            final Reader rdr = new StringReader(calData);
            final Icalendar ical = trans.fromIcal(null, rdr);

            fbel.setCalData(ical.getEventInfo());
          } else if (XmlUtil.nodeMatches(respel, errorTag)) {
            fbel.setDavError(respel.getFirstChild().getLocalName());
          } else if (XmlUtil.nodeMatches(respel, descriptionTag)) {
            // XXX Not processed yet
          } else {
            throw new CalFacadeException(CalFacadeException.badResponse);
          }
        }

        resp.addResponse(fbel);
      }
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }

      resp.setException(t);
    }
  }

  /**
   * @param iout
   * @param hi
   * @param resp Response
   * @throws CalFacadeException
   */
  public void send(final IscheduleOut iout,
                   final HostInfo hi,
                   final Response resp) {
    try {
      /* We may have to rediscover and retry. */
      for (int failures = 0; failures < 10; failures++) {
        //PrivateKey key = pkeys.getKey(url, "isched");

        //if (key != null) {
        //  iout.sign(hi, key);
        //}

        resp.setHostInfo(hi);

        final URI uri = new URI(hi.getIScheduleUrl());
        final HttpRequestBase req = findMethod(iout.getMethod(), uri);

        if (req == null) {
          throw new CalFacadeException("No method " + iout.getMethod());
        }

        if (!Util.isEmpty(iout.getHeaders())) {
          for (final Header hdr: iout.getHeaders()) {
            req.addHeader(hdr);
          }
        }

        /* Send the ischedule request. If we get a redirect from the other end
         * we need to do the discovery thing again.
         */

        setContent(req,
                   iout.getContentBytes(),
                   iout.getContentType());

        try (final CloseableHttpResponse hresp = cio.execute(req)) {
          final int rcode = getStatus(hresp);

          if (rcode != HttpServletResponse.SC_OK) {
            error("Got response " + resp.getResponseCode() +
                          ", host " + hi.getHostname() +
                          " and url " + hi.getIScheduleUrl());

//          hi.setIScheduleUrl(null);
            discover(hi);
            continue;
          }

          resp.setHttpResponse(hresp);
          parseResponse(hi, resp);

          return;
        }
      }
    } catch (final NoHttpResponseException nhre) {
      resp.setNoResponse(true);
    } catch (final Throwable t) {
      resp.setException(t);
      throw new CalFacadeException(t);
    }
  }

  private IscheduleOut makeFreeBusyRequest(final HostInfo hi,
                                           final EventInfo ei) {
    final BwEvent ev = ei.getEvent();

    //if (!iSchedule && (recipients.size() > 1)) {
    //  throw new CalFacadeException(CalFacadeException.schedulingBadRecipients);
    //}

    final IscheduleOut iout = makeIout(hi, "text/calendar", "POST");

    addOriginator(iout, ev);
    addRecipients(iout, ev);

    final Calendar cal = trans.toIcal(ei, ev.getScheduleMethod());

    final StringWriter sw = new StringWriter();
    IcalendarUtil.writeCalendar(cal, sw);

    iout.addContentLine(sw.toString());

    return iout;
  }

  private void addOriginator(final IscheduleOut iout,
                            final BwEvent ev) {
    if (ev.getOriginator() != null) {
      iout.addHeader("Originator", ev.getOriginator());
      return;
    }

    iout.addHeader("Originator", ev.getSchedulingInfo()
                                   .getSchedulingOwner()
                                   .getCalendarAddress());
  }

  private void addRecipients(final IscheduleOut iout,
                             final BwEvent ev) {
    final Collection<String> recipients = ev.getRecipients();

    for (final String recip: recipients) {
      iout.addHeader("Recipient", recip);
    }
  }

  private IscheduleOut makeMeetingRequest(final HostInfo hi,
                                          final EventInfo ei) {
    final BwEvent ev = ei.getEvent();

    final IscheduleOut iout = makeIout(hi, "text/calendar", "POST");

    addOriginator(iout, ev);
    addRecipients(iout, ev);

    final Calendar cal = trans.toIcal(ei, ev.getScheduleMethod());

    final StringWriter sw = new StringWriter();
    IcalendarUtil.writeCalendar(cal, sw);

    iout.addContentLine(sw.toString());

    return iout;
  }

  private IscheduleOut makeIout(final HostInfo hi,
                                final String contentType,
                                final String method) {
    final IscheduleOut iout = new IscheduleOut(svci, domain);

    iout.setContentType(contentType);
    iout.setMethod(method);

    return iout;
  }

  /* ==============================================================
   *                   XmlUtil wrappers
   * ============================================================== */

  protected Collection<Element> getChildren(final Node nd) {
    try {
      return XmlUtil.getElements(nd);
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }

      throw new CalFacadeException(CalFacadeException.badResponse);
    }
  }

  protected String getElementContent(final Element el) {
    try {
      return XmlUtil.getElementContent(el);
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }

      throw new CalFacadeException(CalFacadeException.badResponse);
    }
  }

  /* =============================================================
   *                   Logged methods
   * ============================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
