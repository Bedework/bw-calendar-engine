package org.bedework.calcore.rw.common.dao;

import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;

import java.util.List;

public interface CoreEventPropertiesDAO extends DAOBase {
  List<BwEventProperty<?>> getAll(String ownerHref);

  BwEventProperty<?> get(String uid);

  void delete(BwEventProperty<?> val);

  List<EventPropertiesReference> getRefs(BwEventProperty<?> val);

  long getRefsCount(BwEventProperty<?> val);

  BwEventProperty<?> find(BwString val,
                       String ownerHref);

  void checkUnique(BwString val,
                   String ownerHref);
}
