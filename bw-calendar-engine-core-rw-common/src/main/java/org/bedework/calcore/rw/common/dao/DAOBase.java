package org.bedework.calcore.rw.common.dao;

import org.bedework.base.exc.BedeworkException;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.database.db.DbSession;
import org.bedework.util.logging.Logged;

public interface DAOBase extends Logged {
  /**
   * @param sess the session
   */
  void init(DbSession sess);

  /**
   *
   * @return a unique name for the instance.
   */
  String getName();

  void setSess(DbSession val);

  DbSession getSess();

  DbSession createQuery(String query);

  void rollback();

  DbSession add(BwUnversionedDbentity<?> val);

  DbSession update(BwUnversionedDbentity<?> val);

  DbSession delete(BwUnversionedDbentity<?> val);

  BwUnversionedDbentity<?> merge(BwUnversionedDbentity<?> val);

  void throwException(BedeworkException be);

  void throwException(String pname);

  void throwException(String pname, String extra);
}
