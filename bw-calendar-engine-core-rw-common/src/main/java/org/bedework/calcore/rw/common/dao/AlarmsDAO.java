package org.bedework.calcore.rw.common.dao;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwEvent;

import java.util.Collection;

public interface AlarmsDAO extends DAOBase {
  Collection<BwAlarm> getUnexpiredAlarms(long triggerTime);

  Collection<BwEvent> getEventsByAlarm(BwAlarm alarm);
}
