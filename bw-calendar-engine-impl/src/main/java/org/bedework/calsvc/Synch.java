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

import org.bedework.caldav.server.soap.synch.SynchConnection;
import org.bedework.caldav.server.soap.synch.SynchConnectionsMBean;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.configs.SynchConfig;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.synch.BwSynchInfo;
import org.bedework.calsvci.CalendarsI.CheckSubscriptionResult;
import org.bedework.calsvci.SynchI;
import org.bedework.synch.wsmessages.ActiveSubscriptionRequestType;
import org.bedework.synch.wsmessages.ArrayOfSynchProperties;
import org.bedework.synch.wsmessages.ConnectorInfoType;
import org.bedework.synch.wsmessages.GetInfoRequestType;
import org.bedework.synch.wsmessages.GetInfoResponseType;
import org.bedework.synch.wsmessages.SubscribeRequestType;
import org.bedework.synch.wsmessages.SubscribeResponseType;
import org.bedework.synch.wsmessages.SubscriptionStatusRequestType;
import org.bedework.synch.wsmessages.SubscriptionStatusResponseType;
import org.bedework.synch.wsmessages.SynchDirectionType;
import org.bedework.synch.wsmessages.SynchEndType;
import org.bedework.synch.wsmessages.SynchIdTokenType;
import org.bedework.synch.wsmessages.SynchMasterType;
import org.bedework.synch.wsmessages.SynchPropertyType;
import org.bedework.synch.wsmessages.SynchRemoteService;
import org.bedework.synch.wsmessages.SynchRemoteServicePortType;
import org.bedework.synch.wsmessages.UnsubscribeRequestType;
import org.bedework.synch.wsmessages.UnsubscribeResponseType;

import org.bedework.util.jmx.MBeanUtil;

import org.oasis_open.docs.ws_calendar.ns.soap.StatusType;

import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;

/** Handles interactions with the synch engine from within bedework.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class Synch extends CalSvcDb implements SynchI {
  private SynchConnectionsMBean conns;
  //private ObjectFactory of = new ObjectFactory();

  private SynchConfig synchConf;

  /** Namespace of the synch SOAP service
   */
  static final String synchNamespace = "http://www.bedework.org/synch/wsmessages";

  static final String synchManagerUriPname = "synchManagerUri";

  static final String synchWsdlUriPname = "synchWsdlUri";

  static final String synchConnectorIdPname = "synchConnectorId";

  static final QName synchServicename = new QName(synchNamespace,
                                                  "SynchRemoteService");

  /* Refetched periodically but cached */
  private static volatile BwSynchInfo synchInfo;

  private static long synchInfoRefreshPeriod = 1000 * 60 * 5; // every 5 mins

  private static volatile long lastSynchInfoRefresh;

  Synch(final CalSvc svci,
        final SynchConfig synchConf) {
    super(svci);

    this.synchConf = synchConf;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SynchI#getActive()
   */
  @Override
  public boolean getActive() throws CalFacadeException {
    return getSynchConnection() != null;
  }

  private static class SConnection implements Connection {
    private SynchConnection sc;

    SConnection(final SynchConnection sc) {
      this.sc = sc;
    }
  }
  /* (non-Javadoc)
   * @see org.bedework.calsvci.SynchI#getSynchConnection()
   */
  @Override
  public Connection getSynchConnection() throws CalFacadeException {
    try {
      return new SConnection(getActiveSynchConnections().
                                 getConnectionById(synchConf.getConnectorId()));
    } catch (Throwable t) {
      if (debug) {
        error(t);
      }
      throw new CalFacadeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SynchI#subscribe(org.bedework.calfacade.BwCalendar)
   */
  @Override
  public boolean subscribe(final BwCalendar val) throws CalFacadeException {
    SConnection sconn = (SConnection)getSynchConnection();

    if (sconn == null) {
      throw new CalFacadeException("No active synch connection");
    }

    SynchConnection sc = sconn.sc;

    SubscribeRequestType subreq = new SubscribeRequestType();

    subreq.setToken(sc.getSynchToken());
    subreq.setPrincipalHref(val.getOwnerHref());

    // We'll be A other end is B
    subreq.setDirection(SynchDirectionType.B_TO_A);
    subreq.setMaster(SynchMasterType.B);

    // We'll make this up for the moment - needs to be handed to us by front end

    /* =============== End A - bedework ======================= */
    ConnectorInfoType ciA = new ConnectorInfoType();

    ciA.setConnectorId(synchConf.getConnectorId());

    ArrayOfSynchProperties aosA = new ArrayOfSynchProperties();

    ciA.setProperties(aosA);

    aosA.getProperty().add(makeSynchProperty("uri", val.getPath()));
    aosA.getProperty().add(makeSynchProperty("principal",
                                             getPrincipal().getPrincipalRef()));

    subreq.setEndAConnector(ciA);

    /* =============== End B - file ======================= */
    ConnectorInfoType ciB = new ConnectorInfoType();

    ciB.setConnectorId("read-only-file");

    ArrayOfSynchProperties aosB = new ArrayOfSynchProperties();

    ciB.setProperties(aosB);

    aosB.getProperty().add(makeSynchProperty("uri", val.getAliasUri()));

    if (val.getRemoteId() != null) {
      aosB.getProperty().add(makeSynchProperty("principal",
                                               val.getRemoteId()));
      aosB.getProperty().add(makeSynchProperty("password",
                                               val.getRemotePw()));
    }

    int refreshRate = val.getRefreshRate(); // seconds
    if (refreshRate == 0) {
      refreshRate = 60;
    }

    aosB.getProperty().add(makeSynchProperty("refreshDelay",
                                             String.valueOf(refreshRate * 1000)));


    subreq.setEndBConnector(ciB);

    /* =============== Global subscription properties ======================= */

    ArrayOfSynchProperties aos = new ArrayOfSynchProperties();
    subreq.setInfo(aos);

    aos.getProperty().add(makeSynchProperty("alarm-processing", "REMOVE"));
    aos.getProperty().add(makeSynchProperty("scheduling-processing", "REMOVE"));

    SubscribeResponseType sresp = getPort(synchConf.getManagerUri()).subscribe(
             getIdToken(getPrincipal().getPrincipalRef(), sc),
             subreq);
    if (sresp.getStatus() != StatusType.OK) {
      return false;
    }

    val.setSubscriptionId(sresp.getSubscriptionId());

    return true;
  }

  @Override
  public boolean unsubscribe(final BwCalendar val) throws CalFacadeException {
    if (val.getSubscriptionId() == null) {
      return true; // just noop it
    }

    SConnection sconn = (SConnection)getSynchConnection();

    if ((sconn == null) | (sconn.sc == null)) {
      throw new CalFacadeException("No active synch connection");
    }

    SynchConnection sc = sconn.sc;

    UnsubscribeRequestType usreq = (UnsubscribeRequestType)makeAsr(val,
                                           new UnsubscribeRequestType(),
                                           sc.getSynchToken());

    UnsubscribeResponseType usresp = getPort(synchConf.getManagerUri()).unsubscribe(
             getIdToken(getPrincipal().getPrincipalRef(), sc),
             usreq);
    if (usresp.getStatus() != StatusType.OK) {
      return false;
    }

    val.setSubscriptionId(null);

    return true;
  }

  @Override
  public CheckSubscriptionResult checkSubscription(final BwCalendar val) throws CalFacadeException {
    if (val == null) {
      return CheckSubscriptionResult.notFound;
    }

    if (!val.getExternalSub()) {
      return CheckSubscriptionResult.notExternal;
    }

    if (val.getSubscriptionId() != null) {
      SConnection sconn = (SConnection)getSynchConnection();

      if ((sconn == null) | (sconn.sc == null)) {
        return CheckSubscriptionResult.noSynchService;
      }

      SynchConnection sc = sconn.sc;

      SubscriptionStatusRequestType ssreq = (SubscriptionStatusRequestType)makeAsr(val,
                                                    new SubscriptionStatusRequestType(),
                                                    sc.getSynchToken());

      SubscriptionStatusResponseType ssresp = getPort(synchConf.getManagerUri()).subscriptionStatus(
               getIdToken(getPrincipal().getPrincipalRef(), sc),
               ssreq);
      if (ssresp.getStatus() == StatusType.OK) {
        // Assume all is fine
        return CheckSubscriptionResult.ok;
      }

      if (ssresp.getStatus() != StatusType.NOT_FOUND) {
        return CheckSubscriptionResult.failed;
      }
    }

    // Try to resubscribe

    if (subscribe(val)) {
      return CheckSubscriptionResult.resubscribed;
    }

    return CheckSubscriptionResult.failed;
  }

  @Override
  public BwSynchInfo getSynchInfo() throws CalFacadeException {
    if ((synchInfo != null) &&
        ((System.currentTimeMillis() - lastSynchInfoRefresh) < synchInfoRefreshPeriod)) {
      return synchInfo;
    }

    lastSynchInfoRefresh = System.currentTimeMillis(); // We're doing it

    SConnection sconn = (SConnection)getSynchConnection();

    if ((sconn == null) || (sconn.sc == null)) {
      warn("No active synch connection");
      return null;
    }

    SynchConnection sc = sconn.sc;

    GetInfoResponseType girt = getPort(synchConf.getManagerUri()).getInfo(
           getIdToken(getPrincipal().getPrincipalRef(), sc),
           new GetInfoRequestType());

    if ((girt == null) || (girt.getInfo() == null)) {
      warn("Unable to fetch synch info");
      return null;
    }

    synchInfo = BwSynchInfo.copy(girt.getInfo());

    return synchInfo;
  }

  /* ====================================================================
   *                   private methods
   * ==================================================================== */

  private SynchConnectionsMBean getActiveSynchConnections() throws CalFacadeException {
    try {
      if (conns == null) {
        conns = (SynchConnectionsMBean)MBeanUtil.getMBean(SynchConnectionsMBean.class,
                                           "org.bedework:service=CalDAVSynchConnections");
      }

      return conns;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private ActiveSubscriptionRequestType makeAsr(final BwCalendar val,
                                                final ActiveSubscriptionRequestType asr,
                                                final String synchToken) throws CalFacadeException {
    if (val.getSubscriptionId() == null) {
      return null; // not active
    }

    asr.setToken(synchToken);
    asr.setPrincipalHref(val.getOwnerHref());
    asr.setSubscriptionId(val.getSubscriptionId());

    asr.setEnd(SynchEndType.A);

    asr.setConnectorInfo(makeCi(val));

    return asr;
  }

  private ConnectorInfoType makeCi(final BwCalendar val) throws CalFacadeException {
    ConnectorInfoType ci = new ConnectorInfoType();

    ci.setConnectorId(synchConf.getConnectorId());

    ArrayOfSynchProperties aosA = new ArrayOfSynchProperties();

    ci.setProperties(aosA);

    aosA.getProperty().add(makeSynchProperty("uri", val.getPath()));
    aosA.getProperty().add(makeSynchProperty("principal",
                                             getPrincipal().getPrincipalRef()));

    return ci;
  }

  private SynchPropertyType makeSynchProperty(final String name,
                                              final String value) {
    SynchPropertyType sp = new SynchPropertyType();

    sp.setName(name);
    sp.setValue(value);

    return sp;
  }

//  private String getSynchUri() throws CalFacadeException {
    //return (String)getGlobalProperty(synchManagerUriPname);
  //}

  SynchIdTokenType getIdToken(final String principal,
                              final SynchConnection sc) {
    SynchIdTokenType idToken = new SynchIdTokenType();

    idToken.setPrincipalHref(principal);
    idToken.setSubscribeUrl(sc.getSubscribeUrl());
    idToken.setSynchToken(sc.getSynchToken());

    return idToken;
  }

  SynchRemoteServicePortType getPort(final String uri) throws CalFacadeException {
    try {
      URL wsURL = new URL(synchConf.getWsdlUri());

      SynchRemoteService ers =
        new SynchRemoteService(wsURL, synchServicename);
      SynchRemoteServicePortType port = ers.getSynchRSPort();

      // Override the endpoint address
      ((BindingProvider)port).getRequestContext().put(
              BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
              uri);
      return port;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }
}
