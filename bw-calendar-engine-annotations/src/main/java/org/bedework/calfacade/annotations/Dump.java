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
package org.bedework.calfacade.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** This is used to annotate a method for which we should generate dump output.
 *
 * @author Mike Douglass
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Dump {
  /** Specify the type for the dump
   */
  public enum DumpFormat {
    /** Proprietary bedework xml */
    xml,

    /** Dump as xCal */
    xCal,

    /** Dump as vcard (4) */
    vCard,

    /** Dump as vcard (4) */
    xCard,

    /** Dump as appropriate resource type */
    media,
  }

  /** Specify the type for the dump
   */
  DumpFormat format() default DumpFormat.xml;

  /** Name of element to use in xml dump
   */
  String elementName() default "";

  /** For a collection, if this is not defaulted, each element of the
   * collection will be wrapped in this extra tag
   */
  String collectionElementName() default "";

  /** If true this element is treated as a compound type - we don't give it an
   * outer element and we dump all the values
   */
  boolean compound() default false;

  /** If being dumped as a reference these are the fields to dump */
  String[] keyFields() default {};

  /** If non empty these are the first fields to dump */
  String[] firstFields() default {};
}
