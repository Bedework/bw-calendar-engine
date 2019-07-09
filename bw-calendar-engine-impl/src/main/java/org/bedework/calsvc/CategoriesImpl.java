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

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SimpleFilterParser.ParseResult;
import org.bedework.calfacade.responses.GetEntitiesResponse;
import org.bedework.calfacade.responses.Response;
import org.bedework.calsvci.Categories;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

import java.util.Collection;

import static org.bedework.calfacade.indexing.BwIndexer.docTypeCategory;

/** Class which handles manipulation of Categories.
 *
 * @author Mike Douglass   douglm - rpi.edu
 */
public class CategoriesImpl
        extends EventPropertiesImpl<BwCategory>
        implements Categories {
  /** Constructor
  *
  * @param svci calsvc object
  */
  public CategoriesImpl(final CalSvc svci) {
    super(svci);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void init(final boolean adminCanEditAllPublic) {
    super.init(BwCategory.class.getCanonicalName(),
               adminCanEditAllPublic);
  }

  @Override
  String getDocType() {
    return docTypeCategory;
  }

  @Override
  Collection<BwCategory> fetchAllIndexed(final boolean publick,
                                         final String ownerHref)
          throws CalFacadeException {
    return filterDeleted(getIndexer(publick,
                                    ownerHref).fetchAllCats());
  }

  @Override
  BwCategory fetchIndexedByUid(final String uid) throws CalFacadeException {
    return getIndexer().fetchCat(uid, PropertyInfoIndex.UID);
  }

  @Override
  BwCategory fetchIndexed(final String href) throws CalFacadeException {
    return getIndexer().fetchCat(href, PropertyInfoIndex.HREF);
  }

  @Override
  BwCategory findPersistent(final BwCategory val,
                            final String ownerHref) throws CalFacadeException {
    return findPersistent(val.getWord(), ownerHref);
  }

  @Override
  public boolean exists(final BwCategory cat) throws CalFacadeException {
    return findPersistent(cat.getWord(), cat.getOwnerHref()) != null;
  }

  @Override
  public BwCategory find(final BwString val) throws CalFacadeException {
    return getIndexer()
            .fetchCat(val.getValue(),
                      PropertyInfoIndex.CATEGORIES,
                      PropertyInfoIndex.VALUE);
  }

  @Override
  public GetEntitiesResponse<BwCategory> find(final String fexpr,
                                              final int from,
                                              final int size) {
    final ParseResult pr = getSvc().getFilterParser().parse(fexpr,
                                                            false,
                                                            null);

    if (!pr.ok) {
      return Response.error(new GetEntitiesResponse<>(), pr.message);
    }

    return getIndexer()
            .findCategories(pr.filter, from, size);
  }
}

