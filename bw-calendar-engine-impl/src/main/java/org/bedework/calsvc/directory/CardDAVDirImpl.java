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

import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.configs.DirConfigProperties;
import org.bedework.calfacade.configs.LdapConfigProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeUnimplementedException;

import org.bedework.access.WhoDefs;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.TreeSet;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchResult;
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
  public boolean validPrincipal(final String href) throws CalFacadeException {
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
    } catch (Throwable t) {
      valid = false;
    }

    if (valid) {
      addValidPrincipal(href);
    }

    return valid;
  }

  @Override
  public BwPrincipalInfo getDirInfo(final BwPrincipal p) throws CalFacadeException {
    // LDAP implement
    return null;
  }

  @Override
  public Collection<BwGroup> getGroups(final BwPrincipal val) throws CalFacadeException {
    return getGroups(getProps(), val);
  }

  @Override
  public Collection<BwGroup> getAllGroups(final BwPrincipal val) throws CalFacadeException {
    Collection<BwGroup> groups = getGroups(getProps(), val);
    Collection<BwGroup> allGroups = new TreeSet<BwGroup>(groups);

    for (BwGroup grp: groups) {
      Collection<BwGroup> gg = getAllGroups(grp);
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
  public Collection<BwGroup> getAll(final boolean populate) throws CalFacadeException {
    Collection<BwGroup> gs = getGroups(getProps(), null);

    if (!populate) {
      return gs;
    }

    for (BwGroup g: gs) {
      getMembers(g);
    }

    return gs;
  }

  @Override
  public void getMembers(final BwGroup group) throws CalFacadeException {
    getGroupMembers(getProps(), group);
  }

  /* ====================================================================
   *  The following are available if group maintenance is on.
   * ==================================================================== */

  @Override
  public void addGroup(final BwGroup group) throws CalFacadeException {
    if (findGroup(group.getAccount()) != null) {
      throw new CalFacadeException(CalFacadeException.duplicateAdminGroup);
    }
    throw new CalFacadeUnimplementedException();
  }

  /** Find a group given its name
   *
   * @param  name             String group name
   * @return AdminGroupVO   group object
   * @exception CalFacadeException If there's a problem
   */
  @Override
  public BwGroup findGroup(final String name) throws CalFacadeException {
    return findGroup(getProps(), name);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.AdminGroups#addMember(org.bedework.calfacade.BwGroup, org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public void addMember(final BwGroup group, final BwPrincipal val) throws CalFacadeException {
    BwGroup ag = findGroup(group.getAccount());

    if (ag == null) {
      throw new CalFacadeException("Group " + group + " does not exist");
    }

    /* val must not already be present on any paths to the root.
     * We'll assume the possibility of more than one parent.
     */

    if (!checkPathForSelf(group, val)) {
      throw new CalFacadeException(CalFacadeException.alreadyOnGroupPath);
    }

    /*
    ag.addGroupMember(val);

    BwAdminGroupEntry ent = new BwAdminGroupEntry();

    ent.setGrp(ag);
    ent.setMember(val);

    getSess().save(ent);
    */
    throw new CalFacadeUnimplementedException();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.AdminGroups#removeMember(org.bedework.calfacade.BwGroup, org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public void removeMember(final BwGroup group, final BwPrincipal val) throws CalFacadeException {
    BwGroup ag = findGroup(group.getAccount());

    if (ag == null) {
      throw new CalFacadeException("Group " + group + " does not exist");
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
    throw new CalFacadeUnimplementedException();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.AdminGroups#removeGroup(org.bedework.calfacade.BwGroup)
   */
  @Override
  public void removeGroup(final BwGroup group) throws CalFacadeException {
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
    throw new CalFacadeUnimplementedException();
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.AdminGroups#updateGroup(org.bedework.calfacade.svc.BwAdminGroup)
   */
  @Override
  public void updateGroup(final BwGroup group) throws CalFacadeException {
    //getSess().saveOrUpdate(group);
    throw new CalFacadeUnimplementedException();
  }

  @Override
  public Collection<BwGroup> findGroupParents(final BwGroup group) throws CalFacadeException {
    throw new CalFacadeUnimplementedException();
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

  private boolean checkPathForSelf(final BwGroup group,
                                   final BwPrincipal val) throws CalFacadeException {
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
    throw new CalFacadeUnimplementedException();
  }

  private InitialLdapContext createLdapInitContext(final LdapConfigProperties props)
          throws CalFacadeException {
    Properties env = new Properties();

    // Map all options into the JNDI InitialLdapContext env

    env.setProperty(Context.INITIAL_CONTEXT_FACTORY,
                    props.getInitialContextFactory());

    env.setProperty(Context.SECURITY_AUTHENTICATION,
                    props.getSecurityAuthentication());

    env.setProperty(Context.SECURITY_PROTOCOL,
                    props.getSecurityProtocol());

    env.setProperty(Context.PROVIDER_URL, props.getProviderUrl());

    String protocol = env.getProperty(Context.SECURITY_PROTOCOL);
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

    InitialLdapContext ctx = null;

    try {
      ctx = new InitialLdapContext(env, null);
      if (debug) {
        trace("Logged into LDAP server, " + ctx);
      }

      return ctx;
    } catch(Throwable t) {
      if (debug) {
        error(t);
      }
      throw new CalFacadeException(t);
    }
  }

  /* Search for a group to ensure it exists
   *
   */
  private BwGroup findGroup(final DirConfigProperties dirProps, final String groupName)
          throws CalFacadeException {
    LdapConfigProperties props = (LdapConfigProperties)dirProps;
    InitialLdapContext ctx = null;

    try {
      ctx = createLdapInitContext(props);

      BasicAttributes matchAttrs = new BasicAttributes(true);

      matchAttrs.put(props.getGroupIdAttr(), groupName);

      String[] idAttr = {props.getGroupIdAttr()};

      BwGroup group = null;
      NamingEnumeration response = ctx.search(props.getGroupContextDn(),
                                              matchAttrs, idAttr);
      while (response.hasMore()) {
//        SearchResult sr = (SearchResult)response.next();
//        Attributes attrs = sr.getAttributes();

        if (group != null) {
          throw new CalFacadeException("org.bedework.ldap.groups.multiple.result");
        }

        group = new BwGroup();
        group.setAccount(groupName);
        group.setPrincipalRef(makePrincipalUri(groupName, WhoDefs.whoTypeGroup));
      }

      return group;
    } catch(Throwable t) {
      if (debug) {
        error(t);
      }
      throw new CalFacadeException(t);
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
  private Collection<BwGroup> getGroups(final DirConfigProperties dirProps,
                                        final BwPrincipal principal)
          throws CalFacadeException {
    LdapConfigProperties props = (LdapConfigProperties)dirProps;
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

      BasicAttributes matchAttrs = new BasicAttributes(true);

      if (member != null) {
        matchAttrs.put(props.getGroupMemberAttr(), member);
      }

      String[] idAttr = {props.getGroupIdAttr()};

      ArrayList<BwGroup> groups = new ArrayList<BwGroup>();
      NamingEnumeration response = ctx.search(props.getGroupContextDn(),
                                              matchAttrs, idAttr);
      while (response.hasMore()) {
        SearchResult sr = (SearchResult)response.next();
        Attributes attrs = sr.getAttributes();

        Attribute nmAttr = attrs.get(props.getGroupIdAttr());
        if (nmAttr.size() != 1) {
          throw new CalFacadeException("org.bedework.ldap.groups.multiple.result");
        }

        BwGroup group = new BwGroup();
        group.setAccount(nmAttr.get(0).toString());
        group.setPrincipalRef(makePrincipalUri(group.getAccount(),
                                               WhoDefs.whoTypeGroup));

        groups.add(group);
      }

      return groups;
    } catch(Throwable t) {
      if (debug) {
        error(t);
      }
      throw new CalFacadeException(t);
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
  private void getGroupMembers(final DirConfigProperties dirProps, final BwGroup group)
          throws CalFacadeException {
    LdapConfigProperties props = (LdapConfigProperties)dirProps;
    InitialLdapContext ctx = null;

    try {
      ctx = createLdapInitContext(props);

      BasicAttributes matchAttrs = new BasicAttributes(true);

      matchAttrs.put(props.getGroupIdAttr(), group.getAccount());

      String[] memberAttr = {props.getGroupMemberAttr()};

      ArrayList<String> mbrs = null;

      boolean beenHere = false;

      NamingEnumeration response = ctx.search(props.getGroupContextDn(),
                                              matchAttrs, memberAttr);
      while (response.hasMore()) {
        SearchResult sr = (SearchResult)response.next();
        Attributes attrs = sr.getAttributes();

        if (beenHere) {
          throw new CalFacadeException("org.bedework.ldap.groups.multiple.result");
        }

        beenHere = true;

        Attribute membersAttr = attrs.get(props.getGroupMemberAttr());
        mbrs = new ArrayList<String>();

        for (int m = 0; m < membersAttr.size(); m ++) {
          mbrs.add(membersAttr.get(m).toString());
        }
      }
      // LDAP We need a way to search recursively for groups.

      /* Search for each user in the group */
      String memberContext = props.getGroupMemberContextDn();
      String memberSearchAttr = props.getGroupMemberSearchAttr();
      String[] idAttr = {props.getGroupMemberUserIdAttr(),
                         props.getGroupMemberGroupIdAttr(),
                         "objectclass"};

      for (String mbr: mbrs) {
        if (memberContext != null) {
          matchAttrs = new BasicAttributes(true);

          matchAttrs.put(memberSearchAttr, mbr);

          response = ctx.search(memberContext, matchAttrs, idAttr);
        } else {
          response = ctx.search(memberContext, null, idAttr);
        }

        if (response.hasMore()) {
          SearchResult sr = (SearchResult)response.next();
          Attributes attrs = sr.getAttributes();

          Attribute ocsAttr = attrs.get("objectclass");
          String userOc = props.getUserObjectClass();
          String groupOc = props.getGroupObjectClass();
          boolean isGroup = false;

          for (int oci = 0; oci < ocsAttr.size(); oci++) {
            String oc = ocsAttr.get(oci).toString();
            if (userOc.equals(oc)) {
              break;
            }

            if (groupOc.equals(oc)) {
              isGroup = true;
              break;
            }
          }

          BwPrincipal p = null;
          Attribute attr;

          if (isGroup) {
            p = BwPrincipal.makeGroupPrincipal();

            attr = attrs.get(props.getGroupMemberGroupIdAttr());
          } else {
            p = BwPrincipal.makeUserPrincipal();

            attr = attrs.get(props.getGroupMemberUserIdAttr());
          }

          if (attr.size() != 1) {
            throw new CalFacadeException("org.bedework.ldap.groups.multiple.result");
          }

          p.setAccount(attr.get(0).toString());
          p.setPrincipalRef(makePrincipalUri(p.getAccount(), p.getKind()));
          group.addGroupMember(p);
        }
      }
    } catch(Throwable t) {
      if (debug) {
        error(t);
      }
      throw new CalFacadeException(t);
    } finally {
      // Close the context to release the connection
      if (ctx != null) {
        closeContext(ctx);
      }
    }

    /* Recursively fetch members of groups that are members. */

    for (BwGroup g: group.getGroups()) {
      getGroupMembers(props, g);
    }
  }

  /* Return the entry we will find in a group identifying this user
   */
  private String getUserEntryValue(final LdapConfigProperties props,
                                   final BwPrincipal p) {
    return makeUserDn(props, p);
  }

  /* Return the entry we will find in a group identifying this group
   */
  private String getGroupEntryValue(final LdapConfigProperties props,
                                    final BwPrincipal p) {
    return makeGroupDn(props, p);
  }

  private String makeUserDn(final LdapConfigProperties props,
                            final BwPrincipal p) {
    return props.getUserDnPrefix() + p.getAccount() +
           props.getUserDnSuffix();
  }

  private String makeGroupDn(final LdapConfigProperties props,
                             final BwPrincipal p) {
    return props.getGroupDnPrefix() + p.getAccount() +
           props.getGroupDnSuffix();
  }

  private void closeContext(final InitialLdapContext ctx) {
    if (ctx != null) {
      try {
        ctx.close();
      } catch (Throwable t) {}
    }
  }
}
