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

import org.bedework.base.exc.BedeworkException;
import org.bedework.base.response.Response;
import org.bedework.caldav.server.soap.synch.SynchConnection;
import org.bedework.caldav.server.soap.synch.SynchConnectionsMBean;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.configs.SynchConfig;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.synch.BwSynchInfo;
import org.bedework.calsvci.CalendarsI.CheckSubscriptionResult;
import org.bedework.calsvci.CalendarsI.SynchStatusResponse;
import org.bedework.calsvci.SynchI;
import org.bedework.synch.wsmessages.ActiveSubscriptionRequestType;
import org.bedework.synch.wsmessages.ArrayOfSynchProperties;
import org.bedework.synch.wsmessages.ConnectorInfoType;
import org.bedework.synch.wsmessages.GetInfoRequestType;
import org.bedework.synch.wsmessages.GetInfoResponseType;
import org.bedework.synch.wsmessages.RefreshRequestType;
import org.bedework.synch.wsmessages.RefreshResponseType;
import org.bedework.synch.wsmessages.SubscribeRequestType;
import org.bedework.synch.wsmessages.SubscribeResponseType;
import org.bedework.synch.wsmessages.SubscriptionStatusRequestType;
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

import jakarta.xml.ws.BindingProvider;
import org.oasis_open.docs.ws_calendar.ns.soap.BaseResponseType;
import org.oasis_open.docs.ws_calendar.ns.soap.StatusType;

import java.net.URL;

import javax.xml.namespace.QName;

import static org.bedework.base.response.Response.Status.failed;
import static org.bedework.base.response.Response.Status.noAccess;
import static org.bedework.base.response.Response.Status.notFound;
import static org.bedework.base.response.Response.Status.ok;

/** Handles interactions with the synch engine from within bedework.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class Synch extends CalSvcDb implements SynchI {
  private SynchConnectionsMBean conns;
  //private ObjectFactory of = new ObjectFactory();

  private final SynchConfig synchConf;

  static final String synchConnectionsMBeanName =
          SynchConnectionsMBean.serviceName;

  /** Namespace of the synch SOAP service
   */
  static final String synchNamespace = "http://www.bedework.org/synch/wsmessages";

  //static final String synchManagerUriPname = "synchManagerUri";

  //static final String synchWsdlUriPname = "synchWsdlUri";

  //static final String synchConnectorIdPname = "synchConnectorId";

  static final QName synchServicename = new QName(synchNamespace,
                                                  "SynchRemoteService");

  /* Refetched periodically but cached */
  private static volatile BwSynchInfo synchInfo;

  private static final long synchInfoRefreshPeriod = 1000 * 60 * 5; // every 5 mins

  private static volatile long lastSynchInfoRefresh;

  Synch(final CalSvc svci,
        final SynchConfig synchConf) {
    super(svci);

    this.synchConf = synchConf;
  }

  @Override
  public boolean getActive() {
    return getSynchConnection() != null;
  }

  private static class SConnection implements Connection {
    private final SynchConnection sc;

    SConnection(final SynchConnection sc) {
      this.sc = sc;
    }
  }

  @Override
  public Connection getSynchConnection() {
    try {
      return new SConnection(getActiveSynchConnections().
                                 getConnectionById(synchConf.getConnectorId()));
    } catch (final Throwable t) {
      warn("Exception getting synch connection: " +
                   "is the synch engine installed and running?");
      if (debug()) {
        error(t);
      }
      return null;
    }
  }

  @Override
  public boolean subscribe(final BwCalendar val) {
    final SConnection sconn = (SConnection)getSynchConnection();

    if ((sconn == null) || (sconn.sc == null)) {
      throw new BedeworkException("No active synch connection");
    }

    final SynchConnection sc = sconn.sc;

    final SubscribeRequestType subreq = new SubscribeRequestType();

    subreq.setToken(sc.getSynchToken());
    subreq.setPrincipalHref(val.getOwnerHref());

    // We'll be A other end is B
    subreq.setDirection(SynchDirectionType.B_TO_A);
    subreq.setMaster(SynchMasterType.B);

    // We'll make this up for the moment - needs to be handed to us by front end

    /* =============== End A - bedework ======================= */
    final ConnectorInfoType ciA = new ConnectorInfoType();

    ciA.setConnectorId(synchConf.getConnectorId());

    final ArrayOfSynchProperties aosA = new ArrayOfSynchProperties();

    ciA.setProperties(aosA);

    aosA.getProperty().add(makeSynchProperty("uri", val.getPath()));
    aosA.getProperty().add(makeSynchProperty("principal",
                                             getPrincipal().getPrincipalRef()));
    aosA.getProperty().add(makeSynchProperty("opaque-data",
                                             makeOpaqueData(val)));

    subreq.setEndAConnector(ciA);

    /* =============== End B - file ======================= */
    final ConnectorInfoType ciB = new ConnectorInfoType();
    String aliasUri = val.getAliasUri();

    String subType = null;

    // We'll allow specification of known type by prefixing the url
    final int pos = aliasUri.indexOf("+http");
    if (pos > 0) {
      subType = aliasUri.substring(0, pos);
      aliasUri = aliasUri.substring(pos + 1);
    }

    if (subType == null) {
      subType = val.getSubscriptionTargetType();
    }

    //warn("############################################ Subtype = " + subType);
    if ((subType == null) || (subType.equals("file"))) {
      ciB.setConnectorId("read-only-file");
    } else {
      // Assume it's the correct id
      ciB.setConnectorId(subType);
    }

    final ArrayOfSynchProperties aosB = new ArrayOfSynchProperties();

    ciB.setProperties(aosB);

    aosB.getProperty().add(makeSynchProperty("uri", aliasUri));

    if (val.getRemoteId() != null) {
      aosB.getProperty().add(makeSynchProperty("principal",
                                               val.getRemoteId()));
    }
    if (val.getRemotePw() != null) {
      // May have pw without id
      aosB.getProperty().add(makeSynchProperty("password",
                                               val.getRemotePw()));
    }

    aosB.getProperty()
        .add(makeSynchProperty("orgsync-publicOnly",
                               String.valueOf(val.getOrgSyncPublicOnly())));
    if (val.getLocationKey() != null) {
      aosB.getProperty()
          .add(makeSynchProperty("locKey",
                                 val.getLocationKey()));
    }

    int refreshRate = val.getRefreshRate(); // seconds
    if (refreshRate == 0) {
      refreshRate = 60 * 60;  // 1 hour default
    }

    aosB.getProperty().add(makeSynchProperty("refreshDelay",
                                             String.valueOf(refreshRate * 1000)));


    subreq.setEndBConnector(ciB);

    /* =============== Global subscription properties ======================= */

    final ArrayOfSynchProperties aos = new ArrayOfSynchProperties();
    subreq.setInfo(aos);

    aos.getProperty().add(makeSynchProperty("alarm-processing", "REMOVE"));
    aos.getProperty().add(makeSynchProperty("scheduling-processing", "REMOVE"));
    if (val.getSynchXlocXcontacts()) {
      aos.getProperty().add(makeSynchProperty("xlocxcontacts",
                                              "true"));
    }

    if (val.getSynchXcategories()) {
      aos.getProperty().add(makeSynchProperty("xcategories",
                                              "true"));
    }

    if (val.getSynchDeleteSuppressed()) {
      aos.getProperty().add(makeSynchProperty("delete-suppressed",
                                              "true"));
    }

    final SubscribeResponseType sresp =
            getPort(synchConf.getManagerUri()).subscribe(
                    getIdToken(getPrincipal().getPrincipalRef(), sc),
                    subreq);
    if (sresp.getStatus() != StatusType.OK) {
      return false;
    }

    val.setSubscriptionId(sresp.getSubscriptionId());

    return true;
  }

  @Override
  public boolean unsubscribe(final BwCalendar val,
                             final boolean forDelete) {
    if (val.getSubscriptionId() == null) {
      return true; // just noop it
    }

    final SConnection sconn = (SConnection)getSynchConnection();

    if ((sconn == null) || (sconn.sc == null)) {
      throw new BedeworkException("No active synch connection");
    }

    final SynchConnection sc = sconn.sc;

    final UnsubscribeRequestType usreq =
            (UnsubscribeRequestType)makeAsr(val,
                                            new UnsubscribeRequestType(),
                                            sc.getSynchToken());

    final UnsubscribeResponseType usresp =
            getPort(synchConf.getManagerUri()).unsubscribe(
                    getIdToken(getPrincipal().getPrincipalRef(), sc),
                    usreq);
    if (usresp.getStatus() != StatusType.OK) {
      return false;
    }

    if (!forDelete) {
      // It's always delete at the moment - this is an Oracle LONG workround
      val.setSubscriptionId(null);
    }

    return true;
  }

  @Override
  public Response<?> refresh(final BwCalendar val) {
    if (val.getSubscriptionId() == null) {
      return new Response<>().ok(); // just noop it
    }

    final SConnection sconn = (SConnection)getSynchConnection();
    final var resp = new Response<>();

    if ((sconn == null) || (sconn.sc == null)) {
      return resp.error("No active synch connection");
    }

    final SynchConnection sc = sconn.sc;

    final RefreshRequestType syncreq =
            (RefreshRequestType)makeAsr(val,
                                        new RefreshRequestType(),
                                        sc.getSynchToken());

    final RefreshResponseType syncresp =
            getPort(synchConf.getManagerUri()).refresh(
                    getIdToken(getPrincipal().getPrincipalRef(), sc),
                    syncreq);
    if (syncresp.getStatus() != StatusType.OK) {
      return fromSynchResponse(resp, syncresp);
    }

    return resp;
  }

  @Override
  public SynchStatusResponse getSynchStatus(final BwCalendar val) {
    final SynchStatusResponse ssr = new SynchStatusResponse();

    if (val == null) {
      ssr.requestStatus = CheckSubscriptionResult.notFound;
      return ssr;
    }

    if (!val.getExternalSub()) {
      ssr.requestStatus = CheckSubscriptionResult.notExternal;
      return ssr;
    }

    if (val.getSubscriptionId() == null) {
      ssr.requestStatus = CheckSubscriptionResult.notsubscribed;
      return ssr;
    }

    final SConnection sconn = (SConnection)getSynchConnection();

    if ((sconn == null) || (sconn.sc == null)) {
      ssr.requestStatus = CheckSubscriptionResult.noSynchService;
      return ssr;
    }

    final SynchConnection sc = sconn.sc;

    final SubscriptionStatusRequestType ssreq =
            (SubscriptionStatusRequestType)makeAsr(val,
                                                   new SubscriptionStatusRequestType(),
                                                   sc.getSynchToken());

    try {
      ssr.subscriptionStatus = getPort(
              synchConf.getManagerUri()).subscriptionStatus(
              getIdToken(getPrincipal().getPrincipalRef(), sc),
              ssreq);
      if (ssr.subscriptionStatus.getStatus() == StatusType.NOT_FOUND) {
        ssr.requestStatus = CheckSubscriptionResult.notsubscribed;
      } else {
        ssr.requestStatus = CheckSubscriptionResult.ok;
      }
    } catch (final Throwable t) {
      error(t);
      ssr.requestStatus = CheckSubscriptionResult.failed;
    }

    return ssr;
  }

  @Override
  public CheckSubscriptionResult checkSubscription(final BwCalendar val) {
    final SynchStatusResponse ssr = getSynchStatus(val);

    if (ssr.requestStatus == CheckSubscriptionResult.notsubscribed) {
      // Try to resubscribe

      if (subscribe(val)) {
        return CheckSubscriptionResult.resubscribed;
      }

      return CheckSubscriptionResult.failed;
    }

    if (ssr.requestStatus != CheckSubscriptionResult.ok) {
      return ssr.requestStatus;
    }

    if (ssr.subscriptionStatus.getStatus() == StatusType.OK) {
      // Assume all is fine
      return CheckSubscriptionResult.ok;
    }

    return CheckSubscriptionResult.failed;
  }

  @Override
  public BwSynchInfo getSynchInfo() {
    if ((synchInfo != null) &&
        ((System.currentTimeMillis() - lastSynchInfoRefresh) < synchInfoRefreshPeriod)) {
      return synchInfo;
    }

    lastSynchInfoRefresh = System.currentTimeMillis(); // We're doing it

    final SConnection sconn = (SConnection)getSynchConnection();

    if ((sconn == null) || (sconn.sc == null)) {
      warn("No active synch connection");
      return null;
    }

    final SynchConnection sc = sconn.sc;

    final GetInfoResponseType girt;
    
    try {
      girt = getPort(synchConf.getManagerUri()).getInfo(
              getIdToken(getPrincipal().getPrincipalRef(), sc),
              new GetInfoRequestType());
    } catch (final Throwable t){
      error(t);
      warn("Unable to fetch synch info");
      return null;
    }

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

  private SynchConnectionsMBean getActiveSynchConnections() {
    try {
      if (conns == null) {
        conns = (SynchConnectionsMBean)MBeanUtil.
                getMBean(SynchConnectionsMBean.class,
                         synchConnectionsMBeanName);
      }

      return conns;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  private ActiveSubscriptionRequestType makeAsr(final BwCalendar val,
                                                final ActiveSubscriptionRequestType asr,
                                                final String synchToken) {
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

  private ConnectorInfoType makeCi(final BwCalendar val) {
    final ConnectorInfoType ci = new ConnectorInfoType();

    ci.setConnectorId(synchConf.getConnectorId());

    final ArrayOfSynchProperties aosA = new ArrayOfSynchProperties();

    ci.setProperties(aosA);

    aosA.getProperty().add(makeSynchProperty("uri", val.getPath()));
    aosA.getProperty().add(makeSynchProperty("principal",
                                             getPrincipal().getPrincipalRef()));

    return ci;
  }

  private SynchPropertyType makeSynchProperty(final String name,
                                              final String value) {
    final SynchPropertyType sp = new SynchPropertyType();

    sp.setName(name);
    sp.setValue(value);

    return sp;
  }

//  private String getSynchUri() {
    //return (String)getGlobalProperty(synchManagerUriPname);
  //}

  SynchIdTokenType getIdToken(final String principal,
                              final SynchConnection sc) {
    final SynchIdTokenType idToken = new SynchIdTokenType();

    idToken.setPrincipalHref(principal);
    idToken.setSubscribeUrl(sc.getSubscribeUrl());
    idToken.setSynchToken(sc.getSynchToken());

    return idToken;
  }

  private String makeOpaqueData(final BwCalendar col) {
    final BwCalSuite cs = getSvc().getCalSuitesHandler().get();
    final String csname;
    if (cs != null) {
      csname = cs.getName();
    } else {
      csname = null;
    }

    String s = "public-admin=" + isPublicAdmin() + "\t" +
            "adminCreateEprops=" + col.getSynchAdminCreateEprops();

    if (csname != null) {
      s += "\tcalsuite=" + csname;
    }
    return s;
  }

  SynchRemoteServicePortType getPort(final String uri) {
    try {
      final URL wsURL = new URL(synchConf.getWsdlUri());

      final SynchRemoteService ers =
        new SynchRemoteService(wsURL, synchServicename);
      final SynchRemoteServicePortType port = ers.getSynchRSPort();

      // Override the endpoint address
      ((BindingProvider)port).getRequestContext().put(
              BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
              uri);
      return port;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  private Response<?> fromSynchResponse(final Response<?> resp,
                                     final BaseResponseType respType) {
    switch (respType.getStatus()) {
      case OK -> {
        resp.setStatus(ok);
      }
      case NOT_FOUND -> {
        resp.setStatus(notFound);
      }
      case NO_ACCESS -> {
        resp.setStatus(noAccess);
      }
      default -> {
        resp.setStatus(failed);
      }
    }

    String desc = null;
    if (respType.getErrorResponse() != null) {
      desc = respType.getErrorResponse().getDescription();
    }
    resp.setMessage(String.format("message: %s, description: %s",
                                  respType.getMessage(), desc));

    return resp;
  }
}
