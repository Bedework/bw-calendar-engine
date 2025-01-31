package org.bedework.calcore.rw.common.dao;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.CollectionSynchInfo;

import java.sql.Timestamp;
import java.util.List;

public interface CalendarsDAO extends DAOBase {
  CollectionSynchInfo getSynchInfo(String path,
                                   String token);

  List<BwCalendar> findCollectionAlias(String aliasPath,
                                       String ownerHref);

  BwCalendar getCollection(String path);

  boolean collectionExists(String path);

  void touchCollection(BwCalendar col,
                       Timestamp ts);

  void updateCollection(BwCalendar val);

  void addCollection(BwCalendar val);

  void removeCalendarFromAuthPrefs(BwCalendar val);

  void deleteCalendar(BwCalendar col);

  boolean isEmptyCollection(BwCalendar val);

  List<BwCalendar> getSynchCollections(
          String path,
          String token);

  List<BwCalendar> getPathPrefix(String path);

  List<String> getChildrenCollections(String parentPath,
                                      int start,
                                      int count);

  @SuppressWarnings("unchecked")
  List<BwCalendar> getChildCollections(String parentPath);

  List<LastModAndPath> getChildLastModsAndPaths(String parentPath);

  List<BwCalendar> getCollections(List<String> paths);

  void removeTombstoned(String path);

  void removeTombstonedVersion(BwCalendar val);

  BwCalendar getTombstonedCollection(String path);

  record LastModAndPath(String path,
                        String timestamp,
                        Integer sequence) {}
}
