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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.AnnotationProcessorFactory;
import com.sun.mirror.apt.Messager;
import com.sun.mirror.apt.RoundCompleteEvent;
import com.sun.mirror.apt.RoundCompleteListener;
import com.sun.mirror.declaration.AnnotationTypeDeclaration;
import com.sun.mirror.declaration.ClassDeclaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.Modifier;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.util.DeclarationVisitors;
import com.sun.mirror.util.SimpleDeclarationVisitor;

/**
 * @author douglm
 *
 */
public class FacadeAPF implements AnnotationProcessorFactory {

  /**
   * List of annotations are given using the method called
   * "unmodifiableCollection", which gives back a collection that
   * cannot be modified (hence the name). Annotations are defined
   * almost exactly like imports. org.bedework.calfacade.annotations.* means
   * all annotations defined in the package
   * org.bedework.calfacade.annotations. One difference between Java's import
   * mechanism and this is that we can use the String "*" to mean all
   * annotations in the class path (or no annotations at all--meaning
   * we can even look at classes that have no annotations!).
   * If you want to define more than one package, include them in
   * the array like this:
   * Arrays.asList("org.bedework.calfacade.annotations1.*",
   *    "org.bedework.calfacade.annotations2.*")
   */
  private static final Collection<String> supportedAnnotations =
    Collections.unmodifiableCollection(
        //Arrays.asList("org.bedework.calfacade.annotations.*"));
        Arrays.asList("*"));

  /**
   * No options are supported.
   */
  private static final Collection<String> supportedOptions =
        Collections.emptySet();

  ProcessState pstate = new ProcessState();

  /* (non-Javadoc)
   * @see com.sun.mirror.apt.AnnotationProcessorFactory#supportedAnnotationTypes()
   */
  public Collection<String> supportedAnnotationTypes() {
    System.out.println("supportedAnnotationTypes called");
    return supportedAnnotations;
  }

  /* (non-Javadoc)
   * @see com.sun.mirror.apt.AnnotationProcessorFactory#supportedOptions()
   */
  public Collection<String> supportedOptions() {
    System.out.println("supportedOptions called");
    return supportedOptions;
  }

  /* (non-Javadoc)
   * @see com.sun.mirror.apt.AnnotationProcessorFactory#getProcessorFor(java.util.Set, com.sun.mirror.apt.AnnotationProcessorEnvironment)
   */
  public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> atds,
                                             AnnotationProcessorEnvironment env) {
    Map<String, String> options = env.getOptions();
    for (String option : options.keySet()) {
      //env.getMessager().printNotice(option);
      //env.getMessager().printNotice(options.get(option));
      if (option.startsWith("-AresourcePath=")) {
        pstate.resourcePath = option.substring("-AresourcePath=".length());
      }

      if (option.startsWith("-Adebug=")) {
        pstate.debug = "true".equals(option.substring("-Adebug=".length()));
      }
    }

    env.addListener(new RcListener(env, pstate));

    return new BedworkAp(env, pstate);
    /*
    for (AnnotationTypeDeclaration atd : atds) {
      if (atd.getQualifiedName().
          equals("com.ddj.annotations.ApplicationExceptions")) {
        return new ApplicationExceptionsAp(env);
      }
    }

    return AnnotationProcessors.NO_OP;*/
  }

  private static class RcListener implements RoundCompleteListener {
    private final AnnotationProcessorEnvironment env;

    private ProcessState pstate;

    RcListener(AnnotationProcessorEnvironment env, ProcessState pstate) {
      this.env = env;
      this.pstate = pstate;
    }

    public void roundComplete(RoundCompleteEvent event) {
      // Assume we're done
      pstate.getIcalPropertyHandler().closePinfo(env);
    }
  }

  /**
   * *** This class contains the annotation processor.  It
   * produces a class based upon the annotation given to it.
   * It's a simple processor. It takes the values given to
   * the annotation and processes them appropriately to give an
   * exception class as defined.
   * @author J. Benton
   */
  private static class BedworkAp implements AnnotationProcessor {
    private final AnnotationProcessorEnvironment env;

    private ProcessState pstate;

    /* Don't process inner classes - depth 0 is no class, depth 1 is outer class */
    private int classDepth;

    BedworkAp(AnnotationProcessorEnvironment env, ProcessState pstate) {
      this.env = env;
      this.pstate = pstate;
    }

    /**
     * This method is the main processor and what you have to
     * implement to get anything done around here.  Here's a summary
     * of what it does: It looks for any class containing the
     * "ApplicationExceptions" annotation.  After that, it looks
     * at all of the containing annotations--the ApplicationException
     * annotations that are defined in an array of annotations.  These
     * are then processed based upon their parameters to generate
     * Java source files.
     * @see com.sun.mirror.apt.AnnotationProcessor#process()
     */
    public void process() {
      Messager msg = env.getMessager();
      for (TypeDeclaration typeDecl: env.getSpecifiedTypeDeclarations()) {
        typeDecl.accept(DeclarationVisitors.getDeclarationScanner(new DeclVisitor(msg, pstate),
                                                                  new DeclEndVisitor(msg, pstate)));
      }
    }

    private class DeclVisitor extends SimpleDeclarationVisitor {
      private Messager msg;
      private ProcessState pstate;

      /**
       * @param msg
       * @param pstate
       */
      public DeclVisitor(Messager msg, ProcessState pstate) {
        this.msg = msg;
        this.pstate = pstate;
      }

      public void visitClassDeclaration(ClassDeclaration d) {
        if (pstate.debug) {
          msg.printNotice("Start Class: " + d.getQualifiedName());
        }
        pstate.setCurrentClassName(d.getQualifiedName());

        classDepth++;

        if (classDepth > 1) {
          // In inner class
          return;
        }

        if (!pstate.processingEvent) {
          pstate.processingEvent = "org.bedework.calfacade.BwEvent".equals(d.getQualifiedName());

          if (pstate.processingEvent) {
            pstate.getProxyHandler().startProxy(env);
          }
        }

        Wrapper wpr = d.getAnnotation(Wrapper.class);

        if (wpr != null) {
          pstate.processingWrapper = true;
          pstate.getWrapperHandler(wpr).start(env);
        }
      }

      public void visitMethodDeclaration(MethodDeclaration d) {
        //msg.printNotice("Method: " + d.getSimpleName());

        if (classDepth > 1) {
          // In inner class
          return;
        }

        if (pstate.processingEvent) {
          if (d.getAnnotation(NoProxy.class) == null) {
            Collection<Modifier> mods = d.getModifiers();

            if (mods.contains(Modifier.PUBLIC)) {
              pstate.getProxyHandler().proxyMethod(env, d);
            }
          }
        }

        if (pstate.processingWrapper) {
          if (d.getAnnotation(NoWrap.class) == null) {
            Collection<Modifier> mods = d.getModifiers();

            if (mods.contains(Modifier.PUBLIC)) {
              pstate.getWrapperHandler().method(env, d, false);
            }
          }
        }

        IcalProperty ip = d.getAnnotation(IcalProperty.class);
        if (ip != null) {
          pstate.getIcalPropertyHandler().property(env, ip, d);
          if (pstate.debug) {
            msg.printNotice("IcalProperty: " + ip.pindex().name());
          }
        }

        IcalProperties ips = d.getAnnotation(IcalProperties.class);
        if (ips != null) {
          IcalPropertyHandler iph = pstate.getIcalPropertyHandler();

          for (IcalProperty ip1: ips.value()) {
            iph.property(env, ip1, d);
            if (pstate.debug) {
              msg.printNotice("IcalProperty: " + ip1.pindex().name());
            }
          }
        }

        /*
        Collection<AnnotationMirror> ams = d.getAnnotationMirrors();
        if (ams != null) {
          for (AnnotationMirror am: ams) {
            msg.printNotice("AnnotationMirror: " + am.toString());
          }
        }*/
      }
    }

    private class DeclEndVisitor extends SimpleDeclarationVisitor {
      //private Messager msg;
      private ProcessState pstate;

      /**
       * @param msg
       * @param pstate
       */
      public DeclEndVisitor(Messager msg, ProcessState pstate) {
        //this.msg = msg;
        this.pstate = pstate;
      }

      public void visitClassDeclaration(ClassDeclaration d) {
        //msg.printNotice("End class: " + d.getQualifiedName());

        classDepth--;

        if (classDepth == 1) {
          // Finished inner class
          return;
        }

        if (pstate.processingEvent) {
          if ("org.bedework.calfacade.BwEvent".equals(d.getQualifiedName())) {
            pstate.getProxyHandler().endProxy(env);

            pstate.processingEvent = false;
          }
        }

        if (pstate.processingWrapper) {
          ClassDeclaration superD = d.getSuperclass().getDeclaration();
          if (superD.getQualifiedName().startsWith("org.bedework")) {
            processSuper(superD);
          }

          pstate.getWrapperHandler().end(env);

          pstate.endWrapperHandler();
        }
      }

      private void processSuper(ClassDeclaration d) {
        for (MethodDeclaration md: d.getMethods()) {
          if (md.getAnnotation(NoWrap.class) == null) {
            Collection<Modifier> mods = md.getModifiers();

            if (mods.contains(Modifier.PUBLIC)) {
              pstate.getWrapperHandler().method(env, md, true);
            }
          }
        }

        ClassDeclaration superD = d.getSuperclass().getDeclaration();
        if (superD.getQualifiedName().startsWith("org.bedework")) {
          processSuper(superD);
        }
      }
    }
  }
}
