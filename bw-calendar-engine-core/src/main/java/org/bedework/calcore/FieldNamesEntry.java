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
package org.bedework.calcore;

import java.io.Serializable;

/**
 * @author douglm
 *
 */
public class FieldNamesEntry implements Serializable {
  private String pname;
  private String fname;
  private String addMethodName; // For multi
  private boolean multi;

  FieldNamesEntry(final String pname,
                  final String fname,
                  final boolean multi) {
    this(pname, fname, fname, multi);
  }

  FieldNamesEntry(final String pname,
                  final String fname,
                  final String addMethodName,
                  final boolean multi) {
    this.pname = pname.toUpperCase();
    this.fname = fname;
    this.addMethodName = addMethodName;
    this.multi = multi;
  }

  /**
   *
   * @return Property name - uppercased
   */
  public String getPname() {
    return pname;
  }

  /**
   * @return Event class field name
   */
  public String getFname() {
    return fname;
  }

  /**
   * @return Event class adder method name
   */
  public String getAddMethodName() {
    return addMethodName;
  }

  /**
   * @return true for multi-valued
   */
  public boolean getMulti() {
    return multi;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof FieldNamesEntry)) {
      return false;
    }

    FieldNamesEntry that = (FieldNamesEntry)o;

    return fname.equals(that.fname);
  }
}
