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
package org.bedework.dumprestore.restore.rules;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwLongString;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.xml.sax.Attributes;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class BwStringRule extends RestoreRule {
  /** Constructor
   *
   * @param globals
   */
  public BwStringRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void begin(final String ns, final String name, final Attributes att) {
    if (!name.equals("bwstring")) {
      // 3.5 onwards we wrapped with a tag. Do nothing
      return;
    }

    push(new BwString());
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    if (name.equals("bwstring")) {
      // 3.5 onwards we wrapped with a tag. Do nothing
      return;
    }

    BwString entity = (BwString)pop();

    if (top() instanceof BwRequestStatus) {
      BwRequestStatus rs = (BwRequestStatus)top();
      rs.setDescription(entity);
      return;
    }

    if (top() instanceof BwCategory) {
      BwCategory cat = (BwCategory)top();

      if (name.equals("keyword")) {
        cat.setWord(entity);
      } else if (name.equals("desc")) {
        cat.setDescription(entity);
      } else {
        unknownTag(name);
      }
      return;
    }

    if (top() instanceof BwContact) {
      BwContact ent = (BwContact)top();

      if (name.equals("value")) {
        ent.setName(entity);
      } else {
        unknownTag(name);
      }
      return;
    }

    if (top() instanceof BwLocation) {
      BwLocation loc = (BwLocation)top();

      if (name.equals("addr")) {
        loc.setAddress(entity);
      } else if (name.equals("subaddr")) {
        loc.setSubaddress(entity);
      } else {
        unknownTag(name);
      }
      return;
    }

    if (top() instanceof BwFilterDef) {
      BwFilterDef f = (BwFilterDef)top();

      if (name.equals("display-name")) {
        f.addDisplayName(entity);
      } else if (name.equals("subaddr")) {
        f.addDescription(new BwLongString(entity.getLang(), entity.getValue()));
      } else {
        unknownTag(name);
      }
      return;
    }

    if (top() instanceof BwAlarm) {
      BwAlarm a = (BwAlarm)top();

      if (name.equals("description")) {
        a.addDescription(entity);

        // For 3.5 we added x-props to the descriptions with the lang non-null

        if (entity.getLang() != null) {
          a.addXproperty(new BwXproperty(entity.getLang(),
                                         null,
                                         entity.getValue()));
        } else {
          a.addDescription(entity);
        }
        return;
      }

      if (name.equals("summary")) {
        a.addSummary(entity);
      } else {
        unknownTag(name);
      }
      return;
    }

    EventInfo ei = (EventInfo)top();

    BwEvent e = ei.getEvent();
    if (e instanceof BwEventProxy) {
      e = ((BwEventProxy)e).getRef();
    }

    if (name.equals("comment")) {
      e.addComment(entity);
    } else if (name.equals("description")) {
      e.addDescription(new BwLongString(entity.getLang(), entity.getValue()));
    } else if (name.equals("resource")) {
      e.addResource(entity);
    } else if (name.equals("summary")) {
      e.addSummary(entity);
    } else {
      unknownTag(name);
    }
  }
}

