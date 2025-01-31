package org.bedework.calcore.rw.common.dao;

import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;

import java.util.Collection;

public interface CalSuitesDAO extends DAOBase {
  BwCalSuite get(BwAdminGroup group);

  BwCalSuite getCalSuite(String name);

  Collection<BwCalSuite> getAllCalSuites();
}
