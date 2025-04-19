package org.bedework.calcorei;

import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.calfacade.base.ShareableEntity;

import java.io.Serializable;
import java.util.Collection;

public interface CoreAccessI extends Serializable  {
  /** Change the access to the given collection entity.
   *
   * @param ent      BwShareableDbentity
   * @param aces     Collection of ace
   * @param replaceAll true to replace the entire access list.
   */
  void changeAccess(ShareableEntity ent,
                    Collection<Ace> aces,
                    boolean replaceAll);

  /** Remove any explicit access for the given who to the given collection entity.
   *
   * @param ent      A shareable entity
   * @param who      AceWho
   */
  void defaultAccess(ShareableEntity ent,
                     AceWho who);
}
