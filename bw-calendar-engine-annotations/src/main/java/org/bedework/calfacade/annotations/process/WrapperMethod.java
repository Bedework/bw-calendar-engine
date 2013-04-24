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
package org.bedework.calfacade.annotations.process;

import org.bedework.calfacade.annotations.NoQuota;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.type.PrimitiveType;
import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.util.Types;

/** We create a list of these as we process the event. We tie together the
 * setter and getter methods so that only the setter needs to be annotated
 *
 * @author Mike DOuglass
 */
public class WrapperMethod extends MethodHandler<WrapperMethod> {
  private ProcessState pstate;

  private boolean fromSuper;

  private boolean unquotad;

  private static TypeMirror typeBoolean;
  private static TypeMirror typeByte;
  private static TypeMirror typeChar;
  private static TypeMirror typeDouble;
  private static TypeMirror typeFloat;
  private static TypeMirror typeInt;
  private static TypeMirror typeLong;
  private static TypeMirror typeShort;

  /**
   * @param env
   * @param annUtil
   * @param pstate
   * @param d
   * @param fromSuper if declared in a superdeclaration
   */
  public WrapperMethod(final AnnotationProcessorEnvironment env,
                       final AnnUtil annUtil,
                       final ProcessState pstate,
                       final MethodDeclaration d,
                       final boolean fromSuper) {
    super(env, annUtil, d);
    this.pstate = pstate;
    this.fromSuper = fromSuper;

    unquotad = d.getAnnotation(NoQuota.class) != null;

    if (typeBoolean == null) {
      // init primitive types.
      Types types = env.getTypeUtils();
      typeBoolean = types.getPrimitiveType(PrimitiveType.Kind.BOOLEAN);
      typeByte = types.getPrimitiveType(PrimitiveType.Kind.BYTE);
      typeChar = types.getPrimitiveType(PrimitiveType.Kind.CHAR);
      typeDouble = types.getPrimitiveType(PrimitiveType.Kind.DOUBLE);
      typeFloat = types.getPrimitiveType(PrimitiveType.Kind.FLOAT);
      typeInt = types.getPrimitiveType(PrimitiveType.Kind.INT);
      typeLong = types.getPrimitiveType(PrimitiveType.Kind.LONG);
      typeShort = types.getPrimitiveType(PrimitiveType.Kind.SHORT);
    }
  }

  /**
   * @return true if declared by a superdeclaration
   */
  public boolean getFromSuper() {
    return fromSuper;
  }

  /**
   */
  @Override
  public void generateGet() {
    annUtil.println("    return ", makeCallGetter("entity"), ";");
    annUtil.prntlns("  }",
                    "");
  }

  /**
   */
  @Override
  public void generateSet() {
    if (pstate.generateQuotaSupport) {
      if (basicType) {
        // Update overhead

        if (pstate.debug) {
          msg.printNotice(" calc quota overhead for " + fieldType);
        }

        if (fieldType.equals(typeBoolean)) {
          pstate.sizeOverhead += 1;
        } else if (fieldType.equals(typeByte)) {
          pstate.sizeOverhead += 1;
        } else if (fieldType.equals(typeChar)) {
          pstate.sizeOverhead += 1;
        } else if (fieldType.equals(typeDouble)) {
          pstate.sizeOverhead += 8;
        } else if (fieldType.equals(typeFloat)) {
          pstate.sizeOverhead += 4;
        } else if (fieldType.equals(typeInt)) {
          pstate.sizeOverhead += 4;
        } else if (fieldType.equals(typeLong)) {
          pstate.sizeOverhead += 8;
        } else if (fieldType.equals(typeShort)) {
          pstate.sizeOverhead += 2;
        }
      } else if (!unquotad) {
        // Generate size change code.
      }
    }

    annUtil.println("    ", makeCallSetter("entity", "val"), ";");
    annUtil.prntlns("  }",
                    "");
  }

  /**
   */
  @Override
  public void generateMethod() {
    if (returnsVoid) {
      annUtil.println("    entity.", makeCall(), ";");
    } else {
      annUtil.println("    return entity.", makeCall(), ";");
    }
    annUtil.prntlns("  }",
                    "");
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("WrapperMethod{");
    toStringSegment(sb);

    return sb.toString();
  }
}
