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
package org.bedework.icalendar;

import org.bedework.calfacade.exc.CalFacadeException;

import com.fasterxml.jackson.core.JsonGenerator;
import net.fortuna.ical4j.model.AddressList;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.parameter.DelegatedFrom;
import net.fortuna.ical4j.model.parameter.DelegatedTo;
import net.fortuna.ical4j.model.parameter.Member;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author Mike Douglass douglm rpi.edu
 * @version 1.0
 */
public class JsonParameters implements Serializable {
  /* An entry in here if the value may be multi-valued..
   */
  private static Map<String, String> multiMap = new HashMap<>();

  static {
    multiMap.put("delegated-from", "");
    multiMap.put("delegated-to", "");
    multiMap.put("member", "");
  }

  public static void addFields(final JsonGenerator jgen,
                               final Property prop) throws CalFacadeException {
    try {
      jgen.writeStartObject();

      ParameterList pl = prop.getParameters();

      if ((pl != null) && (pl.size() > 0)) {
        Iterator it = pl.iterator();
        while (it.hasNext()) {
          Parameter p = (Parameter)it.next();

          String nm = p.getName().toLowerCase();
          jgen.writeFieldName(nm);

          if (multiMap.get(nm) == null) {
            jgen.writeString(p.getValue());
          } else {
            outValue(jgen, p);
          }
        }
      }

      jgen.writeEndObject();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static void outValue(final JsonGenerator jgen,
                               final Parameter par) throws Throwable {
    if (par instanceof DelegatedFrom) {
      DelegatedFrom d = (DelegatedFrom)par;

      outAddrs(jgen, d.getDelegators());
      return;
    }

    if (par instanceof DelegatedTo) {
      DelegatedTo d = (DelegatedTo)par;

      outAddrs(jgen, d.getDelegatees());
      return;
    }

    if (par instanceof Member) {
      Member m = (Member)par;

      outAddrs(jgen, m.getGroups());
      return;
    }
  }

  private static void outAddrs(final JsonGenerator jgen,
                               final AddressList al) throws Throwable {
    if (al.size() == 1) {
      jgen.writeString(al.iterator().next().toString());
      return;
    }

    jgen.writeStartArray();

    Iterator it = al.iterator();
    while (it.hasNext()) {
      jgen.writeString(it.next().toString());
    }

    jgen.writeEndArray();
  }
}
