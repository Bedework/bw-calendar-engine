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

package org.bedework.calcore.hibernate.daoimpl;

import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.rw.common.dao.EventPropertiesDAO;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;
import org.bedework.database.db.DbSession;

import java.util.Collection;
import java.util.List;

/** Implementation of core event properties interface.
 *
 * @author Mike Douglass    douglm  rpi.edu
 * @version 1.0
 */
public class CoreEventPropertiesDAOImpl extends DAOBaseImpl
        implements EventPropertiesDAO {
  private final String entityName;
  private final Class<?> cl;

  /* This was easier with named queries */
  private static class ClassString {
    private final String catQuery;
    private final String contactQuery;
    private final String locQuery;

    ClassString(final String catQuery,
                final String contactQuery,
                final String locQuery){
      this.catQuery = catQuery;
      this.contactQuery = contactQuery;
      this.locQuery = locQuery;
    }

    String get(final Class<?> cl) {
      if (cl.equals(BwCategory.class)) {
        return catQuery;
      }

      if (cl.equals(BwContact.class)) {
        return contactQuery;
      }

      if (cl.equals(BwLocation.class)) {
        return locQuery;
      }

      throw new BedeworkException("Should never happen");
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
                    "from BwEvent ev " +
                    "where :ent in elements(ev.categories)",

            "select new org.bedework.calfacade.EventPropertiesReference(ev.colPath, ev.uid)" +
                    "from BwEvent ev " +
                    "where :ent in elements(ev.contacts)",

            "select new org.bedework.calfacade.EventPropertiesReference(ev.colPath, ev.uid)" +
                    "from BwEvent ev " +
                    "where ev.location = :ent"
    );
    refsCountQuery = new ClassString(
            "select count(*) from BwEvent ev " +
                    "where :ent in elements(ev.categories)",

            "select count(*) from BwEvent ev " +
                    "where :ent in elements(ev.contacts)",

            "select count(*) from BwEvent ev " +
                    "where ev.location = :ent");

    delPrefsQuery = new ClassString(
            "delete from BwAuthUserPrefsCategory" +
                    " where categoryid=:id",

            "delete from BwAuthUserPrefsContact" +
                    " where contactid=:id",

            "delete from BwAuthUserPrefsLocation" +
                    " where locationid=:id");

    keyFields = new ClassString("word",
                                "uid",
                                "uid");

    finderFields = new ClassString("word",
                                   "cn",
                                   "address");

  }

  private final String keyFieldName;

  private final String finderFieldName;

  /** Constructor
   *
   * @param sess the session
   * @param cl class we act for
   */
  public CoreEventPropertiesDAOImpl(final DbSession sess,
                                    final Class<?> cl) {
    super(sess);

    this.cl = cl;
    entityName = cl.getSimpleName();

    keyFieldName = keyFields.get(cl);
    finderFieldName = finderFields.get(cl);
  }

  @Override
  public String getName() {
    return CoreEventPropertiesDAOImpl.class.getName() + "-" + entityName;
  }

  private String getAllQuery;

  @SuppressWarnings("unchecked")
  @Override
  public List<BwEventProperty<?>> getAll(final String ownerHref) {
    if (getAllQuery == null) {
      getAllQuery = "from " + entityName + " ent " +
              "where ent.ownerHref=:ownerHref " +
              "order by ent." + keyFieldName;
    }
    
    final var sess = getSess();

    sess.createQuery(getAllQuery);

    if (debug()) {
      debug("getAll: q=" + getAllQuery + " owner=" + ownerHref);
    }

    sess.setString("ownerHref", ownerHref);

    return (List<BwEventProperty<?>>)sess.getList();
  }

  private String getQuery;

  @Override
  public BwEventProperty<?> get(final String uid) {
    if (getQuery == null) {
      getQuery = "from " + entityName + " ent " +
              "where uid=:uid";
    }

    final var sess = getSess();

    sess.createQuery(getQuery);

    sess.setString("uid", uid);

    return (BwEventProperty<?>)sess.getUnique();
  }

  @Override
  public void delete(final BwEventProperty<?> val) {
    final var sess = getSess();

    final var v = (BwEventProperty<?>)sess.merge(val);

    sess.createQuery(delPrefsQuery.get(cl));
    sess.setInt("id", v.getId());
    sess.executeUpdate();

    sess.delete(v);
  }

  @Override
  public List<EventPropertiesReference> getRefs(
          final BwEventProperty<?> val) {
    final List<EventPropertiesReference> refs =
            getRefs(val, refsQuery.get(cl));

    /* The parameterization doesn't quite cut it for categories. They can appear
     * on collections as well
     */
    if (val instanceof BwCategory) {
      refs.addAll(getRefs(val,
                          "select new org.bedework.calfacade.EventPropertiesReference(col.path) " +
                                  "from BwCollection col " +
                                  "where :ent in elements(col.categories)"));
    }

    return refs;
  }

  @Override
  public long getRefsCount(final BwEventProperty<?> val) {
    long total = getRefsCount(val, refsCountQuery.get(cl));

    /* The parameterization doesn't quite cut it for categories. They can appear
     * on collections as well
     */
    if (val instanceof BwCategory) {
      total += getRefsCount(val,
                            "select count(*) from BwCollection col " +
                                    "where :ent in elements(col.categories)");
    }

    return total;
  }
  
  private String findQuery;
  private String findCountQuery;

  @Override
  public BwEventProperty<?> find(final BwString val,
                                 final String ownerHref) {
    if (findQuery == null) {
      findQuery = "from " + entityName + " ent where ";
    }

    doFind(findQuery, val, ownerHref);
    return (BwEventProperty<?>)getSess().getUnique();
  }

  @Override
  public void checkUnique(final BwString val,
                          final String ownerHref) {
    if (findCountQuery == null) {
      findCountQuery =
              "select count(*) from " + entityName + " ent where ";
    }

    doFind(findCountQuery, val, ownerHref);

    final var sess = getSess();

    //noinspection unchecked
    final Collection<Long> counts = (Collection<Long>)sess.getList();
    if (counts.iterator().next() > 1) {
      sess.rollback();
      throw new BedeworkException("org.bedework.duplicate.object");
    }
  }

  /* ============================================================
   *                   Private methods
   * ============================================================ */

  private void doFind(final String qpfx,
                      final BwString val,
                      final String ownerHref) {
    if (val == null) {
      throw new BedeworkException("Missing key value");
    }

    if (ownerHref == null) {
      throw new BedeworkException("Missing owner value");
    }

    final var sess = getSess();

    final StringBuilder qstr = new StringBuilder(qpfx);

    addBwStringKeyTerms(val, finderFieldName, qstr);
    qstr.append("and ent.ownerHref=:ownerHref");

    sess.createQuery(qstr.toString());

    addBwStringKeyvals(val);

    sess.setString("ownerHref", ownerHref);
  }

  @SuppressWarnings("unchecked")
  private List<EventPropertiesReference> getRefs(final BwEventProperty<?> val,
                                                 final String query) {
    final var sess = getSess();

    sess.createQuery(query);
    sess.setEntity("ent", val);

    /* May get multiple counts back for events and annotations. */
    final List<EventPropertiesReference> refs = (List<EventPropertiesReference>)sess.getList();

    if (debug()) {
      debug(" ----------- count = " + refs.size());
    }

    return refs;
  }

  private long getRefsCount(final BwEventProperty<?> val,
                            final String query) {
    final var sess = getSess();

    sess.createQuery(query);
    sess.setEntity("ent", val);

    /* May get multiple counts back for events and annotations. */
    @SuppressWarnings("unchecked")
    final Collection<Long> counts = (Collection<Long>)sess.getList();

    long total = 0;

    if (debug()) {
      debug(" ----------- count = " + counts.size());
      if (!counts.isEmpty()) {
        debug(" ---------- first el class is " + counts.iterator().next().getClass().getName());
      }
    }

    for (final Long l: counts) {
      total += l;
    }

    return total;
  }

  private void addBwStringKeyTerms(final BwString key,
                                   final String keyName,
                                   final StringBuilder sb) {
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

  private void addBwStringKeyvals(final BwString key) {
    final var sess = getSess();

    if (key.getLang() != null) {
      sess.setString("langval", key.getLang());
    }

    if (key.getValue() != null) {
      sess.setString("val", key.getValue());
    }
  }
}
