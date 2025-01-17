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
package org.bedework.calsvc.directory;

import org.bedework.access.WhoDefs;
import org.bedework.base.exc.BedeworkException;
import org.bedework.base.exc.BedeworkUnimplementedException;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.configs.DirConfigProperties;
import org.bedework.calfacade.configs.LdapConfigProperties;
import org.bedework.calfacade.exc.CalFacadeErrorCode;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.TreeSet;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.InitialLdapContext;

/** A directory implementation which interacts with a CardDAV service.
 *
 * @author Mike Douglass douglm - rpi.edu
 * @version 1.0
 */
public class CardDAVDirImpl extends AbstractDirImpl {
  static class VcardEntry {
    String account;
    String calAddr;
    long timestamp;
    //VCard card;
  }

  /* ===================================================================
   *  The following should not change the state of the current users
   *  group.
   *  =================================================================== */

  @Override
  public boolean validPrincipal(final String href) {
    // XXX Not sure how we might use this for admin users.
    if (href == null) {
      return false;
    }

    /* Use a map to avoid the lookup if possible.
     * This does mean that we retain traces of a user who gets deleted until
     * we flush.
     */

    if (lookupValidPrincipal(href)) {
      return true;
    }

    // XXX We should look up the user in our directory and fail it if it's not there.
    boolean valid = !href.startsWith("invalid");  // allow some testing

    try {
      // Is it parseable?
      new URI(href);
    } catch (final Throwable t) {
      valid = false;
    }

    if (valid) {
      addValidPrincipal(href);
    }

    return valid;
  }

  @Override
  public BwPrincipalInfo getDirInfo(final BwPrincipal<?> p) {
    // LDAP implement
    return null;
  }

  @Override
  public Collection<BwGroup<?>> getGroups(final BwPrincipal<?> val) {
    return getGroups(getProps(), val);
  }

  @Override
  public Collection<BwGroup<?>> getAllGroups(final BwPrincipal<?> val) {
    final var groups = getGroups(getProps(), val);
    final var allGroups = new TreeSet<>(groups);

    for (final var grp: groups) {
      final var gg = getAllGroups(grp);
      if (!gg.isEmpty()) {
        allGroups.addAll(gg);
      }
    }

    return allGroups;
  }

  /** Show whether user entries can be modified with this
   * class. Some sites may use other mechanisms.
   *
   * @return boolean    true if group maintenance is implemented.
   */
  @Override
  public boolean getGroupMaintOK() {
    return false;
  }

  @Override
  public Collection<BwGroup<?>> getAll(final boolean populate) {
    final var gs = getGroups(getProps(), null);

    if (!populate) {
      return gs;
    }

    for (final var g: gs) {
      getMembers(g);
    }

    return gs;
  }

  @Override
  public void getMembers(final BwGroup<?> group) {
    getGroupMembers(getProps(), group);
  }

  /* ====================================================================
   *  The following are available if group maintenance is on.
   * ==================================================================== */

  @Override
  public void addGroup(final BwGroup<?> group) {
    if (findGroup(group.getAccount()) != null) {
      throw new BedeworkException(CalFacadeErrorCode.duplicateAdminGroup);
    }
    throw new BedeworkUnimplementedException();
  }

  @Override
  public BwGroup<?> findGroup(final String name) {
    return findGroup(getProps(), name);
  }

  @Override
  public void addMember(final BwGroup<?> group, final BwPrincipal<?> val) {
    final var ag = findGroup(group.getAccount());

    if (ag == null) {
      throw new BedeworkException("Group " + group + " does not exist");
    }

    /* val must not already be present on any paths to the root.
     * We'll assume the possibility of more than one parent.
     */

    if (!checkPathForSelf(group, val)) {
      throw new BedeworkException(CalFacadeErrorCode.alreadyOnGroupPath);
    }

    /*
    ag.addGroupMember(val);

    BwAdminGroupEntry ent = new BwAdminGroupEntry();

    ent.setGrp(ag);
    ent.setMember(val);

    getSess().save(ent);
    */
    throw new BedeworkUnimplementedException();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.AdminGroups#removeMember(org.bedework.calfacade.BwGroup, org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public void removeMember(final BwGroup<?> group, final BwPrincipal<?> val) {
    final var ag = findGroup(group.getAccount());

    if (ag == null) {
      throw new BedeworkException("Group " + group + " does not exist");
    }

    /*
    ag.removeGroupMember(val);

    sess.namedQuery("findAdminGroupEntry");
    sess.setEntity("grp", group);
    sess.setInt("mbrId", val.getId());

    /* This is what I want to do but it inserts 'true' or 'false'
    sess.setBool("isgroup", (val instanceof BwGroup));
    * /
    if (val instanceof BwGroup) {
      sess.setString("isgroup", "T");
    } else {
      sess.setString("isgroup", "F");
    }

    BwAdminGroupEntry ent = (BwAdminGroupEntry)sess.getUnique();

    if (ent == null) {
      return;
    }

    getSess().delete(ent);
    */
    throw new BedeworkUnimplementedException();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.AdminGroups#removeGroup(org.bedework.calfacade.BwGroup)
   */
  @Override
  public void removeGroup(final BwGroup<?> group) {
    // Remove all group members
    /*
    HibSession sess = getSess();

    sess.namedQuery("removeAllGroupMembers");
    sess.setEntity("gr", group);
    sess.executeUpdate();

    // Remove from any groups

    sess.namedQuery("removeFromAllGroups");
    sess.setInt("mbrId", group.getId());

    /* This is what I want to do but it inserts 'true' or 'false'
    sess.setBool("isgroup", (val instanceof BwGroup));
    * /
    sess.setString("isgroup", "T");
    sess.executeUpdate();

    sess.delete(group);
    */
    throw new BedeworkUnimplementedException();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.AdminGroups#updateGroup(org.bedework.calfacade.svc.BwAdminGroup)
   */
  @Override
  public void updateGroup(final BwGroup<?> group) {
    //getSess().saveOrUpdate(group);
    throw new BedeworkUnimplementedException();
  }

  @Override
  public Collection<BwGroup<?>> findGroupParents(final BwGroup<?> group) {
    throw new BedeworkUnimplementedException();
  }

  /* ====================================================================
   *  Protected methods.
   * ==================================================================== */

  @Override
  public String getConfigName() {
    return "user-ldap-group";
  }

  /* ====================================================================
   *  Private methods.
   * ==================================================================== */

  private boolean checkPathForSelf(final BwGroup<?> group,
                                   final BwPrincipal<?> val) {
    if (group.equals(val)) {
      return false;
    }

    /* get all parents of group and try again * /

    HibSession sess = getSess();

    /* Want this
    sess.createQuery("from " + BwAdminGroup.class.getName() + " ag " +
                     "where mbr in elements(ag.groupMembers)");
    sess.setEntity("mbr", val);
    * /

    sess.namedQuery("getGroupParents");
    sess.setInt("grpid", group.getId());

    Collection parents = sess.getList();

    Iterator it = parents.iterator();

    while (it.hasNext()) {
      BwAdminGroup g = (BwAdminGroup)it.next();

      if (!checkPathForSelf(g, val)) {
        return false;
      }
    }

    return true;
    */
    throw new BedeworkUnimplementedException();
  }

  private InitialLdapContext createLdapInitContext(final LdapConfigProperties props) {
    final Properties env = new Properties();

    // Map all options into the JNDI InitialLdapContext env

    env.setProperty(Context.INITIAL_CONTEXT_FACTORY,
                    props.getInitialContextFactory());

    env.setProperty(Context.SECURITY_AUTHENTICATION,
                    props.getSecurityAuthentication());

    env.setProperty(Context.SECURITY_PROTOCOL,
                    props.getSecurityProtocol());

    env.setProperty(Context.PROVIDER_URL, props.getProviderUrl());

    final String protocol = env.getProperty(Context.SECURITY_PROTOCOL);
    String providerURL = env.getProperty(Context.PROVIDER_URL);

    if (providerURL == null) {
      providerURL = "ldap://localhost:" +
      ((protocol != null) && protocol.equals("ssl") ? "389" : "636");
      env.setProperty(Context.PROVIDER_URL, providerURL);
    }

    if (props.getAuthDn() != null) {
      env.setProperty(Context.SECURITY_PRINCIPAL, props.getAuthDn());
      env.put(Context.SECURITY_CREDENTIALS, props.getAuthPw());
    }

    final InitialLdapContext ctx;

    try {
      ctx = new InitialLdapContext(env, null);
      if (debug()) {
        debug("Logged into LDAP server, " + ctx);
      }

      return ctx;
    } catch(final Throwable t) {
      if (debug()) {
        error(t);
      }
      throw new BedeworkException(t);
    }
  }

  /* Search for a group to ensure it exists
   *
   */
  private BwGroup<?> findGroup(final DirConfigProperties dirProps, final String groupName) {
    final var props = (LdapConfigProperties)dirProps;
    InitialLdapContext ctx = null;

    try {
      ctx = createLdapInitContext(props);

      final var matchAttrs = new BasicAttributes(true);

      matchAttrs.put(props.getGroupIdAttr(), groupName);

      final String[] idAttr = {props.getGroupIdAttr()};

      BwGroup<?> group = null;
      final var response = ctx.search(props.getGroupContextDn(),
                                              matchAttrs, idAttr);
      while (response.hasMore()) {
//        SearchResult sr = (SearchResult)response.next();
//        Attributes attrs = sr.getAttributes();

        if (group != null) {
          throw new BedeworkException("org.bedework.ldap.groups.multiple.result");
        }

        group = new BwGroup<>();
        group.setAccount(groupName);
        group.setPrincipalRef(makePrincipalUri(groupName, WhoDefs.whoTypeGroup));
      }

      return group;
    } catch(final Throwable t) {
      if (debug()) {
        error(t);
      }
      throw new RuntimeException(t);
    } finally {
      // Close the context to release the connection
      if (ctx != null) {
        closeContext(ctx);
      }
    }
  }

  /* Return all groups for principal == null or all groups for which principal
   * is a member
   *
   */
  private Collection<BwGroup<?>> getGroups(final DirConfigProperties dirProps,
                                        final BwPrincipal<?> principal) {
    final var props = (LdapConfigProperties)dirProps;
    InitialLdapContext ctx = null;
    String member = null;

    if (principal != null) {
      if (principal.getKind() == WhoDefs.whoTypeUser) {
        member = getUserEntryValue(props, principal);
      } else if (principal.getKind() == WhoDefs.whoTypeGroup) {
        member = getGroupEntryValue(props, principal);
      }
    }

    try {
      ctx = createLdapInitContext(props);

      final var matchAttrs = new BasicAttributes(true);

      if (member != null) {
        matchAttrs.put(props.getGroupMemberAttr(), member);
      }

      final String[] idAttr = {props.getGroupIdAttr()};

      final var groups = new ArrayList<BwGroup<?>>();
      final var response = ctx.search(props.getGroupContextDn(),
                                              matchAttrs, idAttr);
      while (response.hasMore()) {
        final var sr = response.next();
        final Attributes attrs = sr.getAttributes();

        final var nmAttr = attrs.get(props.getGroupIdAttr());
        if (nmAttr.size() != 1) {
          throw new BedeworkException("org.bedework.ldap.groups.multiple.result");
        }

        final var group = new BwGroup<>();
        group.setAccount(nmAttr.get(0).toString());
        group.setPrincipalRef(makePrincipalUri(group.getAccount(),
                                               WhoDefs.whoTypeGroup));

        groups.add(group);
      }

      return groups;
    } catch(final Throwable t) {
      if (debug()) {
        error(t);
      }
      throw new BedeworkException(t);
    } finally {
      // Close the context to release the connection
      if (ctx != null) {
        closeContext(ctx);
      }
    }
  }

  /* Find members for given group
   *
   */
  private void getGroupMembers(final DirConfigProperties dirProps, final BwGroup<?> group) {
    final var props = (LdapConfigProperties)dirProps;
    InitialLdapContext ctx = null;

    try {
      ctx = createLdapInitContext(props);

      BasicAttributes matchAttrs = new BasicAttributes(true);

      matchAttrs.put(props.getGroupIdAttr(), group.getAccount());

      final String[] memberAttr = {props.getGroupMemberAttr()};

      ArrayList<String> mbrs = null;

      boolean beenHere = false;

      var response = ctx.search(props.getGroupContextDn(),
                                matchAttrs, memberAttr);
      while (response.hasMore()) {
        final var sr = response.next();
        final var attrs = sr.getAttributes();

        if (beenHere) {
          throw new BedeworkException("org.bedework.ldap.groups.multiple.result");
        }

        beenHere = true;

        final var membersAttr =
                attrs.get(props.getGroupMemberAttr());
        mbrs = new ArrayList<>();

        for (int m = 0; m < membersAttr.size(); m ++) {
          mbrs.add(membersAttr.get(m).toString());
        }
      }
      // LDAP We need a way to search recursively for groups.

      /* Search for each user in the group */
      final var memberContext = props.getGroupMemberContextDn();
      final var memberSearchAttr = props.getGroupMemberSearchAttr();
      final String[] idAttr = {props.getGroupMemberUserIdAttr(),
                               props.getGroupMemberGroupIdAttr(),
                               "objectclass"};

      for (final var mbr: mbrs) {
        if (memberContext != null) {
          matchAttrs = new BasicAttributes(true);

          matchAttrs.put(memberSearchAttr, mbr);

          response = ctx.search(memberContext, matchAttrs, idAttr);
        } else {
          response = ctx.search(memberContext, null, idAttr);
        }

        if (response.hasMore()) {
          final var sr = response.next();
          final var attrs = sr.getAttributes();

          final var ocsAttr = attrs.get("objectclass");
          final var userOc = props.getUserObjectClass();
          final var groupOc = props.getGroupObjectClass();
          boolean isGroup = false;

          for (int oci = 0; oci < ocsAttr.size(); oci++) {
            final var oc = ocsAttr.get(oci).toString();
            if (userOc.equals(oc)) {
              break;
            }

            if (groupOc.equals(oc)) {
              isGroup = true;
              break;
            }
          }

          final BwPrincipal<?> p;
          final Attribute attr;

          if (isGroup) {
            p = BwPrincipal.makeGroupPrincipal();

            attr = attrs.get(props.getGroupMemberGroupIdAttr());
          } else {
            p = BwPrincipal.makeUserPrincipal();

            attr = attrs.get(props.getGroupMemberUserIdAttr());
          }

          if (attr.size() != 1) {
            throw new BedeworkException("org.bedework.ldap.groups.multiple.result");
          }

          p.setAccount(attr.get(0).toString());
          p.setPrincipalRef(makePrincipalUri(p.getAccount(), p.getKind()));
          group.addGroupMember(p);
        }
      }
    } catch(final Throwable t) {
      if (debug()) {
        error(t);
      }
      throw new BedeworkException(t);
    } finally {
      // Close the context to release the connection
      if (ctx != null) {
        closeContext(ctx);
      }
    }

    /* Recursively fetch members of groups that are members. */

    for (final var g: group.getGroups()) {
      getGroupMembers(props, g);
    }
  }

  /* Return the entry we will find in a group identifying this user
   */
  private String getUserEntryValue(final LdapConfigProperties props,
                                   final BwPrincipal<?> p) {
    return makeUserDn(props, p);
  }

  /* Return the entry we will find in a group identifying this group
   */
  private String getGroupEntryValue(final LdapConfigProperties props,
                                    final BwPrincipal<?> p) {
    return makeGroupDn(props, p);
  }

  private String makeUserDn(final LdapConfigProperties props,
                            final BwPrincipal<?> p) {
    return props.getUserDnPrefix() + p.getAccount() +
           props.getUserDnSuffix();
  }

  private String makeGroupDn(final LdapConfigProperties props,
                             final BwPrincipal<?> p) {
    return props.getGroupDnPrefix() + p.getAccount() +
           props.getGroupDnSuffix();
  }

  private void closeContext(final InitialLdapContext ctx) {
    if (ctx != null) {
      try {
        ctx.close();
      } catch (final Throwable t) {}
    }
  }
}
