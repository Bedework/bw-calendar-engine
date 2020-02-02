package org.bedework.calcorei;

import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.exc.CalFacadeException;

import java.util.List;

/**
 * User: mike Date: 2/1/20 Time: 14:46
 */
public interface CoreResourcesI {
  /** Fetch a resource object.
   *
   * @param href of resource
   * @param desiredAccess we need
   * @return BwResource object or null
   * @throws CalFacadeException on fatal error
   */
  BwResource getResource(final String href,
                         final int desiredAccess) throws CalFacadeException;

  /** Get resource content given the resource. It will be set in the resource
   * object
   *
   * @param  val BwResource
   * @throws CalFacadeException on fatal error
   */
  void getResourceContent(BwResource val) throws CalFacadeException;

  /** Get resources to which this user has access - content is not fetched.
   *
   * @param  path           String path to containing collection
   * @param forSynch true if a synch report
   * @param token synch token or null
   * @param count   return this many < 0 for all
   * @return List     of BwResource or null/empty if done
   * @throws CalFacadeException on fatal error
   */
  List<BwResource> getResources(String path,
                                boolean forSynch,
                                String token,
                                int count) throws CalFacadeException;

  /**
   * @param val resource to add
   * @throws CalFacadeException on error
   */
  void add(final BwResource val) throws CalFacadeException;

  /**
   * @param r resource owning content
   * @param rc content to add
   * @throws CalFacadeException on error
   */
  void addContent(final BwResource r,
                  final BwResourceContent rc) throws CalFacadeException;

  /**
   * @param val resource
   * @throws CalFacadeException on fatal error
   */
  void saveOrUpdate(BwResource val) throws CalFacadeException;

  /**
   * @param r resource owning content
   * @param val resource content
   * @throws CalFacadeException on fatal error
   */
  void saveOrUpdateContent(BwResource r,
                           BwResourceContent val) throws CalFacadeException;

  /**
   * @param val resource to delete
   * @throws CalFacadeException on fatal error
   */
  void delete(final BwResource val) throws CalFacadeException;

  /**
   * @param r resource owning content
   * @param val resource content to delete
   * @throws CalFacadeException on fatal error
   */
  void deleteContent(BwResource r,
                     BwResourceContent val) throws CalFacadeException;
}
