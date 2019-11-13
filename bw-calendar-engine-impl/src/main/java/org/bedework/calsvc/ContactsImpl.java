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

import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SimpleFilterParser.ParseResult;
import org.bedework.calfacade.responses.GetEntitiesResponse;
import org.bedework.calfacade.responses.GetEntityResponse;
import org.bedework.calfacade.responses.Response;
import org.bedework.calsvci.Contacts;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

import java.util.Collection;

import static org.bedework.calfacade.indexing.BwIndexer.docTypeContact;

/** Class which handles manipulation of Contacts.
 *
 * @author Mike Douglass   douglm - rpi.edu
 */
public class ContactsImpl
        extends EventPropertiesImpl<BwContact>
        implements Contacts {
  /** Constructor
  *
  * @param svci calsvc object
  */
  public ContactsImpl(final CalSvc svci) {
    super(svci);
  }

  @Override
  public void init(final boolean adminCanEditAllPublic) {
    super.init(BwContact.class.getCanonicalName(),
               adminCanEditAllPublic);
  }

  @Override
  String getDocType() {
    return docTypeContact;
  }

  @Override
  Collection<BwContact> fetchAllIndexed(final boolean publick,
                                        final String ownerHref)
          throws CalFacadeException {
    return filterDeleted(getIndexer(publick, 
                                    ownerHref).fetchAllContacts());
  }

  @Override
  BwContact fetchIndexedByUid(final String uid) throws CalFacadeException {
    return getIndexer().fetchContact(uid, PropertyInfoIndex.UID);
  }

  @Override
  BwContact fetchIndexed(final String href) throws CalFacadeException {
    return getIndexer().fetchContact(href, PropertyInfoIndex.HREF);
  }

  @Override
  GetEntityResponse<BwContact> findPersistent(final BwContact val,
                                              final String ownerHref) {
    return findPersistent(val.getFinderKeyValue(), ownerHref);
  }

  @Override
  public boolean exists(final Response resp,
                        final BwContact val) {
    var getResp = findPersistent(val.getFinderKeyValue(),
                                 val.getOwnerHref());

    if (getResp.isError()) {
      Response.fromResponse(resp, getResp);
      return false;
    }

    return getResp.isOk();
  }

  @Override
  public BwContact find(final BwString val) throws CalFacadeException {
    return getIndexer().fetchContact(val.getValue(),
                                     PropertyInfoIndex.CN,
                                     PropertyInfoIndex.VALUE);
  }

  @Override
  public GetEntitiesResponse<BwContact> find(final String fexpr,
                                             final int from,
                                             final int size) {
    final ParseResult pr = getSvc().getFilterParser().parse(fexpr,
                                                            false,
                                                            null);

    if (!pr.ok) {
      return Response.error(new GetEntitiesResponse<>(), pr.message);
    }

    return getIndexer().findContacts(pr.filter, from, size);
  }
}

