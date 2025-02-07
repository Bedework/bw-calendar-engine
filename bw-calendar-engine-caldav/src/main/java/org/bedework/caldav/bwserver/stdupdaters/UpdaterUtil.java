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

package org.bedework.caldav.bwserver.stdupdaters;

import org.bedework.caldav.bwserver.ParameterUpdater;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.tagdefs.XcalTags;

import ietf.params.xml.ns.icalendar_2.ArrayOfParameters;
import ietf.params.xml.ns.icalendar_2.BaseParameterType;
import ietf.params.xml.ns.icalendar_2.BasePropertyType;
import ietf.params.xml.ns.icalendar_2.BooleanParameterType;
import ietf.params.xml.ns.icalendar_2.CalAddressListParamType;
import ietf.params.xml.ns.icalendar_2.CalAddressParamType;
import ietf.params.xml.ns.icalendar_2.DurationParameterType;
import ietf.params.xml.ns.icalendar_2.IntegerParameterType;
import ietf.params.xml.ns.icalendar_2.RangeParamType;
import ietf.params.xml.ns.icalendar_2.TextParameterType;
import ietf.params.xml.ns.icalendar_2.UriParameterType;
import net.fortuna.ical4j.data.DefaultParameterFactorySupplier;
import net.fortuna.ical4j.model.ParameterBuilder;
import net.fortuna.ical4j.model.ParameterList;

import java.util.List;

import jakarta.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

/**
 * @author douglm
 *
 */
public class UpdaterUtil {
  /**
   * See if there is a language parameter attached to the property
   *
   * @param p BasePropertyType
   * @return String
   */
  static String getLang(final BasePropertyType p) {
    final ArrayOfParameters pars = p.getParameters();
    if (pars == null) {
      return null;
    }

    for (final JAXBElement<? extends BaseParameterType> parEl:
            pars.getBaseParameter()) {
      if (parEl.getName().equals(XcalTags.language)) {
        return ((TextParameterType)parEl.getValue()).getText();
      }
    }

    return null;
  }

  static String getWrapperName(final BasePropertyType p) {
    final ArrayOfParameters pars = p.getParameters();
    if (pars == null) {
      return null;
    }

    for (final JAXBElement<? extends BaseParameterType> parEl :
            pars.getBaseParameter()) {
      if (parEl.getName().getLocalPart()
               .equalsIgnoreCase("x-bedework-wrapped-name")) {
        return ((TextParameterType)parEl.getValue()).getText();
      }
    }

    return null;
  }

  /**
   * See if there is an altrep parameter attached to the property
   *
   * @param p BasePropertyType
   * @return String
   */
  static String getAltrep(final BasePropertyType p) {
    final ArrayOfParameters pars = p.getParameters();
    if (pars == null) {
      return null;
    }

    for (final JAXBElement<? extends BaseParameterType> parEl:
            pars.getBaseParameter()) {
      if (parEl.getName().equals(XcalTags.altrep)) {
        return ((UriParameterType)parEl.getValue()).getUri();
      }
    }

    return null;
  }

  /**
   * See if there is a language parameter update
   *
   * @param updates list of updates
   * @return ParameterUpdater.UpdateInfo or null
   */
  static ParameterUpdater.UpdateInfo findLangUpdate(
          final List<ParameterUpdater.UpdateInfo> updates) {
    for (final ParameterUpdater.UpdateInfo parUpd: updates) {
      if (parUpd.getParamName().equals(XcalTags.language)) {
        return parUpd;
      }
    }

    return null;
  }

  /**
   * Return named parameter attached to the property
   *
   * @param p BasePropertyType
   * @param name QName
   * @return BaseParameterType or null
   */
  static BaseParameterType getParam(final BasePropertyType p,
                                    final QName name) {
    final ArrayOfParameters pars = p.getParameters();
    if (pars == null) {
      return null;
    }

    for (final JAXBElement<? extends BaseParameterType> parEl:
            pars.getBaseParameter()) {
      if (parEl.getName().equals(name)) {
        return parEl.getValue();
      }
    }

    return null;
  }

  public static String getParams(final BasePropertyType p) {
    final ArrayOfParameters pars = p.getParameters();
    if ((pars == null) || Util.isEmpty(pars.getBaseParameter())) {
      return null;
    }

    final ParameterList plist = new ParameterList(false);

    for (final JAXBElement<? extends BaseParameterType> parEl : pars
            .getBaseParameter()) {
      final String name = parEl.getName().getLocalPart()
                               .toUpperCase();

      if (name.equals("X-BEDEWORK-WRAPPED-NAME")) {
        continue;
      }

      final String val = getValue(parEl.getValue());

      if (val == null) {
        continue;
      }

      try {
        plist.add(new ParameterBuilder(
                new DefaultParameterFactorySupplier()
                        .get())
                          .name(parEl.getName()
                                     .getLocalPart()
                                     .toUpperCase())
                          .value(val).build());
      } catch (Throwable t) {
      }
    }

    return plist.toString();
  }

  public static String getValue(final BaseParameterType par) {
    if (par instanceof RangeParamType) {
      final RangeParamType par1 = (RangeParamType)par;
      return par1.getText().value();
    }

    if (par instanceof DurationParameterType) {
      final DurationParameterType par1 = (DurationParameterType)par;
      return par1.getDuration().toString();
    }

    if (par instanceof TextParameterType) {
      final TextParameterType par1 = (TextParameterType)par;
      return par1.getText();
    }

    if (par instanceof CalAddressListParamType) {
      final CalAddressListParamType par1 = (CalAddressListParamType)par;
      if (Util.isEmpty(par1.getCalAddress())) {
        return null;
      }
      return String.join(",", par1.getCalAddress());
    }

    if (par instanceof CalAddressParamType) {
      final CalAddressParamType par1 = (CalAddressParamType)par;
      return par1.getCalAddress();
    }

    if (par instanceof IntegerParameterType) {
      final IntegerParameterType par1 = (IntegerParameterType)par;
      if (par1.getInteger() == null) {
        return null;
      }
      return par1.getInteger().toString();
    }

    if (par instanceof UriParameterType) {
      final UriParameterType par1 = (UriParameterType)par;
      return par1.getUri();
    }

    if (par instanceof BooleanParameterType) {
      final BooleanParameterType par1 = (BooleanParameterType)par;
      return String.valueOf(par1.isBoolean());
    }

    return null;
  }
}
