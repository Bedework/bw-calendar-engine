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
package org.bedework.calfacade.annotations.ical;

import edu.rpi.cmt.calendar.PropertyIndex.PropertyInfoIndex;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** This is used to annotate a setter method property in a class which
 * represents an icalendar property. For the time being it must be applied to
 * a setter method and if it appears on multiple classes the methods must have
 * the same name.
 *
 * @author Mike Douglass
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IcalProperty {
  /** Property name */
  PropertyInfoIndex pindex();

  /** used in db queries - better extracted from the schema? */
  String dbFieldName() default "";

  /** field we test for presence */
  String presenceField() default "";

  /** It's a parameter   */
  boolean param() default false;

  /** Reschedule meetings if this changes */
  boolean reschedule() default false;

  /** */
  boolean eventProperty() default false;
  /** */
  boolean todoProperty() default false;
  /** */
  boolean journalProperty() default false;
  /** */
  boolean freeBusyProperty() default false;
  /** */
  boolean timezoneProperty() default false;
  /** */
  boolean alarmProperty() default false;
  /** */
  boolean vavailabilityProperty() default false;
  /** */
  boolean availableProperty() default false;
  /** */
  boolean vpollProperty() default false;
}
