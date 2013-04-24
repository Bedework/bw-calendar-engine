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
/*
 Copyright (c) 2000-2005 University of Washington.  All rights reserved.

 Redistribution and use of this distribution in source and binary forms,
 with or without modification, are permitted provided that:

   The above copyright notice and this permission notice appear in
   all copies and supporting documentation;

   The name, identifiers, and trademarks of the University of Washington
   are not used in advertising or publicity without the express prior
   written permission of the University of Washington;

   Recipients acknowledge that this distribution is made available as a
   research courtesy, "as is", potentially with defects, without
   any obligation on the part of the University of Washington to
   provide support, services, or repair;

   THE UNIVERSITY OF WASHINGTON DISCLAIMS ALL WARRANTIES, EXPRESS OR
   IMPLIED, WITH REGARD TO THIS SOFTWARE, INCLUDING WITHOUT LIMITATION
   ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
   PARTICULAR PURPOSE, AND IN NO EVENT SHALL THE UNIVERSITY OF
   WASHINGTON BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
   DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
   PROFITS, WHETHER IN AN ACTION OF CONTRACT, TORT (INCLUDING
   NEGLIGENCE) OR STRICT LIABILITY, ARISING OUT OF OR IN CONNECTION WITH
   THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package org.bedework.dumprestore.dump;

import org.bedework.calfacade.configs.SystemRoots;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.DumpIntf;
import org.bedework.dumprestore.Counters;
import org.bedework.dumprestore.ExternalSubInfo;
import org.bedework.dumprestore.InfoLines;

import edu.rpi.sss.util.xml.XmlEmit;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author douglm
 *
 */
public class DumpGlobals extends Counters {
  /** Used to build hrefs */
  public SystemRoots sysRoots;

  /** Use this to output xml */
  public XmlEmit xml;

  /** Access to our data */
  public CalSvcI svci;

  /** Handler for access to our data */
  public DumpIntf di;

  /** */
  private Writer out;

  /** To track messages.
   *
   */
  public InfoLines info;

  /** Collections marked as external subscriptions. We may need to resubscribe
   */
  public List<ExternalSubInfo> externalSubs = new ArrayList<ExternalSubInfo>();

  /* Some counters */

  /** */
  public DumpGlobals() {
  }

  /**
   * @param val
   * @throws Throwable
   */
  public void setOut(final Writer val) throws Throwable {
    out = val;

    xml = new XmlEmit();
    xml.startEmit(out);
  }

  /**
   * @throws CalFacadeException
   */
  public void close() throws CalFacadeException {
    try {
      xml.flush();
      out.close();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /**
   * @param config
   * @param sysRoots
   */
  public void init(final SystemRoots sysRoots) {
    this.sysRoots = sysRoots;
  }
}
