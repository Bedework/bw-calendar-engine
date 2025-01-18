/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calsvc;

import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.misc.Util;
import org.bedework.base.response.GetEntitiesResponse;
import org.bedework.base.response.Response;
import org.bedework.util.security.PwEncryptionIntf;

import java.util.Collection;

import static org.bedework.calfacade.indexing.BwIndexer.DeletedState.noDeleted;

/**
 * User: mike Date: 6/30/21 Time: 22:53
 */
public class CalSvcHelperRw extends CalSvcDb {
  /**
   * @param svci - the interface
   */
  public CalSvcHelperRw(final CalSvc svci) {
    super(svci);
  }

  /** Set the owner and creator on a shareable entity.
   * Makes this visible outside the package.
   *
   * @param entity shareable entity
   * @param ownerHref - new owner
   */
  protected void setupSharableEntity(
          final BwShareableDbentity<?> entity,
          final String ownerHref) {
    getSvc().setupSharableEntity(entity, ownerHref);
  }

  /** Do NOT expose this via a public interface.
   * @return encrypter
   */
  protected PwEncryptionIntf getEncrypter() {
    return getSvc().getEncrypter();
  }

  protected BwCalendar getSpecialCalendar(final BwPrincipal<?> owner,
                                          final int calType,
                                          final boolean create,
                                          final int access) {
    return getCols().getSpecial(owner, calType, create, access);
  }

  /** Method which allows us to flag it as a scheduling action
   * NOTE: Only used by 1 class.
   *
   * @param cols collections
   * @param filter a filter
   * @param startDate start
   * @param endDate end
   * @param recurRetrieval expanded etc
   * @param freeBusy is this for freebusy
   * @return Collection of matching events
   */
  protected Collection<EventInfo> getEvents(
          final Collection<BwCalendar> cols,
          final FilterBase filter,
          final BwDateTime startDate, final BwDateTime endDate,
          final RecurringRetrievalMode recurRetrieval,
          final boolean freeBusy) {
    final Events events = (Events)getSvc().getEventsHandler();

    return events.getMatching(cols, filter, startDate, endDate,
                              null, noDeleted,
                              recurRetrieval, freeBusy);
  }

  /** Method which allows us to flag it as a scheduling action
   *
   * @param colPath path for collection
   * @param guid uid of event(s)
   * @return response with status and Collection<EventInfo> -
   *                collection as there may be more than
   *                one with this uid in the inbox.
   */
  protected GetEntitiesResponse<EventInfo> getEventsByUid(
          final String colPath,
          final String guid) {
    final Events events = (Events)getSvc().getEventsHandler();
    final GetEntitiesResponse<EventInfo> resp = new GetEntitiesResponse<>();

    try {
      final var ents = events.getByUid(colPath, guid, null,
                                       RecurringRetrievalMode.overrides);
      if (Util.isEmpty(ents)) {
        resp.setStatus(Response.Status.notFound);
      } else {
        resp.setEntities(ents);
      }

      return resp;
    } catch (final Throwable t) {
      return Response.error(resp, t);
    }
  }
}
