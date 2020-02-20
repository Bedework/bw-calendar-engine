/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calfacade.svc;

import org.bedework.calfacade.BwOrganizer;

/** Scheduling information about an event (which may be recurring).
 * User: mike Date: 2/19/20 Time: 17:26
 */
public class SchedulingInfo {
  private boolean masterSuppressed;

  private int maxAttendees;

  private BwOrganizer organizer;

  private boolean organizerSchedulingObject;

  private boolean attendeeSchedulingObject;

  /**
   * @param val true for suppressed
   */
  public void setMasterSuppressed(final boolean val) {
    masterSuppressed = val;
  }

  /**
   * @return true if suppressed
   */
  public boolean getMasterSuppressed() {
    return masterSuppressed;
  }

  /**
   *
   * @param val max number in event and any overrides
   */
  public void setMaxAttendees(final int val) {
    maxAttendees = val;
  }

  /**
   *
   * @return max number in event and any overrides
   */
  public int getMaxAttendees() {
    return maxAttendees;
  }

  /** Set the organizer
   *
   * @param val    BwOrganizer organizer
   */
  public void setOrganizer(final BwOrganizer val) {
    organizer = val;
  }

  /** Get the organizer
   *
   * @return BwOrganizer   the organizer
   */
  public BwOrganizer getOrganizer() {
    return organizer;
  }

  /** True if this is a valid organizer scheduling object. (See CalDAV
   * scheduling specification). This can be set false (and will be on copy) to
   * suppress sending of invitations, e.g. for a draft.
   *
   * <p>When the event is added this flag will be set true if the appropriate
   * conditions are satisfied.
   *
   * @param val boolean  True if this is a valid organizer scheduling object
   */
  public void setOrganizerSchedulingObject(final boolean val) {
    organizerSchedulingObject = val;
  }

  /**
   *
   * @return boolean
   */
  public boolean getOrganizerSchedulingObject() {
    return organizerSchedulingObject;
  }

  /** True if this is a valid attendee scheduling object.
   * (See CalDAV scheduling specification)
   *
   * @param val boolean True if this is a valid attendee scheduling object
   */
  public void setAttendeeSchedulingObject(final boolean val) {
    attendeeSchedulingObject = val;
  }

  /**
   *
   * @return boolean
   */
  public boolean getAttendeeSchedulingObject() {
    return attendeeSchedulingObject;
  }
}
