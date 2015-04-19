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
package org.bedework.calsvc;

import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.UserAuth;

import java.util.List;

/** Class used by UseAuth to do calls into CalSvci
 *
 */
public class UserAuthCallBack extends UserAuth.CallBack {
  CalSvc svci;

  UserAuthCallBack(final CalSvc svci) {
    this.svci = svci;
  }

  @Override
  public BwPrincipal getPrincipal(final String account) throws CalFacadeException {
    return svci.getUsersHandler().getUser(account);
  }

  @Override
  public UserAuth getUserAuth() throws CalFacadeException {
    return svci.getUserAuth();
  }

  @Override
  public void delete(final BwAuthUser val) throws CalFacadeException {
    svci.getCal().delete(val);
  }

  @Override
  public void add(final BwAuthUser val) throws CalFacadeException {
    svci.getCal().addAuthUser(val);
  }

  @Override
  public void update(final BwAuthUser val) throws CalFacadeException {
    svci.getCal().updateAuthUser(val);
  }

  @Override
  public BwAuthUser getAuthUser(final String href) throws CalFacadeException {
    return svci.getCal().getAuthUser(href);
  }

  @Override
  public List<BwAuthUser> getAll() throws CalFacadeException {
    return svci.getCal().getAll();
  }
}