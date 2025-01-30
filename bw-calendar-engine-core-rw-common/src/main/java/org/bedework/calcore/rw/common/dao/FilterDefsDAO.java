package org.bedework.calcore.rw.common.dao;

import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwPrincipal;

import java.util.Collection;

public interface FilterDefsDAO extends DAOBase {
  Collection<BwFilterDef> getAllFilterDefs(BwPrincipal<?> owner);

  BwFilterDef fetch(String name,
                    BwPrincipal<?> owner);
}
