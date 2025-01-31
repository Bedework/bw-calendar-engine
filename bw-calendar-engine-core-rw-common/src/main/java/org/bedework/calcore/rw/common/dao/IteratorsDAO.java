package org.bedework.calcore.rw.common.dao;

import java.util.List;

public interface IteratorsDAO extends DAOBase {
  List<?> getBatch(String className,
                   int start,
                   int size);

  List<?> getBatch(String className,
                   String colPath,
                   String ownerHref,
                   boolean publicAdmin,
                   int start,
                   int size);
}
