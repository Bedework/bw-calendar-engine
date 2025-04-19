package org.bedework.calcore.rw.common.dao;

import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.CollectionSynchInfo;

import java.sql.Timestamp;
import java.util.List;

public interface CollectionsDAO extends DAOBase {
  CollectionSynchInfo getSynchInfo(String path,
                                   String token);

  List<BwCollection> findCollectionAlias(String aliasPath,
                                         String ownerHref);

  BwCollection getCollection(String path);

  boolean collectionExists(String path);

  void touchCollection(BwCollection col,
                       Timestamp ts);

  void updateCollection(BwCollection val);

  void addCollection(BwCollection val);

  void removeCollectionFromAuthPrefs(BwCollection val);

  void deleteCollection(BwCollection col);

  boolean isEmptyCollection(BwCollection val);

  List<BwCollection> getSynchCollections(
          String path,
          String token);

  List<BwCollection> getPathPrefix(String path);

  List<String> getChildrenCollections(String parentPath,
                                      int start,
                                      int count);

  @SuppressWarnings("unchecked")
  List<BwCollection> getChildCollections(String parentPath);

  List<LastModAndPath> getChildLastModsAndPaths(String parentPath);

  List<BwCollection> getCollections(List<String> paths);

  void removeTombstoned(String path);

  void removeTombstonedVersion(BwCollection val);

  BwCollection getTombstonedCollection(String path);

  record LastModAndPath(String path,
                        String timestamp,
                        Integer sequence) {}
}
