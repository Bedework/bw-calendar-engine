package org.bedework.calcorei;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwEvent;

import java.io.Serializable;
import java.util.Collection;

public interface CoreAlarmsI extends Serializable  {
  /** Return all unexpired alarms before a given time. If time is 0 all
   * unexpired alarms will be retrieved.
   *
   * <p>Any cancelled alarms will be excluded from the result.
   *
   * <p>Typically the system will call this with a time set into the near future
   * and then queue up alarms that are near to triggering.
   *
   * @param triggerTime limit
   * @return Collection of unexpired alarms.
   */
  Collection<BwAlarm> getUnexpiredAlarms(long triggerTime);

  /** Given an alarm return the associated event(s)
   *
   * @param alarm to search for
   * @return an event.
   */
  Collection<BwEvent> getEventsByAlarm(BwAlarm alarm);
}
