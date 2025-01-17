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

import org.bedework.base.exc.BedeworkAccessException;
import org.bedework.base.exc.BedeworkException;
import org.bedework.caldav.util.filter.parse.EventQuery;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.filter.SimpleFilterParser.ParseResult;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.responses.GetFilterDefResponse;
import org.bedework.calsvci.FiltersI;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.Response;

import ietf.params.xml.ns.caldav.FilterType;

import java.util.Collection;

/** This acts as an interface to the database for filters.
 *
 * @author Mike Douglass
 */
class Filters extends CalSvcDb implements FiltersI {
  /** Constructor
   *
   * @param svci the interface
   */
  Filters(final CalSvc svci) {
    super(svci);
  }

  @Override
  public ParseResult parse(final BwFilterDef val) {
    final String def = val.getDefinition();

    /* Require xml filters to start with <?xml
     */

    if ((def.length() > 5) && (def.startsWith("<?xml"))) {
      // Assume xml filter

      final ParseResult pr = new ParseResult();
      
      try {
        final FilterType f = org.bedework.caldav.util.filter.parse.Filters.parse(def);
        final EventQuery eq = org.bedework.caldav.util.filter.parse.Filters.getQuery(f);
        val.setFilters(eq.filter);
        pr.ok = true;
      } catch (final Throwable t) {
        pr.ok = false;
        pr.message = t.getMessage();
        pr.be = new BedeworkException(t);
      }

      return pr;
    }

    // Assume simple expression filter
    final String source = "BwFilterDef:" + val.getOwnerHref() + ":" +
            val.getName();
    final ParseResult pr = getSvc().getFilterParser().parse(def,
                                                      false,
                                                      source);

    if (pr.ok) {
      val.setFilters(pr.filter);
    }

    return pr;
  }


  @Override
  public void validate(final String val) {
    try {
      org.bedework.caldav.util.filter.parse.Filters.parse(val);
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  @Override
  public void save(final BwFilterDef val) {
    getSvc().setupOwnedEntity(val,
                              getPrincipal().getPrincipalRef());
    validate(val.getDefinition());

    getCal().save(val, getSvc().getEntityOwner());
  }

  @Override
  public GetFilterDefResponse get(final String name) {
    final GetFilterDefResponse gfdr = new GetFilterDefResponse();

    try {
      final BwFilterDef fdef = getCal()
              .getFilterDef(name, getSvc().getEntityOwner());
      if (fdef == null) {
        gfdr.setStatus(Response.Status.notFound);
      } else {
        gfdr.setStatus(Response.Status.ok);
        gfdr.setFilterDef(fdef);
      }
    } catch (final BedeworkException be) {
      gfdr.setStatus(Response.Status.failed);
      gfdr.setMessage(be.getLocalizedMessage());
    }
    
    return gfdr;
  }

  @Override
  public Collection<BwFilterDef> getAll() {
    final BwPrincipal owner = getSvc().getEntityOwner(); // This can affect the query if done later

    return getCal().getAllFilterDefs(owner);
  }

  @Override
  public void update(final BwFilterDef val) {
    if (!getSvc().getSuperUser() &&
            !getPrincipal().getPrincipalRef().equals(val.getOwnerHref())) {
      throw new BedeworkAccessException();
    }

    getCal().update(val);
  }

  @Override
  public void delete(final String name) {
    getCal().deleteFilterDef(name, getSvc().getEntityOwner());
  }

  @Override
  public ParseResult parseSort(final String val) {
    return getSvc().getFilterParser().parseSort(val);
  }

  @Override
  public int reindex(final BwIndexer indexer) {
    final Collection<BwFilterDef> filters = getAll();
    if (Util.isEmpty(filters)) {
      return 0;
    }

    for (final BwFilterDef f: filters) {
      indexer.indexEntity(f);
    }

    return filters.size();
  }
}
