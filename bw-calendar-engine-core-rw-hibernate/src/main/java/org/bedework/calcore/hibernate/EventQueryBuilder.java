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
package org.bedework.calcore.hibernate;

import org.bedework.base.exc.BedeworkBadDateException;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.database.db.DbSession;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.misc.Util;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * @author Mike Douglass
 */
public class EventQueryBuilder implements Serializable, CalintfDefs {
  private final StringBuilder sb = new StringBuilder();
  private DbSession sess;

  public void fields(final List<BwIcalPropertyInfoEntry> retrieveListFields,
                     final String name,
                     final boolean isAnnotation) {
    if (Util.isEmpty(retrieveListFields)) {
      sb.append(name);
      sb.append(" ");
      return;
    }

    String delim = "";
    for (final BwIcalPropertyInfoEntry ipie: retrieveListFields) {
      sb.append(delim);

      if (ipie.getMultiValued()) {
        sb.append("joined_");
      } else {
        sb.append(name);
        sb.append(".");
      }

      sb.append(ipie.getDbFieldName());
      delim = ", ";
    }

    if (isAnnotation) {
      sb.append(delim);
      sb.append(name);
      sb.append(".");
      sb.append("override");

      sb.append(delim);
      sb.append(name);
      sb.append(".");
      sb.append("target");

      sb.append(delim);
      sb.append(name);
      sb.append(".");
      sb.append("master");
    }

    sb.append(" ");
  }

  public void from() {
    sb.append("from ");
  }

  public void addClass(final Class<?> cl, final String name) {
    sb.append(cl.getName());
    sb.append(" ");
    sb.append(name);
  }

  public void where() {
    sb.append(" where ");
  }

  public void and() {
    if (sb.isEmpty()) {
      return;
    }

    sb.append(" and ");
  }

  public void paren(final Object val) {
    append("(");
    sb.append(val);
    append(")");
  }

  /* Just encapsulate the date restrictions.
   * If both dates are null just return. Otherwise return the appropriate
   * terms with the ids: <br/>
   * fromDate    -- first day
   * toDate      -- last day
   *
   * We build two near identical terms.
   *
   *   (floatFlag=true AND <drtest>) OR
   *   (floatFlag is null AND <drtest-utc>)
   *
   * where drtest uses the local from and to times and
   *       drtest-utc uses the utc from and to.
   *
   * @return boolean true if we appended something
   */
  public boolean appendDateTerms(final String evname,
                                 final BwDateTime from, final BwDateTime to,
                                 final boolean withAnd,
                                 final boolean instances) {
    if ((from == null) && (to == null)) {
      return false;
    }

    if (withAnd) {
      and();
    }

    // No date check on the VAVAILABILITY
    sb.append("(");
    if (!instances) {
      sb.append(evname);
      sb.append(".entityType=");
      sb.append(IcalDefs.entityTypeVavailability);
      sb.append(" or ");
    }
    appendDrtestTerms(true, evname, from, to);
    sb.append(" or ");
    appendDrtestTerms(false, evname, from, to);
    sb.append(")");

    return true;
  }

  public void append(final Object val) {
    sb.append(val);
  }

  @Override
  public String toString() {
    return sb.toString();
  }

  /** Generate a where clause term for a query which handles the
   * public/not public and creator.
   *
   * @param entName   String name of the entity whose values we are matching
   * @param publick   true for public access
   * @param ignoreCreator  true if we can ignore the creator (owner)
   * @return boolean  true if we need to set the :user term
   */
  public boolean appendPublicOrOwnerTerm(final String entName,
                                         final boolean publick,
                                         final boolean ignoreCreator) {
    final boolean all = publick || ignoreCreator;
    boolean setUser = false;

    and();
    sb.append(" (");
    if (!all) {
      sb.append("(");
    }

    sb.append(entName)
      .append(".publick=")
      .append(publick);

    if (!all) {
      sb.append(") and (")
        .append(entName)
        .append(".ownerHref=:userHref")
        .append(")");
      setUser = true;
    }
    sb.append(")");

    return setUser;
  }

  private void appendDrtestTerms(final boolean floatingTest,
                                 final String evname,
                                 final BwDateTime from, final BwDateTime to) {
    final String startField = evname + ".dtstart.date";
    final String endField = evname + ".dtend.date";

    final String startFloatTest;
    final String endFloatTest;
    final String toVal;
    final String fromVal;

    if (floatingTest) {
      startFloatTest = evname + ".dtstart.floatFlag=true and ";
      endFloatTest = evname + ".dtend.floatFlag=true and ";
      toVal = "toFltDate";
      fromVal = "fromFltDate";
    } else {
      startFloatTest = evname + ".dtstart.floatFlag is null and ";
      endFloatTest = evname + ".dtend.floatFlag is null and ";
      toVal = "toDate";
      fromVal = "fromDate";
    }

    /* Note that the comparisons below are required to ensure that the
     *  start date is inclusive and the end date is exclusive.
     * From CALDAV:
     * A VEVENT component overlaps a given time-range if:
     *
     * (DTSTART <= start AND DTEND > start) OR
     * (DTSTART <= start AND DTSTART+DURATION > start) OR
     * (DTSTART >= start AND DTSTART < end) OR
     * (DTEND   > start AND DTEND < end)
     *
     *  case 1 has the event starting between the dates.
     *  case 2 has the event ending between the dates.
     *  case 3 has the event starting before and ending after the dates.
     */

    if (from == null) {
      sb.append("(")
        .append(startFloatTest)
        .append(startField)
        .append(" < :")
        .append(toVal)
        .append(")");
      return;
    }

    if (to == null) {
      sb.append("(")
        .append(endFloatTest)
        .append(endField)
        .append(" >= :")
        .append(fromVal)
        .append(")");
      return;
    }

    sb.append("(");
    sb.append(startFloatTest); // XXX Inadequate?? - should check each field separately?
    sb.append("(");

    sb.append(startField);
    sb.append(" < :");
    sb.append(toVal);
    sb.append(") and ((");

    sb.append(endField);
    sb.append(" > :");
    sb.append(fromVal);
    sb.append(") or ((");

    sb.append(startField);
    sb.append("=");
    sb.append(endField);
    sb.append(") and (");
    sb.append(endField);
    sb.append(" >= :");
    sb.append(fromVal);
    sb.append("))))");

    /*
    ((start < to) and ((end > from) or
      ((start = end) and (end >= from))))
     */
  }

  public void emitCollectionClause(final String qevName,
                                   final Collection<String> colPaths) {
    sb.append("(");
    sb.append(qevName);
    sb.append(".colPath in (:colPaths))");
  }

  public void createQuery(final DbSession sess) {
    this.sess = sess;

    sess.createQuery(sb.toString());
  }

  public void setDateTermValues(final BwDateTime startDate,
                         final BwDateTime endDate) {
    if (startDate != null) {
      sess.setString("fromDate", startDate.getDate());
      sess.setString("fromFltDate", makeUtcformat(startDate.getDtval()));
    }

    if (endDate != null) {
      sess.setString("toDate", endDate.getDate());
      sess.setString("toFltDate", makeUtcformat(endDate.getDtval()));
    }
  }

  private String makeUtcformat(final String dt) {
    final int len = dt.length();

    if (len == 16) {
      return dt;
    }

    if (len == 15) {
      return dt + "Z";
    }

    if (len == 8) {
      return dt + "T000000Z";
    }

    try {
      sess.rollback();
    } catch (final Throwable ignored) {}
    throw new BedeworkBadDateException("Bad date " + dt);
  }
}
