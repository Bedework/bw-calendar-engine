# Release Notes

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased (5.1.0-SNAPSHOT)
* Incorrect value parameter (false instead of true) for admin groups fetch broke the restore.

## [5.0.0] - 2025-07-21
* First jakarta release
* Handle rrule changes
* Use safe xml parser

## [4.1.5] - 2025-02-06
* Pre-jakarta release
* Update library versions
* Overlooked change for hibernate -> jpa changes
* Switch to use DbSession from bw-database.
* Switch to javax.persistence.Query 
* Drop the hibernate interceptor. Non jpa and not used.
* Remove the flush before commit - not needed. 
* Remove evict before delete - just wrong. 
* Align more with the other implementations.
* ...

## [4.1.4] - 2024-11-26
* Update library versions

## [4.1.3] - 2024-11-26
* Update library versions
* Move MySysiCalendar into separate class renamed to BwSysiCalendar
* Don't get scheduling info if we don't need it.
* Use scheduling info for freebusy. 
* Use getRecipientParticipants instead of getRecipients 
* New method getOnlyParticipant 
* Fix copyTo and other small fixes
* Use getParticpantAddrs for BwCalDAVEvent.getAttendeeUris 
* Tidy up exception handling in BwSysIntfImpl 
* For maxAttendees consides recipients only 
* Move attendee and organizer copying int SchedulingInfo 
* Fix isOwner in calcomp.js 
* Show messages in poll tab
* Mostly add code to correctly save and restore participants in the indexer.
* ...

## [4.1.2] - 2024-04-03
* Update library versions

## [4.1.1] - 2024-03-23
* Update library versions

## [4.1.0] - 2024-03-23
* Update library versions
* Next stage in removing the bw-xml module.
  * Move synch xml
  * Added feature packs for the 2 wsdl deployments
  * Removed the xml feature pack from the build.
  * removed the xml modules from the build.
* Remove some throws clauses. Don't wrap exceptions in RunTimeException. Try not invalidating the session. There are multiple requests and this may cause errors in the one that got through
* Add code to query opensearch to get scroll context info.
* Add calls to close scroll contexts. Waiting for timeout causes problems
* Reimplement nested search for x-props
* Fix restore of BwGeo - stored as BigDecimal value
* ...

## [4.0.0] - 2022-02-12
* Whole bunch of changes to handle ssl for opensearch. 
* Util util-opensearch classes for client. Add some extra config for keystore. 
* Fix up feature pack build for docker image
* Switch from ElasticSearch to OpenSearch because of licence issues.
* Add user authentication to elasticsearch
* Use bedework-parent for builds.
* Remove all code dealing with recurrence instances. No longer need to maintain them in the db.
* set refresh=true for deletions. Not an ideal solution but safer. Need to investigate other options - e.g. get ES to do WAIT_UNTIL or query and delete myself.
* Was deleting db copy of event in pending inbox - caused stale state exception at flush.
* Pass class loader as parameter when creating new objects. JMX interactions were failing.
* Don't try getSpecialCalendar in logon if readonly
* Apparently the old apache codec version of Base64 did mime encoding.
* Move a load of stuff out of CalSvcDb to try to make it a read-only class. 
* Use java Base64 rather than apache codec
* Move some methods into the only classes that use them.
* Remove assignGuid from CalSvcDb - add to event object. 
* Delete other unused methods from CalSvcDb
* Return a Response object from delete event. 
* Remove CalSvcDb method 
* Return Response object from copyMoveName
* Fix to avoid "A different object with the same identifier value was already associated with the session" when deleting admin group.
* Fix for category deletion from prefs - bad SQL
* Finish off move to new common project
* ...

## [3.13.2] - 2020-03-23
* Filter out instances for unexpanded fetch of events
* Missing changes for bad characters in event strings. Was inserting unwanted newline literals.
* Changes to try to fix issues with scheduling recurring events. Now passing one of tests it failed. 
* Working on adding a SchedulingInfo object to avoid some of the repeated searching of event data.
* Changes to handle issues caused by scheduling a recurring event with attendee(s) only in overrides.
* Need to determine if an update requires a scheduling message. Change one of the event updates to be a client driven update and add an appropriate flag. 
* ChangeTable code was incorrect for multi-value - was alsways flagging them as changed. Also try to skip updates to user-private values BwAlarm for the moment. Still apparently not fully working.
* Don't convert date values to UTC by applying timezone. Just append 0 hours.
* More resource fixes. Was not nindexing the tombstoned version
* Add new method to fetch a resource and indicate it's absence or otherwise. 
* Add a deleteResource method to delete given the href. If resource is absent in db will still try to unindex. This will clean up after some errors. Still not working as some structural changes are required for the dav interface - e.g. alllow delete of any resource via href without knowing its type.
* Split out back end resource handling into separate class. Fix a bunch of issues related to tombstoned resources. Queries were not excluding tombstoned entities and additions were not removing tombstoned. 
* Fix a bug in the read-only classes - wrong index was being selected.
* Don't delete special calendars. Do everything else - e.g. remove events, children... Also suppress sharing notifications when it's a dav tester cleanup.
* Simplify and another alarm bad comparison fix. Was comparing the same value.
* Implement a touch index which ignores version exceptions.
* Queue up indexing operations to close. Indexing was immediate adn allowed the appearance of partial changes in the index. Seems to work ok but can be effectively disabled by having CalintfBase::indexEntity do immediate index.
* Change collection info so that pending inbox gets created for new users. Queuing indexing means that special collection creates fail as the subsequent fetch is from the index.
* Fix equality check. Was comparing self with self for a couple of fields
* ...

## [3.13.1] - 2019-10-16
* Disable the use of read-only sessions for read-only methods. Current code will attempt to provision new accounts causing read-only exceptions. Can possibly fix this so that provisioning is carried out with a read-write svci.
* For the moment, reinstate checking on poll collection at each request.
* Fix a bug in accessing the href field from a search result. Not convinced this doesn't have its own issues (seem to be retrieving the entire object just to get the href) but the previous code was failing
* Implement fetching of groups from the index. 
* Remove currentMode from many low level classes - replace wit a guestMode flag 
* Remove method appendPublicOrOwnerTerm from hibernate EventQueryBuilder - not used. 
* Add a publicAuth field to SvciParams
* Add a new appType webpublicauth and use it. 
* Fix a problem with calsuite mappings - we were losing owner, creator, publick flag and acl 
* Add readonly flag to svci pars constructors. Set it for public auth client.
* Set timestamps when creating new user calendar otherwise we get inconsistent values. 
* Also use the new wait method when indexing them.
* Add method which waits for entity to appear in index
* Need to call getClient() rather than refer to variable
* Check home is indexed before trying to index it.
* Use TermsFilterBuilder for collections
* Switch to PooledHttpClient
* Lowercase account unless mixed case environment variable BEDEWORK_MIXEDCASE_ACCOUNTS is set to true

## [3.13.0] - 2019-08-27
* Use all_content for queries
* Add easy caslsuite setup command
* Unshelve and fix updates to es 7.2.0. Minor changes to util classes, query structure differs and mappings significantly simpler. 
* Use RestHighLevelClient throughout. 
* Removed option to run ES embedded. Need to update install scripts to install ES if desired.

## [3.12.7] - 2019-06-27
* Fix extracting category names
* List collections for user
* Make categories public
* Avoid NPE when null entries get in the list of x-props (possibly because they were deleted outside of hibernate)

## [3.12.6] - 2019-04-15
* Add method to set Response status from a response
* Svci pars wasn't handling the readonly flag properly. Worked for unauth but wasn't turning on readonly for authenticated methods. 
* Restores were failing because the fake event property calpath code was getting an NPE - no principal. Fixed it so principal isn't needed. Caused cascading updates up the stack. Dropped the principal object where possible. Generally only need the href. 
* Resource content handling was broken in restore. Should just set the byte value and create the blob when we have a session 
* Drop loader-repository elements from jboss-app.xml

## [3.12.5] - 2019-01-27
* Update library versions

## [3.12.4] - 2019-01-16
* Update library versions
* Index wrapper type for calsuite - not calsuite itself
* Not closing the base 64 writer until after getting the content. Was losing bytes.

## [3.12.3] - 2019-01-07
* Update library versions
* Need to touch entity after setting access before indexing.
* Calling wrong indexer to update resource content
* Fix sys monitor mbean - extend correct class
* Need to save entity in response.
* Add cache to SvcSimpleFilterParser so we don't repeatedly attempt to fetch children of collections.
* Need to get explicit index type for resource content
* Should be returning an empty array when the event is not found.

## [3.12.2] - 2018-12-14
* Update library versions

## [3.12.1] - 2018-11-28
* Update library versions
* Further fix to indexing of resources
* Crawler storing resource content index in wrong place.
* Another fix to (public) resource indexing.
* Fix to resource indexing
* Fix indexer fetch for event properties and a missing public flag for prefs
* Attach and trigger returning wrong value type.
* Move event broken. Colpath set incorrectly, tombstone not working.
* Was counting principals twice
* Significant change to indexing to try to resolve the contacts issue and prepare for upgrade. 
* ES v7 will require only one type per index. To prepare the index was split into many. Requires a doctype parameter to be added to most calls, significant changes to the (re)indexing process and other associated changes. 
* Almost all calendar engine classes were affected in some way - mostly relatively minor. 
* Configuration changes: no longer have a public/user calendar name. The location of the mappings is a directory - not a file and there are multiple mapping files under directories named with the lowercased doctype name.
* Move hibsession into calcore/hibernate from interfaces
* Remove dependency on hibernate in facade. Needed to add dependencies on dom4j to client and engine
* Updater code wasn't handling wrapped x-props correctly. Remove didn't work because generated prop wasn't equal. Initial fixes to parameter handling
* Add code to trigger a reindex of an event when change tokens don't match
* Handle altrep in wrapped x-prop
* ...

## [3.12.0] - 2018-04-08
* Many changes up to this point. github log may be best reference.

