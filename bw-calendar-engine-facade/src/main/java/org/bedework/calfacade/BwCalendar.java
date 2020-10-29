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

import org.bedework.access.CurrentAccess;
import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.annotations.NoWrap;
import org.bedework.calfacade.annotations.Wrapper;
import org.bedework.calfacade.annotations.ical.IcalProperty;
import org.bedework.calfacade.annotations.ical.NoProxy;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.calfacade.base.CollatableEntity;
import org.bedework.calfacade.base.PropertiesEntity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;
import org.bedework.util.timezones.DateTimeUtil;
import org.bedework.util.xml.FromXmlCallback;
import org.bedework.util.xml.tagdefs.AppleIcalTags;
import org.bedework.util.xml.tagdefs.AppleServerTags;
import org.bedework.util.xml.tagdefs.BedeworkServerTags;
import org.bedework.util.xml.tagdefs.CaldavTags;
import org.bedework.util.xml.tagdefs.NamespaceAbbrevs;
import org.bedework.util.xml.tagdefs.XcalTags;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import net.fortuna.ical4j.model.property.LastModified;
import org.w3c.dom.Element;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.xml.namespace.QName;

/** A collection in Bedework. This is roughly equivalent to a folder with some
 * rules attached.
 *
 * <p>These objects are used to create two tree structures rooted at the public
 * calendars root and the user calendars root. The names of the roots are defined
 * at system build time.
 *
 * <p>For caldav compatability we do not allow calendar collections inside
 * calendar collections.
 *
 * <p>Collections are given a type defined below.
 *
 * <p>Entities have names which must be unique within a collection. An
 * event, either a single non-recurring event, or a master event and all it's
 * overrides, have a single effective name which would correspond to the name
 * of an ics file generated for that event.
 *
 * <p>Calendar entities also have uids. These must be unique within normal
 * calendar collections. That requirement is relaxed for other special
 * calendars.
 *
 * Collections may be tombstoned - that is they are effectively deleted but
 * remain for the purpose of synchronization reports. Currently we indicate a
 * tombstoned collection by setting the filter value to "--TOMBSTONED--".
 *
 * XXX We suffix the name and path also to avoid some ugly clashes related to
 * lastmod
 *
 *  @author Mike Douglass douglm - rpi.edu
 *  @version 1.0
 */
@Wrapper(quotas = true)
@Dump(elementName="collection", keyFields={"path"})
@JsonIgnoreProperties({"aliasTarget", 
                       "aliasOrigin", 
                       "collectionInfo", 
                       "numProperties",
                       "pwNeedsEncrypt", 
                       "tombstoned"})
public class BwCalendar extends BwShareableContainedDbentity<BwCalendar>
        implements CollatableEntity, CategorisedEntity, PropertiesEntity {
  // TODO - make configurable
  public static final int minRefreshRateSeconds = 15 * 60;

  /** The internal name of the calendar
   */
  private String name;

  /** Path to this calendar - including this one.
   * The names concatenated with intervening '/'
   */
  private String path;

  /** The display name for the collection
   */
  private String summary;

  /** Some sort of description - may be null
   */
  private String description;

  /** This identifies an associated mailing list. Its actual value is set
   * by the mailer interface.
   */
  private String mailListId;

  /* The type of calendar */
  private int calType;

  /** Indicate unknown type */
  public final static int calTypeUnknown = -1;

  /** <em>Normal folder</em> Holds other collections */
  public final static int calTypeFolder = 0;

  /** <em>Normal calendar collection</em> holds events, todos etc */
  public final static int calTypeCalendarCollection = 1;

  /** <em>Trash</em> - don't use  */
  public final static int calTypeTrash = 2;

  /** <em>Deleted</em> Holds annotations which effectively delete events to
   * which the user does not have write access
   */
  public final static int calTypeDeleted = 3;

  /** <em>Busy</em> Used to store busy time - acts as a mask for freebusy */
  public final static int calTypeBusy = 4;

  /** <em>Inbox</em> Mostly used for notification of meeting requests */
  public final static int calTypeInbox = 5;

  /** <em>Outbox</em> Target for scheduling. Normally empty */
  public final static int calTypeOutbox = 6;

  /** <em>Alias</em>  */
  public final static int calTypeAlias = 7;

  /** <em>External subscription</em>  */
  public final static int calTypeExtSub = 8;

  /** <em>Resource collection</em> According to the CalDAV spec a collection may exist
   * inside a calendar collection but no calendar collection must be so
   * contained at any depth. (RFC 4791 Section 4.2) */
  public final static int calTypeResourceCollection = 9;

  /** <em>Notifications collection</em>  */
  public final static int calTypeNotifications = 10;

  /** <em>List of events</em>  */
  public final static int calTypeEventList = 11;

  /** <em>Vpoll entities</em>  */
  public final static int calTypePoll = 12;

  /** <em>Pending Inbox</em> Unprocessed meeting requests */
  public final static int calTypePendingInbox = 13;

  /** <em>managed attachments</em>  */
  public final static int calTypeAttachments = 14;

  /** <em>Tasks</em>  */
  public final static int calTypeTasks = 15;


  private static final boolean f = false;
  private static final boolean o = false; // flag obsolete entries
  private static final boolean T = true;

  /** The info */
  private static final CollectionInfo[] collectionInfo = {
    ci(calTypeFolder,             f, T, f, f, f, T, T, f, f, f, f),
    ci(calTypeCalendarCollection, f, T, T, T, T, T, T, T, T, T, f),
    ci(calTypeTrash,              o, o, o, o, o, o, o, o, o, o, o),
    ci(calTypeDeleted,            o, o, o, o, o, o, o, o, o, o, o),
    ci(calTypeBusy,               T, f, T, T, T, T, f, T, f, f, f),
    ci(calTypeInbox,              T, f, T, f, f, f, f, T, f, f, T),
    ci(calTypeOutbox,             T, f, T, f, f, f, f, T, f, f, T),
    ci(calTypeAlias,              f, f, f, f, f, T, T, f, f, f, f),
    ci(calTypeExtSub,             f, T, T, T, f, T, T, T, f, f, f),
    ci(calTypeResourceCollection, f, T, f, f, f, f, f, f, f, f, f),
    ci(calTypeNotifications,      T, f, f, f, f, f, f, f, f, f, T),
    ci(calTypeEventList,          T, f, T, T, T, T, f, T, f, f, f),
    ci(calTypePoll,               f, T, T, T, T, T, T, T, T, T, T),
    ci(calTypePendingInbox,       T, f, T, f, f, f, f, T, f, f, T),
    ci(calTypeAttachments,        T, f, T, f, f, f, f, f, f, f, f),
    ci(calTypeTasks,              f, T, T, T, T, T, T, T, T, T, T),
  };
  /*                  ^           1  2  3  4  5  6  7  8  9 10 11
                      |           |  |  |  |  |  |  |  |  |  |  |
    collectionType ---+           |  |  |  |  |  |  |  |  |  |  |
           special  1 ------------+  |  |  |  |  |  |  |  |  |  |
   childrenAllowed  2 --------------+|  |  |  |  |  |  |  |  |  |
         indexable  3 ------------------+  |  |  |  |  |  |  |  |
         uniqueKey  4 ---------------------+  |  |  |  |  |  |  |
  allowAnnotations  5 ------------------------+  |  |  |  |  |  |
     allowFreeBusy  6 ---------------------------+  |  |  |  |  |
          canAlias  7 ------------------------------+  |  |  |  |
   onlyCalEntities  8 ---------------------------------+  |  |  |
        scheduling  9 ------------------------------------+  |  |
         shareable 10 ---------------------------------------+  |
         provision 11 ------------------------------------------+
   */

  private static final List<CollectionInfo> roCollectionInfo =
          List.of(collectionInfo);

  /* Certain collections should be initialised so that they
     restrict the entity types that can be added to them. The
     following table provides that information.
   */

  public final static Map<Integer, List<String>> entityTypes;

  static {

    entityTypes = Map.of(
            calTypeCalendarCollection, List.of("VEVENT"),

            calTypePoll, List.of("VPOLL"),

            calTypeTasks, List.of("VTODO"));
  }

  public final static String internalAliasUriPrefix = "bwcal://";

  /** UTC datetime */
  private String created;

  private BwCollectionLastmod lastmod;

  private String filterExpr;

  /** Value of filter for a tombstoned collection
   */
  public static final String tombstonedFilter = "--TOMBSTONED--";

  /** Value of suffix on path for a tombstoned collection
   */
  public static final String tombstonedSuffix = "(--TOMBSTONED--)";

  private Set<BwCategory> categories = null;

  private Set<BwProperty> properties;

  private String aliasUri;

  private boolean display = true;

  private boolean affectsFreeBusy;

  private boolean ignoreTransparency;

  private boolean unremoveable;

  private int refreshRate;

  private String lastRefresh;

  private String lastRefreshStatus;

  private String lastEtag;

  private String remoteId;

  private String remotePw;

  /* ====================================================================
   *                      Non-db fields
   * ==================================================================== */

  private BwCalendar aliasTarget;

  private BwCalendar aliasOrigin;

  private int aliasCalType;

  private boolean pwNeedsEncrypt;

  private List<String> supportedComponents;

  private Collection<BwCalendar> children;

  private List<String> vpollSupportedComponents;

  private Set<String> categoryHrefs;

  /** Constructor
   */
  public BwCalendar() {
    super();

    /* Set the lastmod and created */

    final Date dt = new Date();
    setLastmod(new BwCollectionLastmod(this, dt));
    setCreated(DateTimeUtil.isoDateTimeUTC(dt));
  }

  /** Set the name
   *
   * @param val    String name
   */
  public void setName(final String val) {
    name = val;
  }

  /** Get the name
   *
   * @return String   name
   */
  public String getName() {
    return name;
  }

  /** Set the path
   *
   * @param val    String path
   */
  public void setPath(final String val) {
    path = val;
    if (getLastmod() != null) {
      getLastmod().setPath(val);
    }
  }

  /** Get the path
   *
   * @return String   path
   */
  public String getPath() {
    return path;
  }

  /** Set the summary
   *
   * @param val    String summary
   */
  public void setSummary(final String val) {
    summary = val;
  }

  /** Get the summary
   *
   * @return String   summary
   */
  public String getSummary() {
    if (summary == null) {
      return getName();
    }
    return summary;
  }

  /** Set the description
   *
   * @param val    description
   */
  public void setDescription(final String val) {
    description = val;
  }

  /** Get the description
   *
   *  @return String   description
   */
  public String getDescription() {
    return description;
  }

  /** Set the mail list id
   *
   * @param val    String mail list id
   */
  public void setMailListId(final String val) {
    mailListId = val;
  }

  /** Get the mail list id
   *
   *  @return String   mail list id
   */
  public String getMailListId() {
    return mailListId;
  }

  /** Set the type
   *
   * @param val    type
   */
  public void setCalType(final int val) {
    calType = val;
  }

  /** Get the type
   *
   *  @return int type
   */
  public int getCalType() {
    return calType;
  }

  /**
   * @param val - the created date
   */
  public void setCreated(final String val) {
    created = val;
  }

  /**
   * @return String created
   */
  public String getCreated() {
    return created;
  }

  /**
   * @param val the lastmod
   */
  public void setLastmod(final BwCollectionLastmod val) {
    lastmod = val;
  }

  /**
   * @return BwCollectionLastmod lastmod
   */
  @Dump(elementName = "col-lastmod", compound=true)
  public BwCollectionLastmod getLastmod() {
    return lastmod;
  }

  /**
   * @param val - the filter expression
   */
  public void setFilterExpr(final String val) {
    filterExpr = val;
  }

  /**
   * @return String FilterExpr
   */
  public String getFilterExpr() {
    return filterExpr;
  }

  /** Set the refresh rate in seconds
   *
   * @param val    type
   */
  public void setRefreshRate(final int val) {
    refreshRate = val;
  }

  /** Get the refresh rate in seconds
   *
   *  @return String   description
   */
  public int getRefreshRate() {
    return refreshRate;
  }

  /**
   * @param val - the value
   */
  public void setLastRefresh(final String val) {
    lastRefresh = val;
  }

  /**
   *
   * @return String lastRefresh
   */
  public String getLastRefresh() {
    return lastRefresh;
  }

  /**
   * @param val HTTP status or other appropriate value
   */
  public void setLastRefreshStatus(final String val) {
    lastRefreshStatus = val;
  }

  /**
   * @return String lastRefreshStatus
   */
  public String getLastRefreshStatus() {
    return lastRefreshStatus;
  }

  /**
   * @param val - the value
   */
  public void setLastEtag(final String val) {
    lastEtag = val;
  }

  /**
   * @return String lastRefresh
   */
  public String getLastEtag() {
    return lastEtag;
  }

  /**
   *
   * @param val If non-null we have a remote id and encrypted password
   */
  public void setRemoteId(final String val) {
    remoteId = val;
  }

  /**
   * @return String remoteId
   */
  public String getRemoteId() {
    return remoteId;
  }

  /**
   *
   * @param val If non-null the encrypted password
   */
  public void setRemotePw(final String val) {
    remotePw = val;
  }

  /**
   * @return String encrypted password
   */
  public String getRemotePw() {
    return remotePw;
  }

  /* ====================================================================
   *               CategorisedEntity interface methods
   * ==================================================================== */

  @Override
  public void setCategories(final Set<BwCategory> val) {
    categories = val;
  }

  @Override
  public Set<BwCategory> getCategories() {
    return categories;
  }

  @Override
  @NoDump
  public int getNumCategories() {
    final Set<BwCategory> c = getCategories();
    if (c == null) {
      return 0;
    }

    return c.size();
  }

  @Override
  public boolean addCategory(final BwCategory val) {
    Set<BwCategory> cats = getCategories();
    if (cats == null) {
      cats = new TreeSet<>();
      setCategories(cats);
    }

    if (!cats.contains(val)) {
      cats.add(val);
      return true;
    }
    
    return false;
  }

  @Override
  public boolean removeCategory(final BwCategory val) {
    final Set<BwCategory> cats = getCategories();
    if (cats == null) {
      return false;
    }

    return cats.remove(val);
  }

  @Override
  public boolean hasCategory(final BwCategory val) {
    final Set<BwCategory> cats = getCategories();
    if (cats == null) {
      return false;
    }

    return cats.contains(val);
  }

  @Override
  public Set<BwCategory> copyCategories() {
    if (getNumCategories() == 0) {
      return null;
    }

    return new TreeSet<>(getCategories());
  }

  @Override
  public Set<BwCategory> cloneCategories() {
    if (getNumCategories() == 0) {
      return null;
    }
    final TreeSet<BwCategory> ts = new TreeSet<>();

    for (final BwCategory cat: getCategories()) {
      ts.add((BwCategory)cat.clone());
    }

    return ts;
  }

  /** Set the alias uri
   *
   * @param val    String uri
   */
  @NoWrap
  public void setAliasUri(final String val) {
    aliasUri = val;
    if (val != null) {
      setCalType(calTypeAlias);
      if (getInternalAliasPath() == null) {
        setCalType(calTypeExtSub);
      }
    }
  }

  /** Get the alias uri
   *
   * @return String   uri
   */
  public String getAliasUri() {
    return aliasUri;
  }

  /**
   *
   * @param val   boolean true if the password needs encrypting
   */
  public void setPwNeedsEncrypt(final boolean val) {
    pwNeedsEncrypt = val;
  }

  /**
   *
   * @return boolean  true if the password needs encrypting
   */
  @NoDump
  public boolean getPwNeedsEncrypt() {
    return pwNeedsEncrypt;
  }

  /**
   *
   * @param val   boolean true if the calendar is to be displayed
   */
  public void setDisplay(final boolean val) {
    display = val;
  }

  /**
   *
   * @return boolean  true if the calendar is to be displayed
   */
  public boolean getDisplay() {
    return display;
  }

  /**
   *
   *  @param val    true if the calendar takes part in free/busy calculations
   */
  public void setAffectsFreeBusy(final boolean val) {
    affectsFreeBusy = val;
  }

  /**
   *
   *  @return boolean    true if the calendar takes part in free/busy calculations
   */
  public boolean getAffectsFreeBusy() {
    return affectsFreeBusy;
  }

  /** Set the ignoreTransparency flag
   *
   *  @param val    true if we ignore tranparency in free/busy calculations
   */
  public void setIgnoreTransparency(final boolean val) {
    ignoreTransparency = val;
  }

  /** Do we ignore transparency?
   *
   *  @return boolean    true for ignoreTransparency
   */
  public boolean getIgnoreTransparency() {
    return ignoreTransparency;
  }

  /**
   *
   * @param val   boolean true if the calendar is unremoveable
   */
  public void setUnremoveable(final boolean val) {
    unremoveable = val;
  }

  /**
   *
   * @return boolean  true if the calendar is unremoveable
   */
  public boolean getUnremoveable() {
    return unremoveable;
  }

  /* ====================================================================
   *                   Property methods
   * ==================================================================== */

  @Override
  public void setProperties(final Set<BwProperty> val) {
    properties = val;
  }

  @Override
  @Dump(collectionElementName = "property", compound = true)
  public Set<BwProperty> getProperties() {
    return properties;
  }

  @Override
  public Set<BwProperty> getProperties(final String name) {
    final TreeSet<BwProperty> ps = new TreeSet<>();

    if (getNumProperties() == 0) {
      return null;
    }

    for (final BwProperty p: getProperties()) {
      if (p.getName().equals(name)) {
        ps.add(p);
      }
    }

    return ps;
  }

  @Override
  public void removeProperties(final String name) {
    final Set<BwProperty> ps = getProperties(name);

    if (ps == null) {
      return;
    }

    for (final BwProperty p: ps) {
      removeProperty(p);
    }
  }

  @Override
  @NoDump
  public int getNumProperties() {
    final Collection<BwProperty> c = getProperties();
    if (c == null) {
      return 0;
    }

    return c.size();
  }

  @Override
  public BwProperty findProperty(final String name) {
    final Collection<BwProperty> props = getProperties();

    if (props == null) {
      return null;
    }

    for (final BwProperty prop: props) {
      if (name.equals(prop.getName())) {
        return prop;
      }
    }

    return null;
  }

  @Override
  public void addProperty(final BwProperty val) {
    Set<BwProperty> c = getProperties();
    if (c == null) {
      c = new TreeSet<>();
      setProperties(c);
    }

    c.add(val);
  }

  @Override
  public boolean removeProperty(final BwProperty val) {
    final Set<BwProperty> c = getProperties();
    if (c == null) {
      return false;
    }

    return c.remove(val);
  }

  @Override
  public Set<BwProperty> copyProperties() {
    if (getNumProperties() == 0) {
      return null;
    }

    return new TreeSet<>(getProperties());
  }

  @Override
  public Set<BwProperty> cloneProperties() {
    if (getNumProperties() == 0) {
      return null;
    }
    final TreeSet<BwProperty> ts = new TreeSet<>();

    for (final BwProperty p: getProperties()) {
      ts.add((BwProperty)p.clone());
    }

    return ts;
  }

  /* ====================================================================
   *                   Property convenience methods
   * ==================================================================== */

  /**
   * @param val the supported component names e.g. "VEVENT", "VTODO" etc.
   */
  public void setSupportedComponents(final List<String> val) {
    supportedComponents = val;

    if (Util.isEmpty(val)) {
      removeQproperty(CaldavTags.supportedCalendarComponentSet);
      return;
    }

    setQproperty(CaldavTags.supportedCalendarComponentSet,
                 String.join(",",  val));
  }

  /**
   * @return the supported components
   */
  @NoDump
  public List<String> getSupportedComponents() {
    if (supportedComponents == null) {
      supportedComponents = new ArrayList<>();

      final int ctype = getCalType();

      if (ctype == calTypePoll) {
        supportedComponents.add("VPOLL");
        return supportedComponents;
      }

      if (ctype == calTypeTasks) {
        supportedComponents.add("VTODO");
        return supportedComponents;
      }

      if (ctype == calTypeInbox) {
        supportedComponents.add("VPOLL");
        supportedComponents.add("VEVENT");
        supportedComponents.add("VTODO");
        supportedComponents.add("VAVAILABILITY");
        return supportedComponents;
      }

      if ((ctype != calTypeCalendarCollection) &&
          (ctype != calTypeOutbox) &&
          (ctype != calTypeExtSub)) {
        return supportedComponents;
      }

      final String slist = getQproperty(CaldavTags.supportedCalendarComponentSet);

      if (slist == null) {
        supportedComponents.add("VEVENT");
        //supportedComponents.add("VTODO");
        //supportedComponents.add("VAVAILABILITY");
      } else {
        final String[] ss = slist.split(",");
        supportedComponents.addAll(Arrays.asList(ss));
      }
    }

    return supportedComponents;
  }

  /**
   * @return the supported vpoll components
   */
  @NoDump
  public List<String> getVpollSupportedComponents() {
    if (vpollSupportedComponents == null) {
      vpollSupportedComponents = new ArrayList<>();

      if ((getCalType() != calTypePoll) &&
          (getCalType() != calTypeInbox) &&
          (getCalType() != calTypeOutbox)) {
        return vpollSupportedComponents;
      }

      final String slist = getQproperty(CaldavTags.vpollSupportedComponentSet);

      if (slist == null) {
        vpollSupportedComponents.add("VEVENT");
        //vpollSupportedComponents.add("VTODO");
        //vpollSupportedComponents.add("VAVAILABILITY");
      } else {
        final String[] ss = slist.split(",");
        vpollSupportedComponents.addAll(Arrays.asList(ss));
      }
    }

    return vpollSupportedComponents;
  }

  /** Set the calendar color property
   *
   * @param val color
   */
  public void setColor(final String val) {
    if (Util.checkNull(val) == null) {
      final BwProperty p = findProperty(AppleIcalTags.calendarColor.getLocalPart());
      if (p != null) {
        removeProperty(p);
      }
    } else {
      setProperty(AppleIcalTags.calendarColor.getLocalPart(), val);
    }
  }

  /** Get the calendar color property
   *
   * @return String calendar color
   */
  @NoDump
  public String getColor() {
    return getProperty(AppleIcalTags.calendarColor.getLocalPart());
  }

  static final String subscriptionIdProperty = "org.bedework.subscriptionId";

  /** Set the subscription id
   *
   * @param val subscription id
   */
  public void setSubscriptionId(final String val) {
    if (Util.checkNull(val) == null) {
      final BwProperty p = findProperty(subscriptionIdProperty);
      if (p != null) {
        removeProperty(p);
      }
    } else {
      setProperty(subscriptionIdProperty, val);
    }
  }

  /** Get the subscriptionId property
   *
   * @return String subscriptionId
   */
  @NoDump
  public String getSubscriptionId() {
    return getProperty(subscriptionIdProperty);
  }

  /** Set the admin can create event properties flag for synch
   *
   * @param val if set the admin can create event properties during synch
   */
  public void setSynchAdminCreateEprops(final boolean val) {
    setProperty(BedeworkServerTags.synchAdminCreateEpropsProperty.getLocalPart(),
                String.valueOf(val));
  }

  /** Get the admin can create event properties flag for synch
   *
   * @return boolean on/off
   */
  @NoDump
  public boolean getSynchAdminCreateEprops() {
    return Boolean.parseBoolean(getProperty(
            BedeworkServerTags.synchAdminCreateEpropsProperty.getLocalPart()));
  }

  /** Set the process contacts and locations flag for synch
   *
   * @param val if we process contacts and locations during synch
   */
  public void setSynchXlocXcontacts(final boolean val) {
    setProperty(BedeworkServerTags.synchXlocXcontacts.getLocalPart(),
                String.valueOf(val));
  }

  /** Get the process contacts and locations flag for synch
   *
   * @return boolean on/off
   */
  @NoDump
  public boolean getSynchXlocXcontacts() {
    return Boolean.parseBoolean(getProperty(
            BedeworkServerTags.synchXlocXcontacts.getLocalPart()));
  }

  /** Set the process categories flag for synch
   *
   * @param val if we process categories during synch
   */
  public void setSynchXcategories(final boolean val) {
    setProperty(BedeworkServerTags.synchXcategories.getLocalPart(),
                String.valueOf(val));
  }

  /** Get the process categories flag for synch
   *
   * @return boolean on/off
   */
  @NoDump
  public boolean getSynchXcategories() {
    return Boolean.parseBoolean(getProperty(
            BedeworkServerTags.synchXcategories.getLocalPart()));
  }

  /** Set the deletions suppressed flag for synch
   *
   * @param val true if we suppress deletions during synch
   */
  public void setSynchDeleteSuppressed(final boolean val) {
    setProperty(BedeworkServerTags.synchDeleteSuppressed.getLocalPart(),
                String.valueOf(val));
  }

  /** Get the deletions suppressed flag for synch
   *
   * @return boolean on/off
   */
  @NoDump
  public boolean getSynchDeleteSuppressed() {
    return Boolean.parseBoolean(getProperty(
            BedeworkServerTags.synchDeleteSuppressed.getLocalPart()));
  }

  /** Set the calendar timezone property
   *
   * @param val calendar timezone property
   */
  public void setTimezone(final String val) {
    if (val == null) {
      final BwProperty p =
              findProperty(CaldavTags.calendarTimezone.getLocalPart());
      if (p != null) {
        removeProperty(p);
      }
    } else {
      setProperty(CaldavTags.calendarTimezone.getLocalPart(), val);
    }
  }

  /** Get the calendar timezone property
   *
   * @return String vtimezone spec
   */
  @NoDump
  public String getTimezone() {
    return getProperty(CaldavTags.calendarTimezone.getLocalPart());
  }

  /** Set the subscription target type property
   *
   * @param val subscription target type
   */
  public void setSubscriptionTargetType(final String val) {
    if (val == null) {
      final String p = getQproperty(BedeworkServerTags.subscriptionTargetType);
      if (p != null) {
        removeQproperty(BedeworkServerTags.subscriptionTargetType);
      }
    } else {
      setQproperty(BedeworkServerTags.subscriptionTargetType, val);
    }
  }

  /** Get the subscription target type property
   *
   * @return String subscription target type
   */
  @NoDump
  public String getSubscriptionTargetType() {
    return getQproperty(BedeworkServerTags.subscriptionTargetType);
  }

  /** Set the location key property
   *
   * @param val location key
   */
  public void setLocationKey(final String val) {
    if (val == null) {
      final String p = getQproperty(XcalTags.xBedeworkLocationKey);
      if (p != null) {
        removeQproperty(XcalTags.xBedeworkLocationKey);
      }
    } else {
      setQproperty(XcalTags.xBedeworkLocationKey, val);
    }
  }

  /** Get the location key property
   *
   * @return String location key
   */
  @NoDump
  public String getLocationKey() {
    return getQproperty(XcalTags.xBedeworkLocationKey);
  }

  /** Set the orgsync public only property
   *
   * @param val orgsync public only property
   */
  public void setOrgSyncPublicOnly(final Boolean val) {
    if (val == null) {
      final String p = getQproperty(BedeworkServerTags.orgSyncPublicOnly);
      if (p != null) {
        removeQproperty(BedeworkServerTags.orgSyncPublicOnly);
      }
    } else {
      setQproperty(BedeworkServerTags.orgSyncPublicOnly, String.valueOf(val));
    }
  }

  /** Get the orgsync public only property property
   *
   * @return boolean subscription target type
   */
  @NoDump
  public boolean getOrgSyncPublicOnly() {
    return Boolean.parseBoolean(getQproperty(
            BedeworkServerTags.orgSyncPublicOnly));
  }

  /** Set the topical area property
   *
   * @param val topical area property
   */
  public void setIsTopicalArea(final boolean val) {
    setProperty(BedeworkServerTags.isTopicalArea.getLocalPart(),
                String.valueOf(val));
  }

  /** Get the topical area property
   *
   * @return boolean on/off
   */
  @NoDump
  public boolean getIsTopicalArea() {
    return Boolean.parseBoolean(getProperty(
         BedeworkServerTags.isTopicalArea.getLocalPart()));
  }

  /* ====================================================================
   *                   More property convenience methods
   * The above should be set like this as it namespaces the properties
   * ==================================================================== */

  /**
   * @param name QName
   * @param val its value
   */
  public void setQproperty(final QName name, final String val) {
    setProperty(NamespaceAbbrevs.prefixed(name), val);
  }

  /**
   * @param name QName
   * @return value or null
   */
  public String getQproperty(final QName name) {
    return getProperty(NamespaceAbbrevs.prefixed(name));
  }

  /**
   * @param name QName
   */
  public void removeQproperty(final QName name) {
    final BwProperty p = findProperty(NamespaceAbbrevs.prefixed(name));

    if (p != null) {
      removeProperty(p);
    }
  }

  /* ====================================================================
   *                   Event list methods
   * We store a list of event names in as many property entities as we need.
   * We limit the length of each entity and try to pack in a few more by
   * not duplicating paths.
   * ==================================================================== */

  static final String eventListProperty = "org.bedework.eventlist";
  static final int maxEventListSize = 2500;

  /** Set the event list property
   *
   * @param val event list
   */
  public void setEventList(final SortedSet<EventListEntry> val) {
    String currentPath = null;
    final List<String> vals = new ArrayList<>();
    StringBuilder cur = new StringBuilder();

    for (final EventListEntry ele: val) {
      final String p = ele.getPath() + "/";

      if (cur.length() > 0) {
        cur.append("\t");
      }

      if ((currentPath == null) || !currentPath.equals(p)) {
        // New path - store entire string
        cur.append(ele.getHref());
        currentPath = p;
      } else {
        // Same as prev path - just store the name part
        cur.append(ele.getName());
      }

      if (cur.length() >= maxEventListSize) {
        vals.add(cur.toString());
        currentPath = null;
        cur = new StringBuilder();
      }
    }

    if (cur.length() > 0) {
      vals.add(cur.toString());
    }

    final Set<BwProperty> bwprops = getProperties(eventListProperty);

    /* Put them in a list so I can get indexed elements */
    final List<BwProperty> props = new ArrayList<>();

    if (bwprops != null) {
      props.addAll(bwprops);
    }

    for (int i = 0; i < Math.max(vals.size(), props.size()); i++) {
      String s = null;
      if (i < vals.size()) {
        s = vals.get(i);
      }

      if (s == null) {
        // delete the next property
        removeProperty(props.get(i));
      } else if (i >= props.size()) {
        // Add a new property
        addProperty(new BwProperty(eventListProperty, s));
      } else {
        //Update the next property
        final BwProperty p = props.get(i);

        p.setValue(s);
      }
    }
  }

  /** Get the event list
   *
   * @return event list - never null
   */
  @NoDump
  public SortedSet<EventListEntry> getEventList() {
    final Set<BwProperty> props = getProperties(eventListProperty);
    final SortedSet<EventListEntry> res = new TreeSet<>();

    if (props == null) {
      return res;
    }

    for (final BwProperty prop: props) {
      final String[] vals = prop.getValue().split("\t");

      String curPath = null;

      for (final String s : vals) {
        if (s.startsWith("/")) {
          final EventListEntry ele = new EventListEntry(s);
          curPath = ele.getPath();
          res.add(ele);
        } else {
          res.add(new EventListEntry(curPath + s));
        }
      }
    }

    return res;
  }

  /* ====================================================================
   *                   db entity methods
   * ==================================================================== */

  @IcalProperty(pindex = PropertyInfoIndex.HREF,
          jname = "href",
          required = true,
          eventProperty = true,
          todoProperty = true,
          journalProperty = true)
  @Override
  public void setHref(final String val) {
    setPath(val);
  }

  @Override
  public String getHref() {
    return getPath();
  }

  /* ====================================================================
   *                   Non-db methods
   * ==================================================================== */

  /**
   * @return valid quoted etag
   */
  @NoDump
  @IcalProperty(pindex = PropertyInfoIndex.CTAG,
                jname = "ctag")
  public String getEtag() {
    return "\"" + getLastmod().getTagValue() +
           "\"";
  }

  /**
   * @return a version value in microseconds.
   */
  @NoDump
  @JsonIgnore
  public long getMicrosecsVersion() throws CalFacadeException {
    try {
      final var lm = getLastmod();
      final var micros =
              new LastModified(lm.getTimestamp()).getDate().getTime();
      final var seq = lm.getSequence();
      return micros * 1000000 + seq;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** true if this is to 'hold' calendar objects
   *
   * @return boolean  true if this is to 'hold' calendar objects
   */
  @NoDump
  public boolean getCalendarCollection() {
    return collectionInfo[getCalType()].onlyCalEntities;
  }

  /** true if this is a special collection
   *
   * @return boolean
   */
  @NoDump
  public boolean getSpecial() {
    return collectionInfo[getCalType()].special;
  }

  /** true if this can be the target of an alias
   *
   * @return boolean
   */
  @NoDump
  public boolean getCanAlias() {
    return collectionInfo[getCalType()].canAlias;
  }

  /** Set the aliased entity - this is usualy the end of the chain of
   * aliases, e.g. in a->b->c this would be c.
   *
   * @param val    BwCalendar object's alias target
   */
  public void setAliasTarget(final BwCalendar val) {
    aliasTarget = val;
  }

  /** Get the aliased entity
   *
   * @return BwCalendar   the object's alias target
   */
  @NoDump
  public BwCalendar getAliasTarget() {
    return aliasTarget;
  }

  /** Set the collection that was the root of the chain that referred
   * to this collection, e.g. in a->b->c this would be a.
   *
   * @param val    BwCalendar object's alias target
   */
  public void setAliasOrigin(final BwCalendar val) {
    aliasOrigin = val;
  }

  /** Get the aliased entity
   *
   * @return BwCalendar   the object's alias target
   */
  @NoDump
  public BwCalendar getAliasOrigin() {
    return aliasOrigin;
  }

  /** the aliased entity type
   *
   * @param val    type
   */
  public void setAliasCalType(final int val) {
    aliasCalType = val;
  }

  /**
   *
   * @return the aliased entity type
   */
  @NoDump
  public int getAliasCalType() {
    return aliasCalType;
  }

  /**
   * @param val   boolean true if the target is unreachable
   */
  public void setDisabled(final boolean val) {
    throw new RuntimeException("org.bedework.wrapper.method.called");
  }

  /**
   * @return boolean  true if the target is unreachable
   */
  @NoDump
  public boolean getDisabled() {
    return false;
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  /** return the end of the chain of aliases or this object if not an alias.
   * Assumes aliases have been resolved.
   *
   * @return boolean  true if this is some sort of alias.
   */
  @NoProxy
  @NoDump
  @JsonIgnore
  public BwCalendar getAliasedEntity() {
    BwCalendar coll = this;
    while ((coll != null) && coll.getInternalAlias()) {
      coll = coll.getAliasTarget();
    }

    return coll;
  }

  /** true if this is some sort of alias
   *
   * @return boolean  true if this is some sort of alias.
   */
  @NoProxy
  @NoDump
  public boolean getAlias() {
    return getInternalAlias() || getExternalSub();
  }

  /** true if this is an alias to an internal calendar.
   *
   * @return boolean  true if this is an alias to an internal calendar.
   */
  @NoProxy
  @NoDump
  @NoWrap
  public boolean getInternalAlias() {
    return getCalType() == calTypeAlias;
  }

  /** true if this is an alias to an external calendar.
   *
   * @return boolean  true if this is an alias to an external calendar.
   */
  @NoProxy
  @NoDump
  @NoWrap
  public boolean getExternalSub() {
    return getCalType() == calTypeExtSub;
  }


  /** Return path if this is an alias to an internal calendar.
   *
   * @return String path if this is an alias to an internal calendar otherwise null.
   */
  @NoProxy
  @NoDump
  public String getInternalAliasPath() {
    if (!getInternalAlias()) {
      return null;
    }

    final String uri = getAliasUri();
    if (uri == null) {
      return null;
    }

    if (uri.startsWith(CalFacadeDefs.bwUriPrefix)) {
      return uri.substring(CalFacadeDefs.bwUriPrefix.length());
    }

    return null;
  }

  /**
   *
   * @param val Collection of children objects
   */
  public void setChildren(final Collection<BwCalendar>  val) {
    children = val;
  }

  /**
   *
   * @return Collection of children objects
   */
  @NoProxy
  @NoDump
  public Collection<BwCalendar> getChildren() {
    return children;
  }

  /** Set list of referenced categories
   *
   * @param val list of category hrefs
   */
  @NoProxy
  @NoWrap
  @NoDump
  @Override
  public void setCategoryHrefs(final Set<String> val) {
    categoryHrefs = val;
  }

  /**
   *
   * @return list of category hrefs.
   */
  @NoProxy
  @NoWrap
  @NoDump
  @Override
  public Set<String> getCategoryHrefs() {
    return categoryHrefs;
  }

  /**
   * @return CollectionInfo for this entity
   */
  @NoDump
  public CollectionInfo getCollectionInfo() {
    return getCollectionInfo(getCalType());
  }

  /**
   * @param type of collection
   * @return CollectionInfo for an entity of the given type
   */
  @NoDump
  @NoWrap
  public CollectionInfo getCollectionInfo(final int type) {
    return collectionInfo[type];
  }

  /**
   * @return CollectionInfo for all types
   */
  @NoDump
  @NoWrap
  public static List<CollectionInfo> getAllCollectionInfo() {
    return roCollectionInfo;
  }

  /** Make this thing a tombstoned collection. Non-reversible
   */
  public void tombstone() {
    if (getTombstoned()) {
      return; // Already tombstoned
    }

    setFilterExpr(tombstonedFilter);

    // XXX Schema
    /* We have to change the name and the path to avoid conflicts -
     * currently the lastmod is linked via the path - better linked by id
     */

    /* I don't think this is true - the tombstoned version replaces the
       original - leave these alone
    setName(getName() + tombstonedSuffix);
    setPath(getPath() + tombstonedSuffix);
    getLastmod().setPath(getPath());
     */
  }

  /** Is this collection shared?
   *
   * @return true/false
   */
  @NoProxy
  @NoDump
  @NoWrap
  public boolean getShared() {
    return Boolean.parseBoolean(getQproperty(AppleServerTags.shared));
  }

  /** Mark this collection shared or unshared
   *
   * @param val true for shared
   */
  @NoProxy
  @NoDump
  @NoWrap
  public void setShared(final boolean val) {
    if (val) {
      setQproperty(AppleServerTags.shared, "true");
    } else {
      removeQproperty(AppleServerTags.shared);
    }
  }

  /** Is this collection shared with write access?
   *
   * @return true/false
   */
  @NoProxy
  @NoDump
  @NoWrap
  public boolean getSharedWritable() {
    return Boolean.parseBoolean(getQproperty(AppleServerTags.readWrite));
  }

  /** Mark this collection writable
   *
   * @param val true for writable
   */
  @NoProxy
  @NoDump
  @NoWrap
  public void setSharedWritable(final boolean val) {
    if (val) {
      setQproperty(AppleServerTags.readWrite, "true");
    } else {
      removeQproperty(AppleServerTags.readWrite);
    }
  }

  /** Is this collection shared?
   *
   * @return true/false
   */
  @NoProxy
  @NoDump
  @NoWrap
  public boolean getTombstoned() {
    final String f = getFilterExpr();
    return (f != null) && f.equals(tombstonedFilter);
  }

  /** Check a possible name for validity
   *
   * @param val name to check
   * @return  boolean true for valid calendar name
   */
  public static boolean checkName(final String val) {
    if ((val == null) || (val.length() == 0)) {
      return false;
    }

    /* First character - letter or digit  */

    if (!Character.isLetterOrDigit(val.charAt(0))) {
      return false;
    }

    for (int i = 1; i < val.length(); i++) {
      final char ch = val.charAt(i);

      if (!Character.isLetterOrDigit(ch) &&
          (ch != '_') && (ch != ' ')) {
        return false;
      }
    }

    return true;
  }

  /** Generate an encoded url referring to this calendar.
   *
   * XXX This should not be here
   * @return String encoded url (or path)
   */
  @NoDump
  public String getEncodedPath() {
    if (getPath() == null) {
      return null;
    }
    try {
      return URLEncoder.encode(getPath(), StandardCharsets.UTF_8);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /** Update last mod fields
   * @param val timestamp
   */
  public void updateLastmod(final Timestamp val) {
    getLastmod().updateLastmod(val);
  }

  /** Set the single valued named property
   *
   * @param name of property
   * @param val of property
   */
  public void setProperty(final String name, final String val) {
    BwProperty prop = findProperty(name);

    if (prop == null) {
      if (val != null) {
        prop = new BwProperty(name, val);
        addProperty(prop);
      }
    } else if (val == null) {
      removeProperty(prop);
    } else {
      prop.setValue(val);
    }
  }

  /** Get the single valued named property
   *
   * @param name of property
   * @return String property value
   */
  public String getProperty(final String name) {
    final BwProperty prop = findProperty(name);

    if (prop == null) {
      return null;
    }

    return prop.getValue();
  }

  /* ====================================================================
   *                   Wrapper methods
   * ==================================================================== */

  /**
   *
   * @param val CurrentAccess
   */
  public void setCurrentAccess(final CurrentAccess val) {
    throw new RuntimeException("org.bedework.wrapper.method.called");
  }

  /**
   * @return CurrentAccess
   */
  @NoDump
  @JsonIgnore
  public CurrentAccess getCurrentAccess() {
    throw new RuntimeException("org.bedework.wrapper.method.called");
  }

  /**
   * @param val virtual path for searches
   */
  public void setVirtualPath(final String val) {
    throw new RuntimeException("org.bedework.wrapper.method.called");
  }

  /**
   * @return virtual path for searches
   */
  @NoDump
  public String getVirtualPath() {
    // Allow this - may be called while creating?
    return null;
  }

  /**
   * @param val ui open state
   */
  public void setOpen(final boolean val) {
    throw new RuntimeException("org.bedework.wrapper.method.called");
  }

  /**
   * @return ui open state
   */
  @NoDump
  public boolean getOpen() {
    // Allow this - may be called while creating?
    return false;
  }

  /* ====================================================================
   *                   CollatableEntity methods
   * ==================================================================== */

  @Override
  @NoDump
  public String getCollateValue() {
    return getName();
  }

  /**
   * @return a copy for tombstoning.
   */
  public BwCalendar makeTombstoneCopy() {
    final BwCalendar col = new BwCalendar();

    super.copyTo(col);

    col.setName(getName());
    col.setPath(getPath());

    col.setCalType(getCalType());
    col.setCreated(getCreated());
    col.setLastmod((BwCollectionLastmod)getLastmod().clone());

    col.setAliasUri(getAliasUri());

    return col;
  }

  /* ====================================================================
   *                   Restore callback
   * ==================================================================== */

  private static FromXmlCallback fromXmlCb;

  @NoDump
  public static FromXmlCallback getRestoreCallback() {
    if (fromXmlCb == null) {
      fromXmlCb = new FromXmlCallback() {
        @Override
        public boolean save(final Element el,
                            final Object theObject,
                            final Object theValue) {
          if ("col-lastmod".equals(el.getTagName())) {
            ((BwCalendar)theObject).setLastmod(
                    (BwCollectionLastmod)theValue);
            return true;
          }

          return false;
        }
      };

      fromXmlCb.addClassForName("col-lastmod",
                                BwCollectionLastmod.class);
      fromXmlCb.addClassForName("property", BwProperty.class);

      fromXmlCb.addSkips("byteSize",
                         "id",
                         "seq");

      fromXmlCb.addMapField("col-lastmod", "lastmod");
      fromXmlCb.addMapField("public", "publick");
    }
    
    return fromXmlCb;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int compareTo(final BwCalendar that) {
    if (that == this) {
      return 0;
    }

    if (that == null) {
      return -1;
    }

    return getPath().compareTo(that.getPath());
  }

  @Override
  public int hashCode() {
    if (getPath() == null) {
      return 1;
    }
    return getPath().hashCode();
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);
    ts.append("name", getName());
    ts.append("path", getPath());
    ts.append("tombstoned", getTombstoned());
    ts.append("displayName", getSummary());
    ts.newLine();
    ts.append("description", getDescription());
//    ts.append("mailListId", getMailListId());
    ts.append("calendarCollection", getCalendarCollection());
    ts.append("calType", getCalType());

    /* Forces fetch
    if (hasChildren()) {
      sb.append(",\nchildren(");

      boolean donech = false;

      for (BwCalendar ch: getChildren()) {
        if (!donech) {
          donech = true;
        } else {
          sb.append(",\n");
        }

        sb.append(ch.getPath());
      }
      sb.append(")");
    }
    */

    ts.append("created", getCreated());
    ts.append("lastmod", getLastmod());

    if (getNumCategories() > 0) {
      ts.append("categories",  getCategories());
    }

    return ts.toString();
  }

  @Override
  public Object clone() {
    final BwCalendar cal = shallowClone();

    cal.setCategories(cloneCategories());
    cal.setProperties(cloneProperties());

    return cal;
  }

  /** Used to provide a new wrapper for an entity
   * 
   * @return Same entity with new wrapper
   */
  public BwCalendar cloneWrapper() {
    throw new RuntimeException("org.bedework.wrapper.method.called");
  }

  public BwCalendar shallowClone() {
    final BwCalendar cal = new BwCalendar();

    super.copyTo(cal);

    cal.setName(getName());
    cal.setPath(getPath());

    cal.setSummary(getSummary());
    cal.setDescription(getDescription());
    cal.setMailListId(getMailListId());
    cal.setCalType(getCalType());
    cal.setCreated(getCreated());

    final BwCollectionLastmod lm = (BwCollectionLastmod)getLastmod().clone();
    lm.setDbEntity(cal);
    cal.setLastmod(lm);

    cal.setAliasUri(getAliasUri());
    cal.setDisplay(getDisplay());
    cal.setAffectsFreeBusy(getAffectsFreeBusy());
    cal.setIgnoreTransparency(getIgnoreTransparency());
    cal.setUnremoveable(getUnremoveable());
    cal.setRefreshRate(getRefreshRate());
    cal.setLastRefresh(getLastRefresh());
    cal.setLastEtag(getLastEtag());
    cal.setFilterExpr(getFilterExpr());
    
    if (!Util.isEmpty(getCategoryHrefs())) {
      final Set<String> uids = new TreeSet<>(getCategoryHrefs());
      cal.setCategoryHrefs(uids);
    }

    return cal;
  }

  private static CollectionInfo ci(final int collectionType,
                                   final boolean special,
                                   final boolean childrenAllowed,
                                   final boolean indexable,
                                   final boolean uniqueKey,
                                   final boolean allowAnnotations,
                                   final boolean allowFreeBusy,
                                   final boolean canAlias,
                                   final boolean onlyCalEntities,
                                   final boolean scheduling,
                                   final boolean shareable,
                                   final boolean provision) {
    return new CollectionInfo(collectionType,
                              special,
                              childrenAllowed,
                              indexable,
                              uniqueKey,
                              allowAnnotations,
                              allowFreeBusy,
                              canAlias,
                              onlyCalEntities,
                              scheduling,
                              shareable,
                              provision);
  }

}
