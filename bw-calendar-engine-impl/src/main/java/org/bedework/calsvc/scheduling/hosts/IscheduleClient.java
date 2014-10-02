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
import org.bedework.icalendar.IcalTranslator;
import org.bedework.icalendar.Icalendar;
import org.bedework.util.http.BasicHttpClient;
import org.bedework.util.xml.XmlUtil;
import org.bedework.util.xml.tagdefs.CaldavTags;
import org.bedework.util.xml.tagdefs.IscheduleTags;
import org.bedework.util.xml.tagdefs.WebdavTags;

import net.fortuna.ical4j.model.Calendar;
import org.apache.http.Header;
import org.apache.http.NoHttpResponseException;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.security.PrivateKey;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Handle interactions with ischedule servers.
 *
 * @author Mike Douglass
 */
public class IscheduleClient {
  private boolean debug;

  private transient Logger log;

  private transient IcalTranslator trans;

  /* There is one entry per host + port. Because we are likely to make a number
   * of calls to the same host + port combination it makes sense to preserve
   * the objects between calls.
   */
  private HashMap<String, BasicHttpClient> cioTable = new HashMap<>();

  private PrivateKeys pkeys;

  private String domain;

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
     * @throws CalFacadeException
     */
    public abstract PrivateKey getKey(final String domain,
                                      final String service) throws CalFacadeException;
  }

  /** Constructor
   *
   * @param trans
   * @param pkeys - null for no signing
   * @param domain
   */
  public IscheduleClient(final IcalTranslator trans,
                         final PrivateKeys pkeys,
                         final String domain) {
    this.trans = trans;
    this.pkeys = pkeys;
    this.domain = domain;
    debug = getLogger().isDebugEnabled();
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
                              final EventInfo ei) throws CalFacadeException {
    discover(hi);
    IscheduleOut iout = makeFreeBusyRequest(hi, ei);

    Response resp = new Response();

    try {
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

      parseResponse(hi, resp);

      return resp;
    } finally {
      try {
        if (resp.getClient() != null) {
          resp.getClient().release();
        }
      } catch (Throwable t) {
      }
    }
  }

  /** Schedule a meeting with the recipients specified in the event object,
   *
   * @param hi
   * @param ei
   * @return Response
   * @throws CalFacadeException
   */
  public Response scheduleMeeting(final HostInfo hi,
                                  final EventInfo ei) throws CalFacadeException {
    discover(hi);
    IscheduleOut iout = makeMeetingRequest(hi, ei);

    Response resp = new Response();

    try {
      send(iout, hi, resp);

      if (resp.getResponseCode() != HttpServletResponse.SC_OK) {
        return resp;
      }

      parseResponse(hi, resp);

      return resp;
    } finally {
      try {
        if (resp.getClient() != null) {
          resp.getClient().release();
        }
      } catch (Throwable t) {
      }
    }
  }

  /** See if we have a url for the service. If not discover the real one.
   *
   * @param hi
   */
  private void discover(final HostInfo hi) throws CalFacadeException {
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

    BasicHttpClient cio = null;

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

      String scheme;
      String port;

      if (hi.getPort() == 0) {
        port = "";
      } else {
        port = ":" + hi.getPort();
      }

      if (hi.getSecure()) {
        scheme = "https://";
      } else {
        scheme = "http://";
      }

      String url = scheme + hi.getIScheduleUrl() + port + "/.well-known/ischedule";

      cio = getCio(url);

      for (int redirects = 0; redirects < 10; redirects++) {
        rcode = cio.sendRequest("GET",
                                url + "?action=capabilities",
                                null,
                                "application/xml",
                                0,
                                null);

        if ((rcode == HttpServletResponse.SC_MOVED_PERMANENTLY) ||
            (rcode == HttpServletResponse.SC_MOVED_TEMPORARILY) ||
            (rcode == HttpServletResponse.SC_TEMPORARY_REDIRECT)) {
          //boolean permanent = rcode == HttpServletResponse.SC_MOVED_PERMANENTLY;

          Header locationHeader = cio.getFirstHeader("location");
          if (locationHeader != null) {
            if (debug) {
              debugMsg("Got redirected to " + locationHeader.getValue() +
                       " from " + url);
            }

            String newLoc = locationHeader.getValue();
            int qpos = newLoc.indexOf("?");

            cioTable.remove(url);

            if (qpos < 0) {
              url = newLoc;
            } else {
              url = newLoc.substring(0, qpos);
            }

            cio.release();

            // Try again
            continue;
          }
        }

        if (rcode != HttpServletResponse.SC_OK) {
            // The response is invalid and did not provide the new location for
            // the resource.  Report an error or possibly handle the response
            // like a 404 Not Found error.
          if (debug) {
            error("Got response " + rcode +
                  ", host " + hi.getHostname() +
                  " and url " + url);

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
            }
          }

          throw new CalFacadeException("Got response " + rcode +
                                       ", host " + hi.getHostname() +
                                       " and url " + url);
        }

        /* Should have a capabilities record. */

        hi.setIScheduleUrl(url);
        return;
      }

      if (debug) {
        error("Too many redirects: Got response " + rcode +
              ", host " + hi.getHostname() +
              " and url " + url);
      }

      throw new CalFacadeException("Too many redirects on " + url);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      if (debug) {
        error(t);
      }

      throw new CalFacadeException(t);
    } finally {
      try {
        if (cio != null) {
          cio.release();
        }
      } catch (Throwable t) {
      }
    }
  }


  /** Parse the content, and return the DOM representation.
   *
   * @param resp       response from server
   * @return Document  Parsed body or null for no body
   * @exception CalFacadeException Some error occurred.
   */
  private Document parseContent(final Response resp) throws CalFacadeException {
    try {
      BasicHttpClient cl = resp.getClient();

      long len = cl.getResponseContentLength();
      if (len == 0) {
        return null;
      }

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);

      DocumentBuilder builder = factory.newDocumentBuilder();

      InputStream in = cl.getResponseBodyAsStream();

      return builder.parse(new InputSource(new InputStreamReader(in)));
    } catch (SAXException e) {
      throw new CalFacadeException(e.getMessage());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void parseResponse(final HostInfo hi,
                             final Response resp) throws CalFacadeException {
    try {
      Document doc = parseContent(resp);
      if (doc == null){
        throw new CalFacadeException(CalFacadeException.badResponse);
      }

      QName sresponseTag;
      QName responseTag;
      QName recipientTag;
      QName requestStatusTag;
      QName calendarDataTag;
      QName errorTag;
      QName descriptionTag;

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

      Element root = doc.getDocumentElement();

      if (!XmlUtil.nodeMatches(root, sresponseTag)) {
        throw new CalFacadeException(CalFacadeException.badResponse);
      }

      for (Element el: getChildren(root)) {
        ResponseElement fbel = new ResponseElement();

        if (!XmlUtil.nodeMatches(el, responseTag)) {
          throw new CalFacadeException(CalFacadeException.badResponse);
        }

        /* ================================================================
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

        Iterator<Element> respels = getChildren(el).iterator();

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
            String calData = getElementContent(respel);

            Reader rdr = new StringReader(calData);
            Icalendar ical = trans.fromIcal(null, rdr);

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
    } catch (Throwable t) {
      if (debug) {
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
                   final Response resp) throws CalFacadeException {
    BasicHttpClient cio = null;

    try {
      /* We may have to rediscover and retry. */
      for (int failures = 0; failures < 10; failures++) {
        String url = hi.getIScheduleUrl();

        PrivateKey key = pkeys.getKey(url, "isched");

        if (key != null) {
          iout.sign(hi, key);
        }

        cio = getCio(url);
        resp.setHostInfo(hi);

        /* Send the ischedule request. If we get a redirect from the other end
         * we need to do the discovery thing again.
         */

        resp.setResponseCode(cio.sendRequest(iout.getMethod(),
                                             url,
                                             iout.getHeaders(),
                                             iout.getContentType(),
                                             iout.getContentLength(),
                                             iout.getContentBytes()));

        int rcode = resp.getResponseCode();

        if (rcode != HttpServletResponse.SC_OK) {
          error("Got response " + resp.getResponseCode() +
                ", host " + hi.getHostname() +
                " and url " + url);

//          hi.setIScheduleUrl(null);
          cio.release();
          discover(hi);
          continue;
        }

        resp.setClient(cio);
        return;
      }
    } catch (NoHttpResponseException nhre) {
      resp.setNoResponse(true);
      return;
    } catch (Throwable t) {
      resp.setException(t);
      throw new CalFacadeException(t);
    }
  }

  private IscheduleOut makeFreeBusyRequest(final HostInfo hi,
                                           final EventInfo ei) throws CalFacadeException {
    BwEvent ev = ei.getEvent();

    //if (!iSchedule && (recipients.size() > 1)) {
    //  throw new CalFacadeException(CalFacadeException.schedulingBadRecipients);
    //}

    IscheduleOut iout = makeIout(hi, "text/calendar", "POST");

    addOriginator(iout, ev);
    addRecipients(iout, ev);

    Calendar cal = trans.toIcal(ei, ev.getScheduleMethod());

    StringWriter sw = new StringWriter();
    IcalTranslator.writeCalendar(cal, sw);

    iout.addContentLine(sw.toString());

    return iout;
  }

  private void addOriginator(final IscheduleOut iout,
                            final BwEvent ev) throws CalFacadeException {
    if (ev.getOriginator() != null) {
      iout.addHeader("Originator", ev.getOriginator());
      return;
    }

    iout.addHeader("Originator", ev.getOrganizer().getOrganizerUri());
  }

  private void addRecipients(final IscheduleOut iout,
                             final BwEvent ev) throws CalFacadeException {
    Collection<String> recipients = ev.getRecipients();

    for (String recip: recipients) {
      iout.addHeader("Recipient", recip);
    }
  }

  private IscheduleOut makeMeetingRequest(final HostInfo hi,
                                          final EventInfo ei) throws CalFacadeException {
    BwEvent ev = ei.getEvent();

    IscheduleOut iout = makeIout(hi, "text/calendar", "POST");

    addOriginator(iout, ev);
    addRecipients(iout, ev);

    Calendar cal = trans.toIcal(ei, ev.getScheduleMethod());

    StringWriter sw = new StringWriter();
    IcalTranslator.writeCalendar(cal, sw);

    iout.addContentLine(sw.toString());

    return iout;
  }

  private IscheduleOut makeIout(final HostInfo hi,
                                final String contentType,
                                final String method) throws CalFacadeException {
    IscheduleOut iout = new IscheduleOut(domain);

    iout.setContentType(contentType);
    iout.setMethod(method);

    return iout;
  }

  private BasicHttpClient getCio(final String urlStr) throws Throwable {
    URL url = new URL(urlStr);

    String host = url.getHost();
    int port = url.getPort();

    String proto = url.getProtocol();

    boolean secure = "https".equals(proto);

    BasicHttpClient cio = cioTable.get(host + port + secure);

    if (cio == null) {
      cio = new BasicHttpClient(30 * 1000,
                                false);  // followRedirects

      cioTable.put(host + port + secure, cio);
    }

    return cio;
  }

  /* ====================================================================
   *                   XmlUtil wrappers
   * ==================================================================== */

  protected Collection<Element> getChildren(final Node nd) throws CalFacadeException {
    try {
      return XmlUtil.getElements(nd);
    } catch (Throwable t) {
      if (debug) {
        getLogger().error(this, t);
      }

      throw new CalFacadeException(CalFacadeException.badResponse);
    }
  }

  protected String getElementContent(final Element el) throws CalFacadeException {
    try {
      return XmlUtil.getElementContent(el);
    } catch (Throwable t) {
      if (debug) {
        getLogger().error(this, t);
      }

      throw new CalFacadeException(CalFacadeException.badResponse);
    }
  }

  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }

  protected void debugMsg(final String msg) {
    getLogger().debug(msg);
  }

  protected void warn(final String msg) {
    getLogger().warn(msg);
  }

  protected void error(final String msg) {
    getLogger().error(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }
}
