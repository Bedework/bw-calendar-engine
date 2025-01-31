package org.bedework.calcore.rw.common.dao;

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public interface EventsDAO extends DAOBase {
  List<BwEvent> getEventsByName(String colPath,
                                String name);

  BwEventAnnotation getEventsAnnotationName(
          String colPath,
          String name);

  void deleteTombstonedEvent(String colPath,
                             String uid);

  List<?> getSynchEventObjects(String path,
                               String token);

  List<String> getChildrenEntities(
          String parentPath,
          int start,
          int count);

  Iterator<BwEventAnnotation> getEventAnnotations();

  @SuppressWarnings("unchecked")
  Collection<BwEventAnnotation> getEventOverrides(BwEvent ev);

  /* Return the name of any event which has the same uid
   */
  String calendarGuidExists(BwEvent val,
                            boolean annotation,
                            boolean adding);

  boolean calendarNameExists(BwEvent val,
                             boolean annotation,
                             boolean adding);

  @SuppressWarnings("unchecked")
  Collection<BwEventAnnotation> getAnnotations(
          BwEvent val);

  <T extends BwEvent> List<T> eventQuery(
          Class<T> cl,
          String colPath,
          String guid,
          BwEvent master,
          Boolean overrides);
}
