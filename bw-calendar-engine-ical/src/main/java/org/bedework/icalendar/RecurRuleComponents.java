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
package org.bedework.icalendar;

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.exc.CalFacadeException;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.NumberList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.WeekDay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

/** Broken out recurrence rule.
*
   *          recur      = "FREQ"=freq *(

                    ; either UNTIL or COUNT may appear in a 'recur',
                    ; but UNTIL and COUNT MUST NOT occur in the same 'recur'

                    ( ";" "UNTIL" "=" enddate ) /
                    ( ";" "COUNT" "=" 1*DIGIT ) /

                    ; the rest of these keywords are optional,
                    ; but MUST NOT occur more than once

                    ( ";" "INTERVAL" "=" 1*DIGIT )          /
                    ( ";" "BYSECOND" "=" byseclist )        /
                    ( ";" "BYMINUTE" "=" byminlist )        /
                    ( ";" "BYHOUR" "=" byhrlist )           /
                    ( ";" "BYDAY" "=" bywdaylist )          /
                    ( ";" "BYMONTHDAY" "=" bymodaylist )    /
                    ( ";" "BYYEARDAY" "=" byyrdaylist )     /
                    ( ";" "BYWEEKNO" "=" bywknolist )       /
                    ( ";" "BYMONTH" "=" bymolist )          /
                    ( ";" "BYSETPOS" "=" bysplist )         /
                    ( ";" "WKST" "=" weekday )              /
                    ( ";" x-name "=" text )
                    )

         freq       = "SECONDLY" / "MINUTELY" / "HOURLY" / "DAILY"
                    / "WEEKLY" / "MONTHLY" / "YEARLY"

         enddate    = date
         enddate    =/ date-time            ;An UTC value

         byseclist  = seconds / ( seconds *("," seconds) )

         seconds    = 1DIGIT / 2DIGIT       ;0 to 59

         byminlist  = minutes / ( minutes *("," minutes) )

         minutes    = 1DIGIT / 2DIGIT       ;0 to 59

         byhrlist   = hour / ( hour *("," hour) )

         hour       = 1DIGIT / 2DIGIT       ;0 to 23

         bywdaylist = weekdaynum / ( weekdaynum *("," weekdaynum) )

         weekdaynum = [([plus] ordwk / minus ordwk)] weekday

         plus       = "+"

         minus      = "-"

         ordwk      = 1DIGIT / 2DIGIT       ;1 to 53

         weekday    = "SU" / "MO" / "TU" / "WE" / "TH" / "FR" / "SA"
         ;Corresponding to SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY,
         ;FRIDAY, SATURDAY and SUNDAY days of the week.

         bymodaylist = monthdaynum / ( monthdaynum *("," monthdaynum) )

         monthdaynum = ([plus] ordmoday) / (minus ordmoday)

         ordmoday   = 1DIGIT / 2DIGIT       ;1 to 31

         byyrdaylist = yeardaynum / ( yeardaynum *("," yeardaynum) )

         yeardaynum = ([plus] ordyrday) / (minus ordyrday)

         ordyrday   = 1DIGIT / 2DIGIT / 3DIGIT      ;1 to 366

         bywknolist = weeknum / ( weeknum *("," weeknum) )
         weeknum    = ([plus] ordwk) / (minus ordwk)

         bymolist   = monthnum / ( monthnum *("," monthnum) )

         monthnum   = 1DIGIT / 2DIGIT       ;1 to 12

         bysplist   = setposday / ( setposday *("," setposday) )

         setposday  = yeardaynum

* @author Mike Douglass     douglm - bedework.edu
*  @version 1.0
*/
public class RecurRuleComponents {
  private String rule;

  /**
   * @author douglm
   *
   */
  public enum Freq {
    /**      */
    SECONDLY,
    /**      */
    MINUTELY,
    /**      */
    HOURLY,
    /**      */
    DAILY,
    /**      */
    WEEKLY,
    /**      */
    MONTHLY,
    /**      */
    YEARLY }

  private Freq freq;

  private Date until;

  private int count;

  private int interval;

  /**
   * Allows us to group days
   */
  public static class PosDays {
    Integer pos;
    Collection<String> days;

    PosDays(Integer pos, Collection<String> days) {
      this.pos = pos;
      this.days = days;
    }

    /**
     * @return Integer
     */
    public Integer getPos() {
      return pos;
    }

    /**
     * @return  Collection<String> days;

     */
    public Collection<String> getDays() {
      return days;
    }
  }

  private Collection<Integer> bySecond;
  private Collection<Integer> byMinute;
  private Collection<Integer> byHour;
  private Collection<PosDays> byDay;
  private Collection<Integer> byMonthDay;
  private Collection<Integer> byYearDay;
  private Collection<Integer> byWeekNo;
  private Collection<Integer> byMonth;
  private Collection<Integer> bySetPos;
  private String wkst;

  /**
   * @param val String rule this is derived from
   */
  public void setRule(String val) {
    rule = val;
  }

  /**
   * @return String weekstart or null
   */
  public String getRule() {
    return rule;
  }

  /**
   * @param val
   */
  public void setFreq(Freq val) {
    freq = val;
  }

  /**
   * @return Freq
   */
  public Freq getFreq() {
    return freq;
  }

  /**
   * @param val
   */
  public void setUntil(Date val) {
    until = val;
  }

  /**
   * @return Date
   */
  public Date getUntil() {
    return until;
  }

  /**
   * @param val
   */
  public void setCount(int val) {
    count = val;
  }

  /**
   * @return int
   */
  public int getCount() {
    return count;
  }

  /**
   * @param val
   */
  public void setInterval(int val) {
    interval = val;
  }

  /**
   * @return int
   */
  public int getInterval() {
    return interval;
  }

  /**
   * @param val bySecond list
   */
  public void setBySecond(Collection<Integer> val) {
    bySecond = val;
  }

  /**
   * @return bySecond list or null
   */
  public Collection<Integer> getBySecond() {
    return bySecond;
  }

  /**
   * @param val byMinute list
   */
  public void setByMinute(Collection<Integer> val) {
    byMinute = val;
  }

  /**
   * @return byMinute list or null
   */
  public Collection<Integer> getByMinute() {
    return byMinute;
  }

  /**
   * @param val byHour list
   */
  public void setByHour(Collection<Integer> val) {
    byHour = val;
  }

  /**
   * @return byHour list or null
   */
  public Collection<Integer> getByHour() {
    return byHour;
  }

  /**
   * @param val byDay map
   */
  public void setByDay(Collection<PosDays> val) {
    byDay = val;
  }

  /**
   * @return byDay map or null
   */
  public Collection<PosDays> getByDay() {
    return byDay;
  }

  /**
   * @param val byMonthDay list
   */
  public void setByMonthDay(Collection<Integer> val) {
    byMonthDay = val;
  }

  /**
   * @return byMonthDay list or null
   */
  public Collection<Integer> getByMonthDay() {
    return byMonthDay;
  }

  /**
   * @param val byYearDay list
   */
  public void setByYearDay(Collection<Integer> val) {
    byYearDay = val;
  }

  /**
   * @return byYearDay list or null
   */
  public Collection<Integer> getByYearDay() {
    return byYearDay;
  }

  /**
   * @param val byWeekNo list
   */
  public void setByWeekNo(Collection<Integer> val) {
    byWeekNo = val;
  }

  /**
   * @return byWeekNo list or null
   */
  public Collection<Integer> getByWeekNo() {
    return byWeekNo;
  }

  /**
   * @param val byMonth list
   */
  public void setByMonth(Collection<Integer> val) {
    byMonth = val;
  }

  /**
   * @return byMonth list or null
   */
  public Collection<Integer> getByMonth() {
    return byMonth;
  }

  /**
   * @param val bySetPos list
   */
  public void setBySetPos(Collection<Integer> val) {
    bySetPos = val;
  }

  /**
   * @return bySetPos list or null
   */
  public Collection<Integer> getBySetPos() {
    return bySetPos;
  }

  /**
   * @param val String weekstart
   */
  public void setWkst(String val) {
    wkst = val;
  }

  /**
   * @return String weekstart or null
   */
  public String getWkst() {
    return wkst;
  }

  /** Return parsed rrules.
   *
   * @param ev
   * @return Collection of parsed rrules
   * @throws CalFacadeException
   */
  public static Collection<RecurRuleComponents> fromEventRrules(BwEvent ev)
            throws CalFacadeException {
    return fromEventXrules(ev, ev.getRrules());
  }

  private static Collection<RecurRuleComponents> fromEventXrules(BwEvent ev,
                                                                 Collection<String> rules)
            throws CalFacadeException {
    Collection<RecurRuleComponents> rrcs = new ArrayList<RecurRuleComponents>();
    if (!ev.isRecurringEntity()) {
      return rrcs;
    }

    if (rules == null) {
      return rrcs;
    }

    try {
      for (String rule: rules) {
        RecurRuleComponents rrc = new RecurRuleComponents();

        Recur recur = new Recur(rule);

        rrc.setRule(rule);
        rrc.setFreq(Freq.valueOf(recur.getFrequency()));

        Date until = recur.getUntil();
        if (until != null) {
          rrc.setUntil(until);
        } else {
          rrc.setCount(recur.getCount());
        }

        rrc.setInterval(recur.getInterval());

        rrc.setBySecond(checkNumList(recur.getSecondList()));
        rrc.setByMinute(checkNumList(recur.getMinuteList()));
        rrc.setByHour(checkNumList(recur.getHourList()));

        /* Group by position */
        Collection wds = recur.getDayList();
        if (wds != null) {
          HashMap<Integer, Collection<String>> hm =
            new HashMap<Integer, Collection<String>>();

          for (Object o: wds) {
            WeekDay wd = (WeekDay)o;

            Collection<String>c = hm.get(wd.getOffset());
            if (c == null) {
              c = new ArrayList<String>();
              hm.put(wd.getOffset(), c);
            }

            c.add(wd.getDay());
          }

          Collection<PosDays> pds = new ArrayList<PosDays>();

          Set<Integer> poss = hm.keySet();
          for (Integer pos: poss) {
            pds.add(new PosDays(pos, hm.get(pos)));
          }

          rrc.setByDay(pds);
        }

        rrc.setByMonthDay(checkNumList(recur.getMonthDayList()));
        rrc.setByYearDay(checkNumList(recur.getYearDayList()));
        rrc.setByWeekNo(checkNumList(recur.getWeekNoList()));
        rrc.setByMonth(checkNumList(recur.getMonthList()));
        rrc.setBySetPos(checkNumList(recur.getSetPosList()));

        rrc.setWkst(recur.getWeekStartDay());

        rrcs.add(rrc);
      }
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }

    return rrcs;
  }

  @SuppressWarnings("unchecked")
  private static Collection<Integer> checkNumList(NumberList val) {
    if ((val == null) || (val.isEmpty())) {
      return null;
    }

    return val;
  }
}
