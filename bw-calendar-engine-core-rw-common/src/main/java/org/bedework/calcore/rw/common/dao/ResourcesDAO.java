package org.bedework.calcore.rw.common.dao;

import org.bedework.calfacade.BwResource;

import java.util.List;

public interface ResourcesDAO extends DAOBase {
  BwResource getResource(String name,
                         String colPath,
                         int desiredAccess);

  void getResourceContent(BwResource val);

  List<BwResource> getAllResources(String path,
                                   boolean forSynch,
                                   String token,
                                   int count);
}
