/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calfacade;

/** There are limitations on what may be placed in each type of collection,
 *  e.g folders cannot hold entities, guids must be unique in calendars etc.
 *
 *  <p>This class allows us to create a list of characteristics for each
 *  calendar type.
 */
public class CollectionInfo {
  /** Allows us to use this as a parameter */
  public int collectionType;

  /** Is this 'special', e.g. Trash */
  public boolean special;

  /** Children allowed in this collection */
  public boolean childrenAllowed;

  /** Objects in here can be indexed */
  public boolean indexable;

  /** Guid + recurrence must be unique */
  public boolean uniqueKey;

  /** Allow annotations */
  public boolean allowAnnotations;

  /** Freebusy allowed */
  public boolean allowFreeBusy;

  /** Can be the target of an alias */
  public boolean canAlias;

  /** Only calendar entities here */
  public boolean onlyCalEntities;

  /** Scheduling allowed here */
  public boolean scheduling;

  /** Shareable/publishable */
  public boolean shareable;

  /** Provision at creation and cehck for them */
  public boolean provision;

  /**
   * @param collectionType the type
   * @param special true for special
   * @param childrenAllowed true/false
   * @param indexable true/false
   * @param uniqueKey e.g. false for inbox
   * @param allowAnnotations can we annotate the entities
   * @param allowFreeBusy on this collection?
   * @param canAlias can we symlink it
   * @param onlyCalEntities one entities in this
   * @param scheduling a scheduling collection?
   * @param shareable is it shareable?
   * @param provision do we provision?
   */
  public CollectionInfo(final int collectionType,
                        final boolean special,
                        final boolean childrenAllowed,
                        final boolean indexable,
                        final boolean uniqueKey,
                        final boolean allowAnnotations,
                        final boolean allowFreeBusy,
                        final boolean canAlias,
                        final boolean onlyCalEntities,
                        final boolean scheduling,
                        final boolean shareable,
                        final boolean provision) {
    this.collectionType = collectionType;
    this.special = special;
    this.childrenAllowed = childrenAllowed;
    this.indexable = indexable;
    this.uniqueKey = uniqueKey;
    this.allowAnnotations = allowAnnotations;
    this.allowFreeBusy = allowFreeBusy;
    this.canAlias = canAlias;
    this.onlyCalEntities = onlyCalEntities;
    this.scheduling = scheduling;
    this.shareable = shareable;
    this.provision = provision;
  }
}
