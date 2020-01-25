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
package org.bedework.calfacade.util;

import org.bedework.calfacade.base.SizedEntity;

import java.util.Collection;

/**
 * @author douglm
 *
 */
public class QuotaUtil {
  /** Return the size change for the given object
   *
   * @param fromVal as it was
   * @param toVal as it is
   * @return int chage
   */
  public static int getSizeChange(SizedEntity fromVal, SizedEntity toVal) {
    return size(toVal) - size(fromVal);
  }

  /** Return the size change for the given object
   *
   * @param fromVal as it was
   * @param toVal as it is
   * @return int chage
   */
  public static int getSizeChange(String fromVal, String toVal) {
    return size(toVal) - size(fromVal);
  }

  /**
   * @param val sized entity
   * @return int size
   */
  public static int size(SizedEntity val) {
    if (val == null) {
      return 0;
    }

    return val.getSize();
  }

  /** This actually returns character size - not byte size. Getting byte size
   * might slow things up.
   *
   * @param val string
   * @return int
   */
  public static int size(String val) {
    if (val == null) {
      return 0;
    }

    return val.length();
  }


  /**
   * @param c a collection
   * @return int size
   */
  public static int collectionSize(Collection<?> c) {
    if (c == null) {
      return 0;
    }

    int len = 0;
    for (Object o: c) {
      if (o instanceof SizedEntity) {
        len += size((SizedEntity)o);
      } else if (o instanceof String) {
        len += size((String)o);
      }
    }

    return len;
  }
}
