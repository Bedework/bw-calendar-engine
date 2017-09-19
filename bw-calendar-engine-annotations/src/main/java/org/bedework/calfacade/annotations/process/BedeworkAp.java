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

import org.bedework.calfacade.annotations.NoWrap;
import org.bedework.calfacade.annotations.Wrapper;
import org.bedework.calfacade.annotations.ical.IcalProperties;
import org.bedework.calfacade.annotations.ical.IcalProperty;
import org.bedework.calfacade.annotations.ical.NoProxy;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor6;

/**
 * @author douglm
 *
 */
@SupportedAnnotationTypes(value= {"*"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class BedeworkAp extends AbstractProcessor {
  private ProcessState pstate;

  private AnnUtil annUtil;

  /* Don't process inner classes - depth 0 is no class, depth 1 is outer class */
  private int classDepth;

  @Override
  public void init(final ProcessingEnvironment env) {
    super.init(env);

    annUtil = new AnnUtil(processingEnv);

    pstate = new ProcessState(processingEnv);

    final Map<String, String> options = env.getOptions();
    for (final String option : options.keySet()) {
      annUtil.note("Option: " + option + "=" + options.get(option));
      if (option.equals("resourcePath")) {
        pstate.resourcePath = options.get(option);
        continue;
      }

      if (option.equals("debug")) {
        pstate.debug = "true".equals(options.get(option));
      }
    }

    //pstate.debug = true;
  }

  @Override
  public boolean process(final Set<? extends TypeElement> annotations,
                         final RoundEnvironment roundEnv) {
    if (pstate.debug) {
      annUtil.note(
              "--------------- process called: " + roundEnv
                      .toString());

      for (final TypeElement tel : annotations) {
        annUtil.note("Annotation " + tel.asType().toString());
      }
    }

    for (final Element el : roundEnv.getRootElements()) {
      final String className = el.asType().toString();

      if (pstate.debug) {
        annUtil.note("Processing " + className);
      }

      el.accept(new ElVisitor(), pstate);
    }

    if (roundEnv.processingOver()) {
      pstate.getIcalPropertyHandler().closePinfo(processingEnv);
    }

    return false;
  }

  private class ElVisitor
          extends SimpleElementVisitor6<Element, ProcessState> {
    @Override
    public Element visitType(final TypeElement el,
                             final ProcessState p) {
      final String className = el.asType().toString();

      if (pstate.debug) {
        annUtil.note("Start Class: " + className +
                             " depth: " + classDepth);
      }

      pstate.setCurrentClassName(className);

      classDepth++;

      if (classDepth > 1) {
        // In inner class

        endClass(el, pstate);

        return el;
      }

      if (!pstate.processingEvent) {
        pstate.processingEvent =
                "org.bedework.calfacade.BwEvent".equals(className);

        if (pstate.processingEvent) {
          pstate.getProxyHandler().startProxy(processingEnv);
        }
      }

      final Wrapper wpr = el.getAnnotation(Wrapper.class);

      if (wpr != null) {
        pstate.processingWrapper = true;
        pstate.getWrapperHandler(wpr).start(processingEnv);
      }

      //el.accept(new ElVisitor(), pstate);
      for (final Element subEl : el.getEnclosedElements()) {
        subEl.accept(new ElVisitor(), pstate);
      }

      endClass(el, pstate);

      return el;
    }

    @Override
    public Element visitExecutable(final ExecutableElement e,
                                   final ProcessState pstate) {
      if (pstate.debug) {
        annUtil.note("Executable: " + e);
      }

      if (classDepth > 1) {
        // In inner class
        return e;
      }

      if (pstate.processingEvent) {
        if (e.getAnnotation(NoProxy.class) == null) {
          final Collection<Modifier> mods = e.getModifiers();

          if (mods.contains(Modifier.PUBLIC)) {
            pstate.getProxyHandler().proxyMethod(pstate.getEnv(), e);
          }
        }
      }

      if (pstate.processingWrapper) {
        if (e.getAnnotation(NoWrap.class) == null) {
          final Collection<Modifier> mods = e.getModifiers();

          if (mods.contains(Modifier.PUBLIC)) {
            pstate.getWrapperHandler().method(pstate.getEnv(), e,
                                              false);
          }
        }
      }

      final IcalProperty ip = e.getAnnotation(IcalProperty.class);
      if (ip != null) {
        pstate.getIcalPropertyHandler().property(annUtil,
                                                 pstate.getEnv(), ip,
                                                 e);
        if (pstate.debug) {
          annUtil.note("IcalProperty: " + ip.pindex().name());
        }
      }

      final IcalProperties ips = e.getAnnotation(IcalProperties.class);
      if (ips != null) {
        final IcalPropertyHandler iph = pstate.getIcalPropertyHandler();

        for (final IcalProperty ip1 : ips.value()) {
          iph.property(annUtil,
                       pstate.getEnv(), ip1, e);
          if (pstate.debug) {
            annUtil.note("IcalProperty: " + ip1.pindex().name());
          }
        }
      }

      return e;
    }
  }

  private void endClass(final TypeElement el,
                        final ProcessState pstate) {
    final String className = el.asType().toString();

    /* Now we do the end processing */

    classDepth--;

    if (pstate.debug) {
      annUtil.note("End Class: " + className +
                           " depth: " + classDepth);
    }

    if (classDepth >= 1) {
      // Finished inner class
      return;
    }

    if (pstate.processingEvent) {
      if ("org.bedework.calfacade.BwEvent".equals(className)) {
        pstate.getProxyHandler().endProxy(processingEnv);

        pstate.processingEvent = false;
      }
    }

    if (pstate.processingWrapper) {
      final TypeMirror superD = el.getSuperclass();
      if (superD.toString().startsWith("org.bedework")) {
        processSuper(processingEnv.getTypeUtils().asElement(
                superD));
      }

      pstate.getWrapperHandler().end(processingEnv);

      pstate.endWrapperHandler();
    }

  }

  private void processSuper(final Element el) {
    final TypeElement typeEl = annUtil.asTypeElement(el.asType());

    if (pstate.debug) {
      annUtil.note("process super: " + el.toString());
    }

    for (final Element subEl : el.getEnclosedElements()) {
      if (subEl.getKind() != ElementKind.METHOD) {
        continue;
      }

      if (subEl.getAnnotation(NoWrap.class) == null) {
        final Collection<Modifier> mods = subEl.getModifiers();

        if (mods.contains(Modifier.PUBLIC)) {
          pstate.getWrapperHandler().method(processingEnv,
                                            (ExecutableElement)subEl,
                                            true);
        }
      }
    }

    final TypeMirror superD = typeEl.getSuperclass();
    if (superD.toString().startsWith("org.bedework")) {

      processSuper(processingEnv.getTypeUtils().asElement(superD));
    }
  }
}