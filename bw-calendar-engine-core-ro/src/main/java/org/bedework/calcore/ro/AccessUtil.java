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
package org.bedework.calcore.ro;

import org.bedework.access.Access;
import org.bedework.access.AccessPrincipal;
import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.Acl;
import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.access.PrivilegeSet;
import org.bedework.base.exc.BedeworkAccessException;
import org.bedework.base.exc.BedeworkException;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.ShareableEntity;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.util.AccessUtilI;
import org.bedework.calfacade.wrappers.CollectionWrapper;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** An access helper class. This class makes some assumptions about the
 * classes it deals with but there are no explicit hibernate, or other
 * persistence engine, dependencies.
 *
 * <p>It assumes access to the parent object when needed,
 * continuing on up to the root. For systems which do not allow for a
 * retrieval of the parent on calls to the getCollection method, the getParent
 * method for this class will need to be overridden. This would presumably
 * take place within the core implementation.
 *
 * @author Mike Douglass
 */
public class AccessUtil implements Logged, AccessUtilI {
  /** For evaluating access control
   */
  private Access access;

  private PrincipalInfo cb;

  /**
   */
  public interface CollectionGetter {
    /** Get a collection given the path. No access checks are performed.
     *
     * @param  path          String path of collection
     * @return BwCollection null for unknown collection
       */
    BwCollection getCollectionNoCheck(String path);
  }

  private CollectionGetter cg;

  @Override
  public void init(final PrincipalInfo cb) {
    this.cb = cb;
    access = new Access();
  }

  /**
   * @param cg a collection getter
   */
  public void setCollectionGetter(final CollectionGetter cg) {
    this.cg = cg;
  }

  /** Called at request start
   *
   */
  @Override
  public void open() {
  }

  /** Called at request end
   *
   */
  @Override
  public void close() {
    //pathInfoMap.flush();
  }

  /** Called to get the parent object for a shared entity. This method should be
   * overriden if explicit calls to the back end collection are required.
   *
   * @param val the shareable entity
   * @return parent collection or null.
   */
  @Override
  public BwCollection getParent(final BwShareableContainedDbentity<?> val) {
    return cg.getCollectionNoCheck(val.getColPath());
  }

  /* ====================================================================
   *                   Access control
   * ==================================================================== */

  /*
  @Override
  public String getDefaultPublicAccess() {
    return Access.getDefaultPublicAccess();
  }

  @Override
  public String getDefaultPersonalAccess() {
    return Access.getDefaultPersonalAccess();
  }
   */

  @Override
  public void changeAccess(final ShareableEntity ent,
                           final Collection<Ace> aces,
                           final boolean replaceAll) {
    try {
      final Acl acl = checkAccess(ent,
                                  PrivilegeDefs.privWriteAcl,
                                  false).getAcl();

      final Collection<Ace> allAces;
      if (replaceAll) {
        allAces = aces;
      } else {
        allAces = acl.getAces();
        allAces.addAll(aces);
      }


      ent.setAccess(new Acl(allAces).encodeStr());

//      pathInfoMap.flush();
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  @Override
  public void defaultAccess(final ShareableEntity ent,
                            final AceWho who) {
    try {
      final Acl acl = checkAccess(ent,
                                  PrivilegeDefs.privWriteAcl,
                                  false).getAcl();

      /* Now remove any access */

      if (acl.removeWho(who) != null) {
        ent.setAccess(acl.encodeStr());

//        pathInfoMap.flush();
      }
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  @Override
  public Collection<? extends ShareableEntity>
                checkAccess(final Collection<? extends ShareableEntity> ents,
                                final int desiredAccess,
                                final boolean alwaysReturn) {
    final List<ShareableEntity> out = new ArrayList<>();

    for (final var sdbe: ents) {
      if (checkAccess(sdbe, desiredAccess, alwaysReturn).getAccessAllowed()) {
        out.add(sdbe);
      }
    }

    return out;
  }

  @Override
  public CurrentAccess checkAccess(final ShareableEntity ent,
                                   final int desiredAccess,
                        final boolean alwaysReturnResult) {
    if (ent == null) {
      return null;
    }

    if (ent instanceof CollectionWrapper) {
      final CollectionWrapper col = (CollectionWrapper)ent;

      final CurrentAccess ca = col.getCurrentAccess(desiredAccess);

      if (ca != null) {
        // Checked already

        if (debug()) {
          debug("Access " + desiredAccess +
                        " already checked for " +
                        cb.getPrincipal().getPrincipalRef() +
                        " and allowed=" + ca.getAccessAllowed());
        }

        if (!ca.getAccessAllowed() && !alwaysReturnResult) {
          throw new BedeworkAccessException();
        }

        return ca;
      }
    }

    if (debug()) {
      final String cname = ent.getClass().getName();
      final String ident;
      if (ent instanceof BwCollection) {
        ident = ((BwCollection)ent).getPath();
      } else {
        ident = ((BwUnversionedDbentity<?>)ent).getHref();
      }
      debug("Check access by " +
                    cb.getPrincipal().getPrincipalRef() +
                    " for object " +
                    cname.substring(cname.lastIndexOf(".") + 1) +
                    " ident=" + ident +
                    " desiredAccess = " + desiredAccess);
    }

    try {
      final long startTime = System.currentTimeMillis();
      CurrentAccess ca = null;

      final AccessPrincipal owner = cb.getPrincipal(ent.getOwnerHref());
      if (debug()) {
        debug("After getPrincipal - took: " + (System.currentTimeMillis() - startTime));
      }
      
      if (owner == null) {
        error("Principal(owner) " + ent.getOwnerHref() +
                      " does not exist");
        if (!alwaysReturnResult) {
          throw new BedeworkAccessException();
        }
        return new CurrentAccess(false);
      }

      PrivilegeSet maxPrivs = null;

      final char[] aclChars;

      if (ent instanceof BwCollection) {
        final BwCollection cal = (BwCollection)ent;
        final String path = cal.getPath();

        /* Special case the access to the user root e.g /user and
         * the 'home' directory, e.g. /user/douglm
         */

        /* I think this was wrong. For superuser we want to see the real
         * access but they are going to be allowed access whatever.
        if (userRootPath.equals(path)) {
          ca = new CurrentAccess();

          if (getSuperUser()) {
            ca.privileges = PrivilegeSet.makeDefaultOwnerPrivileges();
          } else {
            ca.privileges = PrivilegeSet.makeDefaultNonOwnerPrivileges();
          }
        } else if (path.equals(userHomePathPrefix + account)){
          // Accessing user home directory
          if (getSuperUser()) {
            ca = new CurrentAccess();

            ca.privileges = PrivilegeSet.makeDefaultOwnerPrivileges();
          } else {
            // Set the maximumn access
            maxPrivs = PrivilegeSet.userHomeMaxPrivileges;
          }
        }
         */
        if (!cb.getSuperUser()) {
          final var userCalPath = Util.buildPath(
                  BasicSystemProperties.colPathEndsWithSlash,
                  "/",
                  BasicSystemProperties.userCollectionRoot);

          if (userCalPath.equals(path)) {
            ca = Acl.defaultNonOwnerAccess;
          } else if (path.equals(Util.buildPath(
                  BasicSystemProperties.colPathEndsWithSlash,
                  userCalPath,
                  "/",
                  owner.getAccount()))) {
            // Accessing user home directory
            // Set the maximumn access

            maxPrivs = PrivilegeSet.userHomeMaxPrivileges;
          }
        }
      }

      if (maxPrivs == null) {
        maxPrivs = cb.getMaximumAllowedPrivs();
      } else if (cb.getMaximumAllowedPrivs() != null) {
        maxPrivs = PrivilegeSet.filterPrivileges(maxPrivs,
                                                 cb.getMaximumAllowedPrivs());
      }

      if (ca == null) {
        /* Not special. getAclChars provides merged access for the current
         * entity.
         */
        aclChars = getAclChars(ent, cb.getSuperUser());

        if (aclChars == null) {
          if (cb.getSuperUser()) {
            if (debug()) {
              debug("Override no aclchars for superuser");
            }
            ca = Acl.forceAccessAllowed(Acl.defaultNonOwnerAccess);
          } else {
            error("Unable to fetch aclchars for " + ent);
            if (!alwaysReturnResult) {
              throw new BedeworkAccessException();
            }
            return new CurrentAccess(false);
          }
        } else {
          if (debug()) {
            debug("aclChars = " + new String(aclChars));
          }

          if (desiredAccess == PrivilegeDefs.privAny) {
            ca = access.checkAny(cb, cb.getPrincipal(), owner,
                                 aclChars, maxPrivs);
          } else if (desiredAccess == PrivilegeDefs.privRead) {
            ca = access.checkRead(cb, cb.getPrincipal(), owner,
                                  aclChars, maxPrivs);
          } else if (desiredAccess == PrivilegeDefs.privWrite) {
            ca = access.checkReadWrite(cb, cb.getPrincipal(), owner,
                                       aclChars, maxPrivs);
          } else {
            ca = access.evaluateAccess(cb, cb.getPrincipal(), owner,
                                       desiredAccess, aclChars,
                                       maxPrivs);
          }
        }
      }

      if (cb.getSuperUser()) {
        // Nobody can stop us - BWAAA HAA HAA

        /* Override rather than just create a readable access as code further
         * up expects a valid filled in object.
         */
        if (debug() && !ca.getAccessAllowed()) {
          debug("Override for superuser");
        }
        ca = Acl.forceAccessAllowed(ca);
      }

      if (ent instanceof CollectionWrapper) {
        final CollectionWrapper col = (CollectionWrapper)ent;

        col.setCurrentAccess(ca, desiredAccess);
      }

      if (debug()) {
        debug("access allowed: " + ca.getAccessAllowed());
      }

      if (!ca.getAccessAllowed() && !alwaysReturnResult) {
        throw new BedeworkAccessException();
      }

      return ca;
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /* If the entity is not a collection we merge the access in with the container
   * access then return the merged aces. We do this because we call getPathInfo
   * with a collection entity. That method will recurse up to the root.
   *
   * For a collection we just use the access for the collection.
   *
   * The container access might be cached in the pathInfoTable.
   */
  private char[] getAclChars(final ShareableEntity ent,
                             final boolean isSuperUser) {
    if ((!(ent instanceof BwEventProperty)) &&
        (ent instanceof BwShareableContainedDbentity)) {
      final BwCollection container;

      if (ent instanceof BwCollection) {
        container = (BwCollection)ent;
      } else {
        container = getParent((BwShareableContainedDbentity<?>)ent);
      }

      if (container == null) {
        return null;
      }

      final String path = container.getPath();

      final CollectionWrapper wcol = (CollectionWrapper)container;

      final String aclStr;
      char[] aclChars = null;

      /* Get access for the parent first if we have one */
      final BwCollection parent = getParent(wcol);

      if (parent != null) {
        aclStr = new String(merged(getAclChars(parent, isSuperUser),
                                   parent.getPath(),
                                   wcol.getAccess()));
      } else if (wcol.getAccess() != null) {
        aclStr = wcol.getAccess();
      } else if (wcol.getColPath() == null) {
        // At root
        if (!isSuperUser) {
          throw new BedeworkException(
                  "Collections must have default access set at root: " +
                          wcol.getPath());
        }

        warn("Collections must have default access set at root: " +
                     wcol.getPath());
        return null;
      } else {
        // Missing collection in hierarchy
        throw new BedeworkException("Missing collection in hierarchy for " +
                                             wcol.getPath());
      }

      if (aclStr != null) {
        aclChars = aclStr.toCharArray();
      }

      if (ent instanceof BwCollection) {
        return aclChars;
      }

      /* Create a merged access string from the entity access and the
       * container access
       */

      return merged(aclChars, path, ent.getAccess());
    }

    /* This is a way of making other objects sort of shareable.
     * The objects are locations, sponsors and categories.
     * (also calsuite)
     *
     * We store the default access in the owner principal and manipulate that to give
     * us some degree of sharing.
     *
     * In effect, the owner becomes the container for the object.
     */

    String aclString = null;
    final String entAccess = ent.getAccess();
    final BwPrincipal<?> owner =
            (BwPrincipal<?>)cb.getPrincipal(ent.getOwnerHref());

    if (ent instanceof BwCategory) {
      aclString = owner.getCategoryAccess();
    } else if (ent instanceof BwLocation) {
      aclString = owner.getLocationAccess();
    } else if (ent instanceof BwContact) {
      aclString = owner.getContactAccess();
    }

    if (aclString == null) {
      if (entAccess == null) {
        if (ent.getPublick()) {
          return Access.getDefaultPublicAccess().toCharArray();
        }
        return Access.getDefaultPersonalAccess().toCharArray();
      }
      return entAccess.toCharArray();
    }

    if (entAccess == null) {
      return aclString.toCharArray();
    }

    try {
      Acl acl = Acl.decode(entAccess.toCharArray());
      acl = acl.merge(aclString.toCharArray(), "/owner");

      return acl.getEncoded();
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  private char[] merged(final char[] parentAccess,
                        final String path,
                        final String access) {
    try {
      Acl acl = null;

      if (access != null) {
        acl = Acl.decode(access.toCharArray());
      }

      if (acl == null) {
        acl = Acl.decode(parentAccess, path);
      } else {
        acl = acl.merge(parentAccess, path);
      }

      return acl.encodeAll();
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  /* Create a merged Acl for the given collection. Progresses up to the root of
   * the system merging acls as it goes.
   * /
  private PathInfo getPathInfo(BwCollection cal) {
    PathInfo pi = new PathInfo();
    Collection<Ace> aces = new ArrayList<Ace>();

    pi.path = cal.getPath();

    try {
      while (cal != null) {
        String aclString = cal.getAccess();

        if ((aclString != null) && (aclString.length() == 0)) {
          warn("Zero length acl for " + cal.getPath());
          aclString = null;
        }

        /* Validity check. The system MUST be set up so that /public and /user
         * have the appropriate default access stored as the acl string.
         *
         * Failure to do so results in incorrect evaluation of access. We'll
         * check it here.
         * /
        if (aclString == null) {
          if (cal.getColPath() == null) {
            // At root
            throw new BedeworkException("Collections must have default access set at root");
          }
        } else if (aces.isEmpty()) {
          aces.addAll(Acl.decode(aclString).getAces());
        } else {
          // leaf acls go last
          Collection<Ace> moreAces = new ArrayList<Ace>();
          moreAces.addAll(Acl.decode(aclString.toCharArray(),
                                     cal.getPath()).getAces());
          moreAces.addAll(aces);

          aces = moreAces;
        }

        cal = getParent(cal);
      }

      pi.pathAcl = new Acl(aces);
      pi.encoded = pi.pathAcl.encodeAll();

      return pi;
    } catch (BedeworkException be) {
      throw be;
    } catch (Throwable t) {
      throw new BedeworkException(t);
    }
  } */

  /* Update the merged Acl for the given collection.
   * Doesn't work because any children in the table need the access changing.
  private void updatePathInfo(BwCollection cal, Acl acl) {
    try {
      String path = cal.getPath();
      PathInfo pi = pathInfoMap.getInfo(path);

      if (pi == null) {
        pi = new PathInfo();

        pi.path = cal.getPath();
      }

      pi.pathAcl = acl;
      pi.encoded = acl.encodeAll();

      pathInfoMap.putInfo(path, pi);
    } catch (Throwable t) {
      throw new BedeworkException(t);
    }
  }
   */

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}

