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
package org.bedework.calfacade;

import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.annotations.ical.IcalProperty;
import org.bedework.calfacade.base.BwCloneable;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

import java.io.Serializable;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Save xproperty values. In general we cannot process these.
 *
 * @author Mike Douglass
 *
 */
public class BwXproperty extends BwDbentity<BwXproperty>
            implements BwCloneable {
  /* ====================================================================
   *                        Temp to fix schema issue
   * ==================================================================== */

  /**  */
  public final static String bedeworkOrganizerSchedulingObject = "X-BEDEWORK-ORGSCHEDOBJ";

  /**  */
  public final static String bedeworkAttendeeSchedulingObject = "X-BEDEWORK-ATTSCHEDOBJ";

  /* ====================================================================
   *                        Submissions client properties
   * ==================================================================== */

  /** Used only in web submission process */
  public final static String bedeworkSubmitComment = "X-BEDEWORK-SUBMIT-COMMENT";

  /** Used only in web submission process */
  public final static String bedeworkSubmitStatus = "X-BEDEWORK-SUBMIT-STATUS";

  /** Used only in web submission process */
  public final static String bedeworkSubmitAlias = "X-BEDEWORK-SUBMIT-ALIAS";

  /** Used only in web submission process */
  public final static String bedeworkSubmitterEmail = "X-BEDEWORK-SUBMITTER-EMAIL";

  /** */
  public final static String bedeworkSubmitterClaimant = "X-BEDEWORK-SUBMISSION-CLAIMANT";

  /* ====================================================================
   *                        Admin client properties
   * ==================================================================== */

  /** Path of alias (topical area) */
  public final static String bedeworkAlias = "X-BEDEWORK-ALIAS";

  /** Display name parameter */
  public final static String bedeworkDisplayName = "X-BEDEWORK-PARAM-DISPLAYNAME";

  /* ====================================================================
   *                        Scheduling notification properties
   * ==================================================================== */

  /** Path of scheduling object in users calendar collections */
  public final static String bedeworkSchedulingEntityPath = "X-BEDEWORK-SCHED-PATH";

  /** Set on inbox entity to flag a new meeting */
  public final static String bedeworkSchedulingNew = "X-BEDEWORK-SCHED-NEW";

  /** Set on inbox entity to flag a rescheduled meeting */
  public final static String bedeworkSchedulingReschedule = "X-BEDEWORK-SCHED-RESCHED";

  /** Set on inbox entity to flag a trivial update (organizer update after attendee reply) */
  public final static String bedeworkSchedulingReplyUpdate =
      "X-BEDEWORK-SCHED-REPLY-UPDATE";

  /* ====================================================================
   *                        cal import/export
   * ==================================================================== */

  /** Holds a text DAV property
   */
  public final static String bedeworkDavProp = "X-BEDEWORK-DAV-PROP";

  /** Holds a text icalendar value
   */
  public final static String bedeworkIcal = "X-BEDEWORK-ICAL";

  /** Holds a text icalendar property - first (or only) parameter is always
   * "PNAME" and its value is the real property name
   */
  public final static String bedeworkIcalProp = "X-BEDEWORK-ICAL-PROP";

  /** name of that parameter */
  public final static String bedeworkIcalPropPname = "PNAME";

  /** Used for location, etc
   */
  public final static String xparUid = "X-BEDEWORK-UID";

  /** Maintain our cost
   */
  public final static String bedeworkCost = "X-BEDEWORK-COST";

  /** The deleted flag
   */
  public final static String bedeworkDeleted = "X-BEDEWORK-DELETED";

  /** Holds an encoded array of related to - until schema gets updated
   */
  public final static String bedeworkRelatedTo = "X-BEDEWORK-RELATED-TO";

  /* ====================================================================
   *                        Exchange synch properties
   * ==================================================================== */

  /** */
  public final static String bedeworkExsynchEndtzid = "X-BEDEWORK-EXSYNCH-ENDTZID";

  /** */
  public final static String bedeworkExsynchLastmod = "X-BEDEWORK-EXSYNCH-LASTMOD";

  /** */
  public final static String bedeworkExsynchOrganizer = "X-BEDEWORK-EXSYNCH-ORGANIZER";

  /** */
  public final static String bedeworkExsynchStarttzid = "X-BEDEWORK-EXSYNCH-STARTTZID";

  /* ====================================================================
   *                        Suggested events properties
   * ==================================================================== */

  /** Hold state and href of group in the form state:href where:</br>
   * state = "A" - accepted, "R" - rejected, "P" - pending
   */
  public final static String bedeworkSuggestedTo = "X-BEDEWORK-SUGGESTED-TO";

  /* ====================================================================
   *                        Event registration properties
   * ==================================================================== */

  /** */
  public final static String bedeworkEventRegMaxTickets = "X-BEDEWORK-MAX-TICKETS";

  /** */
  public final static String bedeworkEventRegMaxTicketsPerUser = "X-BEDEWORK-MAX-TICKETS-PER-USER";

  /** */
  public final static String bedeworkEventRegStart = "X-BEDEWORK-REGISTRATION-START";

  /** */
  public final static String bedeworkEventRegEnd = "X-BEDEWORK-REGISTRATION-END";

  /** */
  public final static String bedeworkEventRegForm = "X-BEDEWORK-REGISTRATION-FORM";

  /** */
  public final static String bedeworkEventRegWaitListLimit = "X-BEDEWORK-WAIT-LIST-LIMIT";

  /** */
  public final static String bedeworkEventRegInternal = "X-BEDEWORK-REGISTRATION-INTERNAL";

  /** */
  public final static String bedeworkEventRegExternal = "X-BEDEWORK-REGISTRATION-EXTERNAL";

  /* ====================================================================
   *                        Sharing/publishing properties
   * ==================================================================== */

  /** */
  public final static String bedeworkPublishUrl = "X-BEDEWORK-PUBLISH-URL";

  /* ====================================================================
   *                        Sharing peruser properties
   * calendar sharing spec defines peruser properties. Alarms are handled
   * by ownership. The remainder are stored as x-properties with a param of
   *  x-peruser-owner
   * ==================================================================== */

  /** */
  public final static String peruserPropTransp = "X-PERUSER-PROP-TRANSP";

  /** */
  public final static String peruserOwnerParam = "X-PERUSER-OWNER";

  /** Flags instances which only exist for peruser data. */
  public final static String peruserInstance = "X-PERUSER-INSTANCE";

  /* ====================================================================
   *                      VPoll or VPoll related fields
   * These are moved into real fields in 4.0
   * ==================================================================== */

  public final static String pollItemId = "X-BW-POLL-ITEMID";

  public final static String pollWinner = "X-BW-POLL-WINNER";

  public final static String pollAccceptResponse = "X-BW-POLL-ACCEPT_RESPONSE";

  public final static String pollMode = "X-BW-POLL-MODE";

  public final static String pollProperties = "X-BW-POLL-PROPERTIES";

  public final static String pollItem = "X-BW-POLL-ITEM";

  public final static String pollVoter = "X-BW-POLL-VOTER";

  public final static String pollCandidate = "X-BW-POLL-CANDIDATE";

  /* ====================================================================
   *                        Synch properties
   * ==================================================================== */

  public final static String xBedeworkCategories = "X-BEDEWORK-CATEGORIES";

  public final static String xBedeworkLocation = "X-BEDEWORK-LOCATION";

  // Param for above
  public final static String xBedeworkLocationKeyName = "x-bedework-loc-key";

  public final static String xBedeworkContact = "X-BEDEWORK-CONTACT";

  /* ====================================================================
   *                        location fields
   * ==================================================================== */

  public final static String xBedeworkLocationAddr = "X-BEDEWORK-LOCATION-ADDR";
  public final static String xBedeworkLocationRoom = "X-BEDEWORK-LOCATION-ROOM";
  public final static String xBedeworkLocationAccessible = "X-BEDEWORK-LOCATION-ACCESSIBLE";
  public final static String xBedeworkLocationSfield1 = "X-BEDEWORK-LOCATION-SFIELD1";
  public final static String xBedeworkLocationSfield2 = "X-BEDEWORK-LOCATION-SFIELD2";
  public final static String xBedeworkLocationGeo = "X-BEDEWORK-LOCATION-GEO";
  public final static String xBedeworkLocationStreet = "X-BEDEWORK-LOCATION-STREET";
  public final static String xBedeworkLocationCity = "X-BEDEWORK-LOCATION-CITY";
  public final static String xBedeworkLocationState = "X-BEDEWORK-LOCATION-STATE";
  public final static String xBedeworkLocationZip = "X-BEDEWORK-LOCATION-ZIP";
  public final static String xBedeworkLocationLink = "X-BEDEWORK-LOCATION-LINK";

  /* ====================================================================
   *                        Misc properties
   * ==================================================================== */

  /** */
  public final static String bedeworkTag = "X-BEDEWORK-TAG";

  /** */
  public final static String bedeworkCalsuite = "X-BEDEWORK-CALSUITE";

  /** */
  public final static String bedeworkSchedAssist = "X-BEDEWORK-SCHED-ASSIST";

  /** */
  public final static String bedeworkImage = "X-BEDEWORK-IMAGE";

  /** */
  public final static String bedeworkThumbImage = "X-BEDEWORK-THUMB-IMAGE";

  /** Used to save a timezone - to avoid having to parse the tz spec we prepend
   * the value with the semicolon escaped tzid
   */
  public final static String bedeworkXTimezone = "X-BEDEWORK-TZ";

  /** Pref + name didn't work too well.
   */
  @Deprecated
  public final static String bedeworkTimezonePrefix = "X-BEDEWORK-TZ-";

  /** Changes made to an event. A semicolon separated list. Elements are
   * <ul>
   * <li>dtstamp</li>
   * <li>action: one of CREATE, UPDATE, CANCEL, REPLY</li>
   * <li>MASTER - optional</li>
   * <li>RID=<recurrenceid> - 0 or more</li>
   * <li>property name: 0 or more</li>
   * </ul>
   *
   * <p>For example</br>
   * 20100728T16:12:00Z;UPDATE;SUMMARY</br>
   * 20100728T16:12:00Z;UPDATE;MASTER;SUMMARY
   */
  public final static String bedeworkChanges = "X-BEDEWORK-CHANGES";

  /* ====================================================================
   *                        Apple properties
   * ==================================================================== */

  /** */
  public final static String appleNeedsReply = "X-APPLE-NEEDS-REPLY";

  /** */
  public final static String appleDefaultAlarm = "X-APPLE-DEFAULT-ALARM";

  /* ====================================================================
   *         List of properties we skip when exporting event information
   * ==================================================================== */

  // For jsp
  private static final Set<String> xskipJsp = new TreeSet<>();

  static {
    xskipJsp.add(bedeworkSuggestedTo);

    xskipJsp.add(bedeworkXTimezone);

    xskipJsp.add(bedeworkChanges);

    xskipJsp.add(bedeworkAttendeeSchedulingObject);

    xskipJsp.add(bedeworkOrganizerSchedulingObject);

    xskipJsp.add(bedeworkIcal);

    xskipJsp.add(bedeworkIcalProp);

    xskipJsp.add(bedeworkDavProp);

    xskipJsp.add(pollItemId);

    xskipJsp.add(pollAccceptResponse);

    xskipJsp.add(pollMode);

    xskipJsp.add(pollWinner);

    xskipJsp.add(pollProperties);

    xskipJsp.add(pollItem);

    xskipJsp.add(pollVoter);

    xskipJsp.add(pollCandidate);

    xskipJsp.add(peruserPropTransp);

    xskipJsp.add(peruserInstance);
  }

  // For icalendar
  private static final Set<String> xskip = new TreeSet<>();

  static {
    xskip.addAll(xskipJsp);

    xskip.add(bedeworkSchedulingNew);

    xskip.add(bedeworkSchedulingEntityPath);

    xskip.add(bedeworkSchedulingReplyUpdate);

    xskip.add(bedeworkRelatedTo);
  }

  public static class XpropInfo {
    public final String xName;

    public final String jscalName;

    // true if jscal property is a simple json type
    public final boolean simpleType;

    XpropInfo(final String xName,
              final String jscalName, final boolean simpleType) {
      this.xName = xName;
      this.jscalName = jscalName;
      this.simpleType = simpleType;
    }
  }

  // For icalendar
  private static final Map<String, XpropInfo> xinfo = new HashMap<>();

  private static final Map<String, String> jsToBw = new HashMap<>();

  private static void addXpinfo(final String xName,
                                final String jscalName,
                                final boolean simpleVal) {
    final var lcn = xName.toLowerCase();
    xinfo.put(lcn,
              new XpropInfo(lcn, jscalName, simpleVal));
    jsToBw.put(jscalName, xName);
  }

  public static XpropInfo getXpropInfo(final String name) {
    return xinfo.get(name.toLowerCase());
  }

  public static String getBwFromJsCal(final String jscalName) {
    return jsToBw.get(jscalName);
  }
  static {
     //                        Submissions client properties

    addXpinfo(bedeworkSubmitComment, "bedework.org/submit-comment",
              true);
    addXpinfo(bedeworkSubmitStatus, "bedework.org/submit-status",
              true);
    addXpinfo(bedeworkSubmitAlias, "bedework.org/submit-alias",
              true);
    addXpinfo(bedeworkSubmitterEmail, "bedework.org/submitter-email",
              true);
    addXpinfo(bedeworkSubmitterClaimant, "bedework.org/submission-claimant",
              true);

    //                        Admin client properties

    addXpinfo(bedeworkAlias, "bedework.org/alias",
              true);
    addXpinfo(bedeworkDisplayName, "bedework.org/param-displayname",
              true);

    //                        Scheduling notification properties

    addXpinfo(bedeworkSchedulingEntityPath, "bedework.org/sched-path",
              true);
    addXpinfo(bedeworkSchedulingNew, "bedework.org/sched-new",
              true);
    addXpinfo(bedeworkSchedulingReschedule, "bedework.org/sched-resched",
              true);
    addXpinfo(bedeworkSchedulingReplyUpdate,
              "bedework.org/sched-reply-update", true);

    //                        cal import/export

    addXpinfo(bedeworkDavProp, "bedework.org/dav-prop", true);
    addXpinfo(bedeworkIcal, "bedework.org/ical", true);
    addXpinfo(bedeworkIcalProp, "bedework.org/ical-prop", true);
    addXpinfo(xparUid, "bedework.org/uid", true);
    addXpinfo(bedeworkCost, "bedework.org/cost", true);
    addXpinfo(bedeworkDeleted, "bedework.org/deleted", true);
    addXpinfo(bedeworkRelatedTo, "bedework.org/related-to", true);

    /*                        Exchange synch properties

    addXpinfo(bedeworkExsynchEndtzid, "bedework.org/EXSYNCH-ENDTZID",
              true);
    addXpinfo(bedeworkExsynchLastmod, "bedework.org/EXSYNCH-LASTMOD",
              true);
    addXpinfo(bedeworkExsynchOrganizer,
              "bedework.org/EXSYNCH-ORGANIZER",
              true);
    addXpinfo(bedeworkExsynchStarttzid,
              "bedework.org/EXSYNCH-STARTTZID",
              true);
     */

    //                        Suggested events properties

    addXpinfo(bedeworkSuggestedTo, "bedework.org/suggested-to", true);

    //                        Event registration properties

    addXpinfo(bedeworkEventRegMaxTickets,
              "bedework.org/max-tickets", true);
    addXpinfo(bedeworkEventRegMaxTicketsPerUser,
              "bedework.org/max-tickets-per-user", true);
    addXpinfo(bedeworkEventRegStart,
              "bedework.org/registration-start", true);
    addXpinfo(bedeworkEventRegEnd, "bedework.org/registration-end",
              true);
    addXpinfo(bedeworkEventRegForm, "bedework.org/registration-form",
              true);
    addXpinfo(bedeworkEventRegWaitListLimit,
              "bedework.org/wait-list-limit", true);
    addXpinfo(bedeworkEventRegInternal,
              "bedework.org/registration-internal", true);
    addXpinfo(bedeworkEventRegExternal,
              "bedework.org/registration-external", true);

    //                        Sharing/publishing properties

    addXpinfo(bedeworkPublishUrl, "bedework.org/publish-url", true);

    //                        Synch properties

    addXpinfo(xBedeworkCategories, "bedework.org/categories", true);
    addXpinfo(xBedeworkLocation, "bedework.org/location", true);
    addXpinfo(xBedeworkContact, "bedework.org/contact", true);

    //                        location fields

    addXpinfo(xBedeworkLocationAddr, "bedework.org/location-addr",
              true);
    addXpinfo(xBedeworkLocationRoom, "bedework.org/location-room",
              true);
    addXpinfo(xBedeworkLocationAccessible,
              "bedework.org/location-accessible", true);
    addXpinfo(xBedeworkLocationSfield1,
              "bedework.org/location-sfield1", true);
    addXpinfo(xBedeworkLocationSfield2,
              "bedework.org/location-sfield2", true);
    addXpinfo(xBedeworkLocationGeo, "bedework.org/location-geo", true);
    addXpinfo(xBedeworkLocationStreet, "bedework.org/location-street",
              true);
    addXpinfo(xBedeworkLocationCity, "bedework.org/location-city",
              true);
    addXpinfo(xBedeworkLocationState, "bedework.org/location-state",
              true);
    addXpinfo(xBedeworkLocationZip, "bedework.org/location-zip", true);
    addXpinfo(xBedeworkLocationLink, "bedework.org/location-link",
              true);

    //                        Misc properties

    addXpinfo(bedeworkTag, "bedework.org/tag", true);
    addXpinfo(bedeworkCalsuite, "bedework.org/calsuite", true);
    addXpinfo(bedeworkSchedAssist, "bedework.org/sched-assist", true);
    addXpinfo(bedeworkImage, "bedework.org/image", true);
    addXpinfo(bedeworkThumbImage, "bedework.org/thumb-image", true);
  }

  private String name;

  private String pars;

  private String value;

  /* Derived value */
  private List<Xpar> parameters;

  /** Constructor
   */
  public BwXproperty() {
    super();
  }

  /** Create an x-property by specifying all its fields
   *
   * @param name        String name
   * @param pars        String parameters
   * @param value       String value
   */
  public BwXproperty(final String name, final String pars, final String value) {
    super();
    this.name = name;

    if ((pars != null) && (pars.length() > 0)) {
      this.pars = pars;
    }
    this.value = value;
  }

  /** Make an xproperty to hold an unknown ical property
   *
   * @param name - name of the ical property
   * @param pars - ical pars
   * @param value - the property value
   * @return an xproperty
   */
  public static BwXproperty makeIcalProperty(final String name,
                                             final String pars,
                                             final String value) {
    final StringBuilder sb = new StringBuilder(bedeworkIcalPropPname);
    sb.append("=");
    sb.append(name);

    if ((pars != null) && (pars.length() > 0)) {
      sb.append(";");
      sb.append(pars);
    }

    return new BwXproperty(bedeworkIcalProp, sb.toString(), value);
  }

  /** Make an xproperty to hold a dav property
   *
   * @param name - name of the dav property
   * @param value of property
   * @return an xproperty
   */
  public static BwXproperty makeDavProperty(final String name,
                                             final String value) {
    return new BwXproperty(bedeworkDavProp,
                           bedeworkIcalPropPname + "PNAME=" +  name, value);
  }

  /** Set the name
   *
   * @param val    String name
   */
  @IcalProperty(pindex = PropertyInfoIndex.NAME,
          jname = "name")
  public void setName(final String val) {
    if (val == null) {
      throw new RuntimeException("Name cannot be null");
    }
    name = val;
    parameters = null;
  }

  /** Get the name
   *
   * @return String   name
   */
  public String getName() {
    return name;
  }

  /** Set the pars
   *
   * @param val    String pars
   */
  public void setPars(final String val) {
    if ((val != null) && (val.length() > 0)) {
      pars = val;
    } else {
      pars = null;
    }
    parameters = null;
  }

  /** Get the pars
   *
   * @return String   pars
   */
  public String getPars() {
    return pars;
  }

  /** Set the value
   *
   * @param val    String value
   */
  @IcalProperty(pindex = PropertyInfoIndex.VALUE,
          jname = "value")
  public void setValue(final String val) {
    value = val;
    parameters = null;
  }

  /** Get the value
   *
   *  @return String   value
   */
  public String getValue() {
    return value;
  }

  /* ====================================================================
   *                        Convenience methods
   * ==================================================================== */

  /**
   */
  public static class Xpar implements Comparable<Xpar>, Serializable {
    private final String name;

    private final String value;

    public Xpar(final String name, final String value) {
      this.name = name;
      this.value = value;
    }

    /** Get the name
     *
     * @return String   name
     */
    public String getName() {
      return name;
    }

    /** Get the value
     *
     *  @return String   value
     */
    public String getValue() {
      return value;
    }

    @Override
    public int compareTo(final Xpar o) {
      if (o == null) {
        return 1;
      }

      final int cmp = Util.cmpObjval(getName(), o.getName());

      if (cmp != 0) {
        return cmp;
      }

      return Util.cmpObjval(getValue(), o.getValue());
    }
  }

  /**
   * @return List of parameters split out at the delimiter
   */
  @NoDump
  public List<Xpar> getParameters() {
    if (getPars() == null) {
      return null;
    }

    if (parameters != null) {
      return parameters;
    }

    parameters = parseParameters(getPars());

    return parameters;
  }

  /**
   * @param name of parameter
   * @return Value of named parameter or null
   */
  @NoDump
  public String getParam(final String name) {
    final List<Xpar> params = getParameters();
    if (params == null) {
      return null;
    }

    for (final Xpar param: params) {
      if (param.getName().equals(name)) {
        return param.getValue();
      }
    }

    return null;
  }

  /**
   * @param val to parse
   * @return List<Xpar>
   * @throws RuntimeException on fatal error
   */
  public static List<Xpar> parseParameters(String val) {
    /* Code copied shamelessly from ical4j.
     * Better approach would be to make these parsing methods available to
     * applications.
     */
    final int WORD_CHAR_START = 32;
    final int WORD_CHAR_END = 255;
    final int WHITESPACE_CHAR_START = 0;
    final int WHITESPACE_CHAR_END = 20;

    if ((val == null) || (val.length() == 0)) {
      return null;
    }

    if (!val.startsWith(";")) {
      val = ";" + val;
    }

    final StreamTokenizer tokeniser =
            new StreamTokenizer(new StringReader(val));
    final List<Xpar> pars = new ArrayList<>();

    try {
      tokeniser.resetSyntax();
      tokeniser.wordChars(WORD_CHAR_START, WORD_CHAR_END);
      tokeniser.whitespaceChars(WHITESPACE_CHAR_START,
                                WHITESPACE_CHAR_END);
      tokeniser.ordinaryChar(':');
      tokeniser.ordinaryChar(';');
      tokeniser.ordinaryChar('=');
      tokeniser.ordinaryChar('\t');
      tokeniser.eolIsSignificant(true);
      tokeniser.whitespaceChars(0, 0);
      tokeniser.quoteChar('"');

      while (tokeniser.nextToken() == ';') {
        parseParameter(tokeniser, pars);
      }
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }

    return pars;
  }

  /** Replace space with underscore.
   *
   * @param val to process
   * @return escaped name
   */
  public static String escapeName(final String val) {
    if (!val.contains(" ")) {
      return val;
    }

    final StringBuilder sb = new StringBuilder();

    int pos = 0;
    while (pos < val.length()) {
      final int nextPos = val.indexOf(" ", pos);

      if (nextPos < 0) {
        sb.append(val.substring(pos));
        break;
      }

      sb.append(val, pos, nextPos);
      sb.append("_");
      pos = nextPos + 1;
    }

    return sb.toString();
  }

  /** Replace semicolon with escape semicolon.
   *
   * @param val to process
   * @return escaped name
   */
  public static String escapeSemi(final String val) {
    if (!val.contains(";")) {
      return val;
    }

    final StringBuilder sb = new StringBuilder();

    int pos = 0;
    while (pos < val.length()) {
      final int nextPos = val.indexOf(";", pos);

      if (nextPos < 0) {
        sb.append(val.substring(pos));
        break;
      }

      sb.append(val, pos, nextPos);
      sb.append("\\;");
      pos = nextPos + 1;
    }

    return sb.toString();
  }

  /** Replace escaped semicolon with semicolon.
   *
   * @param val to process
   * @return escaped name
   */
  public static String unescapeSemi(final String val) {
    if (!val.contains("\\;")) {
      return val;
    }

    final StringBuilder sb = new StringBuilder();

    int pos = 0;
    while (pos < val.length()) {
      final int nextPos = val.indexOf("\\;", pos);

      if (nextPos < 0) {
        sb.append(val.substring(pos));
        break;
      }

      sb.append(val, pos, nextPos);
      sb.append(";");
      pos = nextPos + 2;
    }

    return sb.toString();
  }

  /** Return the position of the next unescaped semicolon
   *
   * @param val to search
   * @param start of search
   * @return int position of semicolon or -1 for no semicolon
   */
  public static int nextSemi(final String val, int start) {
    while (start < val.length()) {
      final int escPos = val.indexOf("\\;", start);
      final int sPos = val.indexOf(";", start);

      if (sPos < 0) {
        // No semicolons
        return -1;
      }

      if (escPos < 0) {
        // No escaped semicolons
        return sPos;
      }

      if (escPos == (sPos - 1)) {
        // They both refer to the escaped semicolon
        start = sPos + 1;
        continue;
      }

      // Unescaped prior to the escaped
      return sPos;
    }

    // Escaped at end

    return -1;
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  @Override
  public int compareTo(final BwXproperty that) {
    if (that == this) {
      return 0;
    }

    if (that == null) {
      return -1;
    }

    int res = CalFacadeUtil.cmpObjval(getName(), that.getName());

    if (res != 0) {
      return res;
    }

    res = CalFacadeUtil.cmpObjval(getPars(), that.getPars());

    if (res != 0) {
      return res;
    }

    return CalFacadeUtil.cmpObjval(getValue(), that.getValue());
  }

  @Override
  public int hashCode() {
    int hc = getName().hashCode();

    if (getPars() != null) {
      hc *= getPars().hashCode();
    }

    if (getValue() != null) {
      hc *= getValue().hashCode();
    }

    return hc;
  }

  @Override
  protected void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);
    ts.append("name", getName())
      .append("pars", getPars())
      .append("value", getValue());
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }

  @Override
  public Object clone() {
    return new BwXproperty(getName(), getPars(), getValue());
  }

  /* ====================================================================
   *                        Non-property methods
   * ==================================================================== */

  /**
   * @return true if this property should be skipped in ical generation
   */
  @NoDump
  public boolean getSkip() {
    return xskip.contains(getName());
  }

  /**
   * @return true if this property should be skipped in jsp generation
   */
  @NoDump
  public boolean getSkipJsp() {
    return xskipJsp.contains(getName());
  }

  /* ====================================================================
   *                        Private methods
   * ==================================================================== */

  private static void parseParameter(final StreamTokenizer tokeniser,
                                     final List<Xpar> pars) throws CalFacadeException {

    try {
      if (tokeniser.nextToken() != StreamTokenizer.TT_WORD) {
        throw new CalFacadeException(CalFacadeException.badRequest);
      }

      final String paramName = tokeniser.sval;

      if (tokeniser.nextToken() != '=') {
        throw new CalFacadeException(CalFacadeException.badRequest);
      }

      final StringBuilder paramValue = new StringBuilder();

      /* Don't preserve quote chars - unlike ical4j. This is also used for
       * emitting xml
       */

      if (tokeniser.nextToken() == '"') {
        paramValue.append(tokeniser.sval);
      } else if (tokeniser.sval != null) {
        paramValue.append(tokeniser.sval);
        // check for additional words to account for equals (=) in param-value
        int nextToken = tokeniser.nextToken();

        while (nextToken > 0 &&
                nextToken != ';' &&
                nextToken != ':' &&
                nextToken != ',') {
          if (tokeniser.ttype == StreamTokenizer.TT_WORD) {
            paramValue.append(tokeniser.sval);
          } else {
            paramValue.append((char) tokeniser.ttype);
          }

          nextToken = tokeniser.nextToken();
        }
        tokeniser.pushBack();
      } else {
        tokeniser.pushBack();
      }

      pars.add(new Xpar(paramName, paramValue.toString()));
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /**
   * @param args ignored
   */
  public static void main(final String[] args) {
    testEsc("abcdefg", "Some value");
    testEsc("abcd;efg", "Some value");
    testEsc("abcdefg;", "Some value");
    testEsc(";abcdefg", "Some value");
    testEsc(";abc;de;fg", "Some value");
    testEsc(";abc;de;fg;", "Some value");
    testEsc("abcdefg", "Some \\;value");
  }

  private static void testEsc(String val1, final String val2) {
    final String svVal1 = val1;

    System.out.println("val1 = " + val1);
    System.out.println("val2 = " + val2);

    val1 = escapeSemi(val1);

    System.out.println("esc val1 = " + val1);

    final String val = val1 + ";" + val2;

    final int pos = nextSemi(val, 0);
    System.out.println("Semi at " + pos +
                       " in " + val);

    if (pos < 0) {
      val1 = unescapeSemi(val);
    } else {
      val1 = unescapeSemi(val.substring(0, pos));
    }

    System.out.println("unesc val1 = " + val1);
    if (!svVal1.equals(val1)) {
      System.out.println("***** Val1 != original");
    }

    System.out.println();
  }
}
