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

import org.bedework.base.exc.BedeworkException;
import org.bedework.caldav.server.IscheduleMessage;
import org.bedework.calsvci.CalSvcI;
import org.bedework.util.misc.Util;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

/** An outgoing (flamboyant?) iSchedule message
 *
 * @author douglm
 *
 */
public class IscheduleOut extends IscheduleMessage {
  private final CalSvcI svci;
  private final String domain;

  private String method;

  private List<Header> headers;

  private List<String> contentLines;
  private byte[] contentBytes;

  /** Constructor
   * @param domain - needed for the d= tag
   */
  public IscheduleOut(final CalSvcI svci,
                      final String domain) {
    this.svci = svci;
    this.domain = domain;

    /* Add the headers we all need */
    addHeader("ischedule-version", "1.0");
    addHeader("cache-control", "no-cache");
    addHeader("cache-control", "no-transform");
  }

  /**
   * @param hinfo host info
   * @param key our private key
   */
  public void sign(final HostInfo hinfo,
                   final PrivateKey key) {
    try {
      final StringBuilder template =
              new StringBuilder("v=1; s=selector; d=");

      template.append(domain);
      template.append("; ");

      template.append("h=content-type:ischedule-version:originator:recipient");

      template.append("; " +
          "c=ischedule-relaxed/simple; ");

      if (hinfo.getIScheduleUsePublicKey()) {
        template.append("q=private-exchange; ");
      } else {
        template.append("q=http/well-known; ");
      }

      template.append("a=rsa-sha256; bh=; b=;");

      final String dkimSig =
              svci.getJDKIM()
                  .getIscheduleDKIMSigner(template.toString(),
                                          key).sign(this,
                                                    getInputStream());

      if (dkimSig == null) {
        return;
      }

      final int pos = dkimSig.indexOf(":");

      addHeader(dkimSig.substring(0, pos), dkimSig.substring(pos + 1));
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  /**
   * @param val   String
   */
  public void setMethod(final String val) {
    method = val;
  }

  /**
   * @return String
   */
  public String getMethod() {
    return method;
  }

  /**
   * @param val   String
   */
  public void setContentType(final String val) {
    replaceHeader("content-type", val);
  }

  /**
   * @return String
   */
  public String getContentType() {
    final List<String> l = getFieldVals("content-type");

    if (Util.isEmpty(l)) {
      return null;
    }

    return l.iterator().next();
  }

  /** Get header for client. This skips the content type header as that is added
   * by the client.
   *
   * @return Header list
   */
  public List<Header> getHeaders() {
    if (headers == null) {
      headers = new ArrayList<>();

      for (final String hname: getFields()) {
        if (hname.equalsIgnoreCase("content-type")) {
          continue;
        }

        for (final String hval: getFieldVals(hname)) {
          headers.add(new BasicHeader(hname, hval));
        }
      }
    }

    return headers;
  }

  /**
   * @param val content line
   */
  public void addContentLine(final String val) {
    if (contentLines == null) {
      contentLines = new ArrayList<>();
    }

    contentLines.add(val);
    contentBytes = null;
  }

  /**
   * @return int content length
   */
  public int getContentLength() {
    if (contentLines == null) {
      return 0;
    }

    return getBytes().length;
  }

  /**
   * @return byte[]  content bytes
   */
  public byte[] getContentBytes() {
    if (contentLines == null) {
      return null;
    }

    return getBytes();
  }

  /**
   * @return a stream based on the byte[] content
   */
  public InputStream getInputStream() {
    return new ByteArrayInputStream(getBytes());
  }

  private void replaceHeader(final String name,
                            final String val) {
    final List<String> l = getFields(name.toLowerCase());

    if (Util.isEmpty(l)) {
      super.addHeader(name, val);
    } else {
      l.clear();
      l.add(val);
    }
  }

  private int getNumRecipients() {
    final List<String> l = getFields("recipient");

    if (Util.isEmpty(l)) {
      return 0;
    }

    return l.size();
  }

  private byte[] getBytes() {
    if (contentBytes != null) {
      return contentBytes;
    }

    final StringBuilder sb = new StringBuilder();

    for (final String ln: contentLines) {
      sb.append(ln);

      if (!ln.endsWith("\n")) {
        sb.append("\n");
      }
    }

    contentBytes = sb.toString().getBytes();

    return contentBytes;
  }
}
