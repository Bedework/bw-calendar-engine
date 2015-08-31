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

import org.bedework.calcore.AccessUtil;
import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.calcorei.HibSession;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;
import org.bedework.calfacade.exc.CalFacadeException;

import java.util.Collection;
import java.util.List;

/** Implementation of core event properties interface.
 *
 * @author Mike Douglass    douglm  rpi.edu
 * @version 1.0
 * @param <T> type of property, Location, contact etc.
 */
public class CoreEventProperties <T extends BwEventProperty>
        extends CalintfHelperHib implements CoreEventPropertiesI<T> {
  private String className;

  /* This was easier with named queries */
  private static class ClassString {
    private String catQuery;
    private String contactQuery;
    private String locQuery;

    ClassString(final String catQuery,
                final String contactQuery,
                final String locQuery){
      this.catQuery = catQuery;
      this.contactQuery = contactQuery;
      this.locQuery = locQuery;
    }

    String get(final String className) {
      if (className.equals(BwCategory.class.getName())) {
        return catQuery;
      }

      if (className.equals(BwContact.class.getName())) {
        return contactQuery;
      }

      if (className.equals(BwLocation.class.getName())) {
        return locQuery;
      }

      throw new RuntimeException("Should never happen");
    }
  }

  private static final ClassString refsQuery;

  private static final ClassString refsCountQuery;

  private static final ClassString delPrefsQuery;

  private static final ClassString keyFields;

  private static final ClassString finderFields;

  static {
    refsQuery = new ClassString(
         "select new org.bedework.calfacade.EventPropertiesReference(ev.colPath, ev.uid)" +
             "from org.bedework.calfacade.BwEvent as ev " +
             "where :ent in elements(ev.categories)",

         "select new org.bedework.calfacade.EventPropertiesReference(ev.colPath, ev.uid)" +
             "from org.bedework.calfacade.BwEvent as ev " +
             "where :ent in elements(ev.contacts)",

         "select new org.bedework.calfacade.EventPropertiesReference(ev.colPath, ev.uid)" +
             "from org.bedework.calfacade.BwEvent as ev " +
             "where ev.location = :ent"
                            );
    refsCountQuery = new ClassString(
         "select count(*) from org.bedework.calfacade.BwEvent as ev " +
           "where :ent in elements(ev.categories)",

         "select count(*) from org.bedework.calfacade.BwEvent as ev " +
           "where :ent in elements(ev.contacts)",

         "select count(*) from org.bedework.calfacade.BwEvent as ev " +
           "where ev.location = :ent");

    delPrefsQuery = new ClassString(
        "delete from org.bedework.calfacade.svc.prefs.BwAuthUserPrefsCategory " +
               "where categoryid=:id",

        "delete from org.bedework.calfacade.svc.prefs.BwAuthUserPrefsContact " +
               "where contactid=:id",

        "delete from org.bedework.calfacade.svc.prefs.BwAuthUserPrefsLocation " +
               "where locationid=:id");

    keyFields = new ClassString("word",
                                "uid",
                                "uid");

    finderFields = new ClassString("word",
                                   "cn",
                                   "address");

  }

  private String keyFieldName;

  private String finderFieldName;

  /** Constructor
   *
   * @param chcb
   * @param cb
   * @param access
   * @param currentMode
   * @param sessionless
   * @param className
   */
  public CoreEventProperties(final CalintfHelperHibCb chcb,
                             final Callback cb,
                             final AccessUtil access,
                             final int currentMode,
                             final boolean sessionless,
                             final String className) {
    super(chcb);
    super.init(cb, access, currentMode, sessionless);

    this.className = className;

    keyFieldName = keyFields.get(className);
    finderFieldName = finderFields.get(className);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcore.CalintfHelper#startTransaction()
   */
  @Override
  public void startTransaction() throws CalFacadeException {
  }

  /* (non-Javadoc)
   * @see org.bedework.calcore.CalintfHelper#endTransaction()
   */
  @Override
  public void endTransaction() throws CalFacadeException {
  }

  @SuppressWarnings("unchecked")
  @Override
  public Collection<T> getAll(final String ownerHref) throws CalFacadeException {
    HibSession sess = getSess();

    StringBuilder qstr = new StringBuilder("from ");
    qstr.append(className);
    qstr.append(" ent where ");
    if (ownerHref != null) {
      qstr.append(" ent.ownerHref=:ownerHref");
    }

    qstr.append(" order by ent.");
    qstr.append(keyFieldName);

    sess.createQuery(qstr.toString());

    if (debug) {
      debugMsg("getAll: q=" + qstr.toString() + " owner=" + ownerHref);
    }

    if (ownerHref != null) {
      sess.setString("ownerHref", ownerHref);
    }

    final List eps = sess.getList();

    final Collection c = access.checkAccess(eps, privRead, true);

    if (debug) {
      debugMsg("getAll: found: " + eps.size() + " returning: " + c.size());
    }

    return c;
  }

  @SuppressWarnings("unchecked")
  @Override
  public T get(final String uid) throws CalFacadeException {
    HibSession sess = getSess();

    StringBuilder qstr = new StringBuilder("from ");
    qstr.append(className);
    qstr.append(" ent where uid=:uid");

    sess.createQuery(qstr.toString());

    sess.setString("uid", uid);

    return check((T)sess.getUnique());
  }

  @SuppressWarnings("unchecked")
  @Override
  public T find(final BwString val,
                final String ownerHref) throws CalFacadeException {
    findQuery(false, val, ownerHref);

    return check((T)getSess().getUnique());
  }

  @Override
  public void checkUnique(final BwString val,
                          final String ownerHref) throws CalFacadeException {
    HibSession sess = getSess();

    findQuery(true, val, ownerHref);

    @SuppressWarnings("unchecked")
    Collection<Long> counts = sess.getList();
    if (counts.iterator().next() > 1) {
      sess.rollback();
      throw new CalFacadeException("org.bedework.duplicate.object");
    }
  }

  @Override
  public void deleteProp(final T val) throws CalFacadeException {
    HibSession sess = getSess();

    @SuppressWarnings("unchecked")
    BwEventProperty v = (T)sess.merge(val);

    sess.createQuery(delPrefsQuery.get(className));
    sess.setInt("id", v.getId());
    sess.executeUpdate();

    sess.delete(v);
  }

  @Override
  public List<EventPropertiesReference> getRefs(final T val)
                          throws CalFacadeException {
    List<EventPropertiesReference> refs = getRefs(val,
                                                  refsQuery.get(className));

    /* The parameterization doesn't quite cut it for categories. They can appear
     * on collections as well
     */
    if (val instanceof BwCategory) {
      refs.addAll(getRefs(val,
                          "select new org.bedework.calfacade.EventPropertiesReference(col.path) " +
                              "from org.bedework.calfacade.BwCalendar as col " +
                              "where :ent in elements(col.categories)"));
    }

    return refs;
  }

  @SuppressWarnings("unchecked")
  private List<EventPropertiesReference> getRefs(final T val,
                                                final String query)
                                                            throws CalFacadeException {
    HibSession sess = getSess();

    sess.createQuery(query);
    sess.setEntity("ent", val);

    /* May get multiple counts back for events and annotations. */
    List<EventPropertiesReference> refs = sess.getList();

    if (debug) {
      trace(" ----------- count = " + refs.size());
    }

    return refs;
  }

  @Override
  public long getRefsCount(final T val) throws CalFacadeException {
    long total = getRefsCount(val, refsCountQuery.get(className));

    /* The parameterization doesn't quite cut it for categories. They can appear
     * on collections as well
     */
    if (val instanceof BwCategory) {
      total += getRefsCount(val,
                            "select count(*) from org.bedework.calfacade.BwCalendar as col " +
                               "where :ent in elements(col.categories)");
    }

    return total;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private long getRefsCount(final T val,
                            final String query) throws CalFacadeException {
    HibSession sess = getSess();

    sess.createQuery(query);
    sess.setEntity("ent", val);

    /* May get multiple counts back for events and annotations. */
    @SuppressWarnings("unchecked")
    Collection<Long> counts = sess.getList();

    long total = 0;

    if (debug) {
      trace(" ----------- count = " + counts.size());
      if (counts.size() > 0) {
        trace(" ---------- first el class is " + counts.iterator().next().getClass().getName());
      }
    }

    for (Long l: counts) {
      total += l;
    }

    return total;
  }

  private T check(final T ent) throws CalFacadeException {
    if (ent == null) {
      return null;
    }

    ent.fixNames(getSyspars(), getPrincipal());

    /*  XXX This is wrong but it's always been like this.
        For scheduling we end up with other users categories embedded
        in attendees events.

        This will probbaly get fixed when scheduling is moved out of
        the core.
    if (ent.getPublick()) {
      return ent;
    }

    if (this.getPrincipal().getPrincipalRef().equals(ent.getOwnerHref())) {
      return ent;
    }

    return null;
     */
    return ent;
  }

  private void findQuery(final boolean count,
                         final BwString val,
                         final String ownerHref) throws CalFacadeException {
    if (val == null) {
      throw new CalFacadeException("Missing key value");
    }

    if (ownerHref == null) {
      throw new CalFacadeException("Missing owner value");
    }

    HibSession sess = getSess();

    StringBuilder qstr = new StringBuilder();
    if (count) {
      qstr.append("select count(*) ");
    }

    qstr.append("from ");
    qstr.append(className);
    qstr.append(" ent where ");
    addBwStringKeyTerms(val, finderFieldName, qstr);
    qstr.append("and ent.ownerHref=:ownerHref");

    sess.createQuery(qstr.toString());

    addBwStringKeyvals(val);

    sess.setString("ownerHref", ownerHref);
  }

  private void addBwStringKeyTerms(final BwString key,
                                   final String keyName,
                                   final StringBuilder sb) throws CalFacadeException {
    sb.append("((ent.");
    sb.append(keyName);
    sb.append(".lang");

    if (key.getLang() == null) {
      sb.append(" is null) and");
    } else {
      sb.append("=:langval) and");
    }

    sb.append("(ent.");
    sb.append(keyName);
    sb.append(".value");

    if (key.getValue() == null) {
      sb.append(" is null)) ");
    } else {
      sb.append("=:val)) ");
    }
  }

  private void addBwStringKeyvals(final BwString key) throws CalFacadeException {
    HibSession sess = getSess();

    if (key.getLang() != null) {
      sess.setString("langval", key.getLang());
    }

    if (key.getValue() != null) {
      sess.setString("val", key.getValue());
    }
  }
}
