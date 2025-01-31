package org.bedework.calcorei;

import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;

import java.util.Collection;

public interface CoreCalSuitesI {
  /** Get a detached instance of the calendar suite given the 'owning' group
   *
   * @param  group     BwAdminGroup
   * @return BwCalSuite null for unknown calendar suite
   */
  BwCalSuite get(BwAdminGroup group);

  /** Get a (live) calendar suite given the name
   *
   * @param  name     String name of calendar suite
   * @return BwCalSuiteWrapper null for unknown calendar suite
   */
  BwCalSuite getCalSuite(String name);

  /** Get calendar suites to which this user has access
   *
   * @return Collection     of BwCalSuiteWrapper
   */
  Collection<BwCalSuite> getAllCalSuites();

  /**
   * @param val to add and index
   */
  void add(BwCalSuite val);

  /**
   * @param val to update and index
   */
  void update(BwCalSuite val);

  /**
   * @param val calsuite to delete and unindex
   */
  void delete(BwCalSuite val);
}
