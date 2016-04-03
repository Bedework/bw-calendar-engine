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

package org.bedework.mail;

import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.locale.Resources;
import org.bedework.calfacade.mail.MailerIntf;
import org.bedework.calfacade.mail.Message;
import org.bedework.calfacade.mail.ObjectAttachment;
import org.bedework.calfacade.util.BwDateTimeUtil;

import java.text.DateFormat;

/** Some useful methods used when mailing calendar objects..
 *
 * @author Mike Douglass douglm@rpi.edu
 */
public class MailUtil {
  /** make event printable
   *
   * @param event
   * @return printable event
   */
  public static StringBuffer displayableEvent(BwEvent event) {
    return displayableEvent(event, new Resources());
  }

  /** make event printable using resources
   *
   * @param event
   * @param rsrc
   * @return printable event
   */
  public static StringBuffer displayableEvent(BwEvent event,
                                              Resources rsrc) {
    StringBuffer sb = new StringBuffer();

    sb.append(event.getSummary());
    sb.append("\n");
    sb.append(rsrc.getString(Resources.START));
    sb.append(": ");
    sb.append(formatDate(event.getDtstart()));
    sb.append("\n");
    sb.append(rsrc.getString(Resources.END));
    sb.append(": ");
    sb.append(formatDate(event.getDtend()));
    sb.append("\n");
    sb.append(rsrc.getString(Resources.DESCRIPTION));
    sb.append(": \n");
    sb.append(event.getDescription());
    sb.append("\n");

    return sb;
  }

  /**
   * @param dt
   * @return formatted date
   */
  public static String formatDate(BwDateTime dt) {
    DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
    try {
      return df.format(BwDateTimeUtil.getDate(dt));
    } catch (Throwable t) {
      return t.getMessage();
    }
  }

  /** Mail a message to somebody.
   *
   * <p>All required message fields are set. The message will be mailed via
   * the supplied mailer. If the Object is non-null it will be
   * converted to the appropriate external form and sent as an attachment.
   *
   * @param mailer
   * @param val      Message to mail
   * @param att      String val to attach - e.g event, todo
   * @param name     name for attachment
   * @param type     mimetype for attachment
   * @param sysid    used for from address
   * @throws Throwable
   */
  public static void mailMessage(MailerIntf mailer,
                                 Message val,
                                 String att,
                                 String name,
                                 String type,
                                 String sysid) throws Throwable {
    ObjectAttachment oa = new ObjectAttachment();

    oa.setOriginalName(name);
    oa.setVal(att);
    oa.setMimeType(type);

    val.addAttachment(oa);

    if (val.getFrom() == null) {
      // This should be a property
      val.setFrom("donotreply-" + sysid);
    }

    mailer.post(val);
  }
}

