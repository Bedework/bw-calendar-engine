package org.bedework.calcore.rw.common.dao;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;

import java.util.Collection;

public interface EntityDAO extends DAOBase {
  BwCalSuite get(BwAdminGroup group);

  BwCalSuite getCalSuite(String name);

  Collection<BwCalSuite> getAllCalSuites();

  Collection<BwAlarm> getUnexpiredAlarms(long triggerTime);

  Collection<BwEvent> getEventsByAlarm(BwAlarm alarm);
}
