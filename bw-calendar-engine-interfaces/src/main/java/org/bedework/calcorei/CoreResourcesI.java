package org.bedework.calcorei;

import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.util.misc.response.GetEntityResponse;

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
   */
  BwResource getResource(String href,
                         int desiredAccess);

  /** Fetch a resource object.
   *
   * @param href of resource
   * @param desiredAccess we need
   * @return response with status and possible BwResource object
   */
  GetEntityResponse<BwResource> fetchResource(String href,
                                              int desiredAccess);

  /** Get resource content given the resource. It will be set in the resource
   * object
   *
   * @param  val BwResource
   */
  void getResourceContent(BwResource val);

  /** Get resources to which this user has access - content is not fetched.
   *
   * @param  path           String path to containing collection
   * @param forSynch true if a synch report
   * @param token synch token or null
   * @param count   return this many < 0 for all
   * @return List     of BwResource or null/empty if done
   */
  List<BwResource> getResources(String path,
                                boolean forSynch,
                                String token,
                                int count);

  /**
   * @param val resource to add
   */
  void add(BwResource val);

  /**
   * @param r resource owning content
   * @param rc content to add
   */
  void addContent(BwResource r,
                  BwResourceContent rc);

  /**
   * @param val resource
   */
  void saveOrUpdate(BwResource val);

  /**
   * @param r resource owning content
   * @param val resource content
   */
  void saveOrUpdateContent(BwResource r,
                           BwResourceContent val);

  /**
   * @param href of resource to delete
   * @throws RuntimeException on fatal error
   */
  void deleteResource(String href);

  /**
   * @param val resource to delete
   */
  void delete(BwResource val);

  /**
   * @param r resource owning content
   * @param val resource content to delete
   */
  void deleteContent(BwResource r,
                     BwResourceContent val);
}
