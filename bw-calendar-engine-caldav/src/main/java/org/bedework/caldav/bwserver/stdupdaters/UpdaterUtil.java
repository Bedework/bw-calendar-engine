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

import org.bedework.util.xml.tagdefs.XcalTags;

import ietf.params.xml.ns.icalendar_2.ArrayOfParameters;
import ietf.params.xml.ns.icalendar_2.BaseParameterType;
import ietf.params.xml.ns.icalendar_2.BasePropertyType;
import ietf.params.xml.ns.icalendar_2.TextParameterType;
import ietf.params.xml.ns.icalendar_2.UriParameterType;

import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

/**
 * @author douglm
 *
 */
public class UpdaterUtil {
  /** See if there is a language parameter attached to the property
   *
   * @param p
   * @return String
   */
  static String getLang(final BasePropertyType p) {
    ArrayOfParameters pars = p.getParameters();
    if (pars == null) {
      return null;
    }

    for (JAXBElement<? extends BaseParameterType> parEl: pars.getBaseParameter()) {
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

    for (final JAXBElement<? extends BaseParameterType> parEl:
            pars.getBaseParameter()) {
      if (parEl.getName().getLocalPart().equalsIgnoreCase("x-bedework-wrapped-name")) {
        return ((TextParameterType)parEl.getValue()).getText();
      }
    }

    return null;
  }

  /** See if there is an altrep parameter attached to the property
   *
   * @param p
   * @return String
   */
  static String getAltrep(final BasePropertyType p) {
    ArrayOfParameters pars = p.getParameters();
    if (pars == null) {
      return null;
    }

    for (JAXBElement<? extends BaseParameterType> parEl: pars.getBaseParameter()) {
      if (parEl.getName().equals(XcalTags.altrep)) {
        return ((UriParameterType)parEl.getValue()).getUri();
      }
    }

    return null;
  }

  /** See if there is a language parameter update
   *
   * @param updates
   * @return ParameterUpdater.UpdateInfo or null
   */
  static ParameterUpdater.UpdateInfo findLangUpdate(final List<ParameterUpdater.UpdateInfo> updates) {
    for (ParameterUpdater.UpdateInfo parUpd: updates) {
      if (parUpd.getParamName().equals(XcalTags.language)) {
        return parUpd;
      }
    }

    return null;
  }

  /** Return named parameter attached to the property
   *
   * @param p
   * @param name
   * @return BaseParameterType or null
   */
  static BaseParameterType getParam(final BasePropertyType p,
                                    final QName name) {
    ArrayOfParameters pars = p.getParameters();
    if (pars == null) {
      return null;
    }

    for (JAXBElement<? extends BaseParameterType> parEl: pars.getBaseParameter()) {
      if (parEl.getName().equals(name)) {
        return parEl.getValue();
      }
    }

    return null;
  }

}
