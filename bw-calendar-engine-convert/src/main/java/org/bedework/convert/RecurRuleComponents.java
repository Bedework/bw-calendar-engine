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
package org.bedework.convert;

import org.bedework.calfacade.BwEvent;
import org.bedework.util.misc.response.GetEntitiesResponse;
import org.bedework.util.misc.response.Response;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.NumberList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.WeekDay;
import net.fortuna.ical4j.model.WeekDayList;

import java.text.ParseException;
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

* @author Mike Douglass     douglm - rpi.edu
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
   * @param val Freq
   */
  public void setFreq(Freq val) {
    freq = val;
  }

  /**
   * @return Freq
   */
  @SuppressWarnings("unused")
  public Freq getFreq() {
    return freq;
  }

  /**
   * @param val until Date
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
   * @param val count
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
   * @param val interval
   */
  public void setInterval(int val) {
    interval = val;
  }

  /**
   * @return int
   */
  @SuppressWarnings("unused")
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
  @SuppressWarnings("unused")
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
  @SuppressWarnings("unused")
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
  @SuppressWarnings("unused")
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
  @SuppressWarnings("unused")
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
  @SuppressWarnings("unused")
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
  @SuppressWarnings("unused")
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
  @SuppressWarnings("unused")
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
  @SuppressWarnings("unused")
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
  @SuppressWarnings("unused")
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
  @SuppressWarnings("unused")
  public String getWkst() {
    return wkst;
  }

  /** Return parsed rrules.
   *
   * @param ev containing rules
   * @return Response containing status and collection of parsed rrules
   */
  public static GetEntitiesResponse<RecurRuleComponents> fromEventRrules(BwEvent ev) {
    final GetEntitiesResponse<RecurRuleComponents> resp =
            new GetEntitiesResponse<>();
    if (!ev.isRecurringEntity()) {
      return Response.notFound(resp);
    }

    Collection<String> rules = ev.getRrules();

    if (rules == null) {
      return Response.notFound(resp);
    }

    for (String rule: rules) {
      RecurRuleComponents rrc = new RecurRuleComponents();

      final Recur recur;
      try {
        recur = new Recur(rule);
      } catch (ParseException e) {
        return Response.error(resp, "Invalid RRULE: " + rule);
      }

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
      WeekDayList wds = recur.getDayList();
      if (wds != null) {
        HashMap<Integer, Collection<String>> hm = new HashMap<>();

        for (WeekDay wd: wds) {
          Collection<String> c = hm.computeIfAbsent(wd.getOffset(),
                                                    k -> new ArrayList<>());

          c.add(wd.getDay().name());
        }

        final Collection<PosDays> pds = new ArrayList<>();

        final Set<Integer> poss = hm.keySet();
        for (final Integer pos: poss) {
          pds.add(new PosDays(pos, hm.get(pos)));
        }

        rrc.setByDay(pds);
      }

      rrc.setByMonthDay(checkNumList(recur.getMonthDayList()));
      rrc.setByYearDay(checkNumList(recur.getYearDayList()));
      rrc.setByWeekNo(checkNumList(recur.getWeekNoList()));
      rrc.setByMonth(checkNumList(recur.getMonthList()));
      rrc.setBySetPos(checkNumList(recur.getSetPosList()));

      if (recur.getWeekStartDay() != null) {
        rrc.setWkst(recur.getWeekStartDay().name());
      }

      resp.addEntity(rrc);
    }

    return Response.ok(resp);
  }

  private static Collection<Integer> checkNumList(NumberList val) {
    if ((val == null) || (val.isEmpty())) {
      return null;
    }

    return val;
  }
}
