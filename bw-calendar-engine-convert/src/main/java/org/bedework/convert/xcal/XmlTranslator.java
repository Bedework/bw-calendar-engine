/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.convert.xcal;

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.convert.EventTimeZonesRegistry;
import org.bedework.convert.IcalTranslator;
import org.bedework.convert.ical.BwEvent2Ical;
import org.bedework.convert.ical.VFreeUtil;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.xml.XmlEmit;
import org.bedework.util.xml.tagdefs.XcalTags;

import ietf.params.xml.ns.icalendar_2.ArrayOfComponents;
import ietf.params.xml.ns.icalendar_2.BaseComponentType;
import ietf.params.xml.ns.icalendar_2.IcalendarType;
import ietf.params.xml.ns.icalendar_2.VcalendarType;
import ietf.params.xml.ns.icalendar_2.VeventType;
import ietf.params.xml.ns.icalendar_2.VfreebusyType;
import ietf.params.xml.ns.icalendar_2.VjournalType;
import ietf.params.xml.ns.icalendar_2.VtodoType;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Version;

import java.util.Collection;
import java.util.Iterator;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

/**
 * User: mike Date: 5/7/20 Time: 22:43
 */
public class XmlTranslator extends IcalTranslator {
  /**
   * Constructor:
   *
   * @param cb IcalCallback object for retrieval of entities
   */
  public XmlTranslator(final IcalCallback cb) {
    super(cb);
  }

  /**
   * @param val event
   * @param methodType icalendar method
   * @param pattern to control output
   * @return XML IcalendarType
   * @throws CalFacadeException on fatal error
   */
  public IcalendarType toXMLIcalendar(final EventInfo val,
                                      final int methodType,
                                      final IcalendarType pattern,
                                      final boolean wrapXprops) throws CalFacadeException {
    final IcalendarType ical = Xutil.initCalendar(prodId, methodType);
    final VcalendarType vcal = ical.getVcalendar().get(0);

    ArrayOfComponents aoc = vcal.getComponents();

    if (aoc == null) {
      aoc = new ArrayOfComponents();
      vcal.setComponents(aoc);
    }

    BwEvent ev = val.getEvent();
    JAXBElement<? extends BaseComponentType> el;

    VcalendarType vc = null;

    if ((pattern != null) &&
            !pattern.getVcalendar().isEmpty()) {
      vc = pattern.getVcalendar().get(0);
    }

    final BaseComponentType bc = matches(vc, ev.getEntityType());
    if ((vc != null) && (bc == null)) {
      return ical;
    }

    if (!ev.getSuppressed()) {
      if (ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
        el = ToXEvent.toComponent(ev, false, wrapXprops, bc);
      } else {
        el = ToXEvent.toComponent(ev, false, wrapXprops, bc);
      }

      if (el != null) {
        aoc.getBaseComponent().add(el);
      }
    }

    if (val.getNumOverrides() == 0) {
      return ical;
    }

    for (final EventInfo oei: val.getOverrides()) {
      ev = oei.getEvent();
      el = ToXEvent.toComponent(ev, true, wrapXprops, bc);

      if (el != null) {
        aoc.getBaseComponent().add(el);
      }
    }

    if (val.getNumContainedItems() > 0) {
      for (final EventInfo aei: val.getContainedItems()) {
        ev = aei.getEvent();
        el = ToXEvent.toComponent(ev, true, wrapXprops, bc);

        if (el != null) {
          aoc.getBaseComponent().add(el);
        }
      }
    }

    return ical;
  }

  /** Write a collection of calendar data as xml
   *
   * @param vals collection of calendar data
   * @param methodType    int value fromIcalendar
   * @param xml for output
   * @throws CalFacadeException on fatal error
   */
  public void writeXmlCalendar(final Collection<EventInfo> vals,
                               final int methodType,
                               final XmlEmit xml) throws CalFacadeException {
    xml.addNs(new XmlEmit.NameSpace(XcalTags.namespace, "X"), false);

    xml.openTag(XcalTags.icalendar);
    xml.openTag(XcalTags.vcalendar);

    xml.openTag(XcalTags.properties);

    xmlProp(xml, Property.PRODID, XcalTags.textVal, prodId);
    xmlProp(xml, Property.VERSION, XcalTags.textVal,
            Version.VERSION_2_0.getValue());

    xml.closeTag(XcalTags.properties);

    boolean componentsOpen = false;

    if (!cb.getTimezonesByReference()) {
      final Calendar cal = newIcal(methodType); // To collect timezones

      addIcalTimezones(cal, vals);

      // Emit timezones
      for (final Object o: cal.getComponents()) {
        if (!(o instanceof VTimeZone)) {
          continue;
        }

        if (!componentsOpen) {
          xml.openTag(XcalTags.components);
          componentsOpen = true;
        }

        xmlComponent(xml, (Component)o);
      }
    }

    String currentPrincipal = null;
    final BwPrincipal principal = cb.getPrincipal();

    if (principal != null) {
      currentPrincipal = principal.getPrincipalRef();
    }

    for (final Object o : vals) {
      if (o instanceof EventInfo) {
        final EventInfo ei = (EventInfo)o;
        final BwEvent ev = ei.getEvent();

        final EventTimeZonesRegistry tzreg = new EventTimeZonesRegistry(
                this, ev);

        final Component comp;
        if (ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
          comp = VFreeUtil.toVFreeBusy(ev);
        } else {
          comp = BwEvent2Ical.convert(ei, false, tzreg,
                                      currentPrincipal);
        }

        if (!componentsOpen) {
          xml.openTag(XcalTags.components);
          componentsOpen = true;
        }

        xmlComponent(xml, comp);

        if (ei.getNumOverrides() > 0) {
          for (final EventInfo oei : ei.getOverrides()) {
            xmlComponent(xml, BwEvent2Ical.convert(oei,
                                                   true,
                                                   tzreg,
                                                   currentPrincipal));
          }
        }
      }
    }

    if (componentsOpen) {
      xml.closeTag(XcalTags.components);
    }

    xml.closeTag(XcalTags.vcalendar);
    xml.closeTag(XcalTags.icalendar);
  }

  private BaseComponentType matches(final VcalendarType vc,
                                    final int entityType) throws CalFacadeException {
    if ((vc == null) || (vc.getComponents() == null)) {
      return null;
    }

    final String nm;

    if (entityType == IcalDefs.entityTypeEvent) {
      nm = VeventType.class.getName();
    } else if (entityType == IcalDefs.entityTypeTodo) {
      nm = VtodoType.class.getName();
    } else if (entityType == IcalDefs.entityTypeJournal) {
      nm = VjournalType.class.getName();
    } else if (entityType == IcalDefs.entityTypeFreeAndBusy) {
      nm = VfreebusyType.class.getName();
    } else {
      throw new CalFacadeException("org.bedework.invalid.entity.type",
                                   String.valueOf(entityType));
    }

    for (final JAXBElement<? extends BaseComponentType> jbc:
            vc.getComponents().getBaseComponent()) {
      final BaseComponentType bc = jbc.getValue();

      if (nm.equals(bc.getClass().getName())) {
        return bc;
      }
    }

    return null;
  }

  private void xmlComponent(final XmlEmit xml,
                            final Component val) throws CalFacadeException {
    final QName tag = openTag(xml, val.getName());

    final PropertyList pl = val.getProperties();

    if (pl.size() > 0) {
      xml.openTag(XcalTags.properties);

      for (final Object po: pl) {
        xmlProperty(xml, (Property)po);
      }
      xml.closeTag(XcalTags.properties);
    }

    ComponentList<?> cl = null;

    if (val instanceof VTimeZone) {
      cl = ((VTimeZone)val).getObservances();
    } else if (val instanceof VEvent) {
      cl = ((VEvent)val).getAlarms();
    } else if (val instanceof VToDo) {
      cl = ((VToDo)val).getAlarms();
    }

    if ((cl != null) && (cl.size() > 0)){
      xml.openTag(XcalTags.components);

      for (final Object o: cl) {
        xmlComponent(xml, (Component)o);
      }

      xml.closeTag(XcalTags.components);
    }

    xml.closeTag(tag);
  }

  private void xmlProperty(final XmlEmit xml,
                           final Property val) {
      final QName tag = openTag(xml, val.getName());

      final ParameterList pl = val.getParameters();

      if (pl.size() > 0) {
        xml.openTag(XcalTags.parameters);

        final Iterator<Parameter> pli = pl.iterator();
        while (pli.hasNext()) {
          xmlParameter(xml, pli.next());
        }
        xml.closeTag(XcalTags.parameters);
      }

      final PropertyIndex.PropertyInfoIndex pii =
              PropertyIndex.PropertyInfoIndex.fromName(val.getName());

      QName ptype = XcalTags.textVal;

      if (pii != null) {
        final PropertyIndex.DataType dtype = pii.getPtype();
        if (dtype != null) {
          ptype = dtype.getXcalType();
        }
      }

      if (ptype == null) {
        // Special processing I haven't done
        warn("Unimplemented value type for " + val.getName());
        ptype = XcalTags.textVal;
      }

      if (ptype.equals(XcalTags.recurVal)) {
        // Emit individual parts of recur rule

        xml.openTag(ptype);

        final Recur r;

        if (val instanceof ExRule) {
          r = ((ExRule)val).getRecur();
        } else {
          r = ((RRule)val).getRecur();
        }

        xml.property(XcalTags.freq, r.getFrequency());
        xmlProp(xml, XcalTags.wkst, r.getWeekStartDay().name());
        if (r.getUntil() != null) {
          xmlProp(xml, XcalTags.until, r.getUntil().toString());
        }
        xmlProp(xml, XcalTags.count, String.valueOf(r.getCount()));
        xmlProp(xml, XcalTags.interval, String.valueOf(r.getInterval()));
        xmlProp(xml, XcalTags.bymonth, r.getMonthList());
        xmlProp(xml, XcalTags.byweekno, r.getWeekNoList());
        xmlProp(xml, XcalTags.byyearday, r.getYearDayList());
        xmlProp(xml, XcalTags.bymonthday, r.getMonthDayList());
        xmlProp(xml, XcalTags.byday, r.getDayList());
        xmlProp(xml, XcalTags.byhour, r.getHourList());
        xmlProp(xml, XcalTags.byminute, r.getMinuteList());
        xmlProp(xml, XcalTags.bysecond, r.getSecondList());
        xmlProp(xml, XcalTags.bysetpos, r.getSetPosList());

        xml.closeTag(ptype);
      } else {
        xml.property(ptype, val.getValue());
      }

      xml.closeTag(tag);
  }

  private void xmlProp(final XmlEmit xml,
                       final QName tag,
                       final String val) {
    if (val == null) {
      return;
    }

    xml.property(tag, val);
  }

  private void xmlProp(final XmlEmit xml,
                       final QName tag,
                       final Collection<?> val) {
    if ((val == null) || val.isEmpty()) {
      return;
    }

    xml.property(tag, val.toString());
  }

  private void xmlProp(final XmlEmit xml,
                       final String pname,
                       final QName ptype,
                       final String val) {
    final QName tag = new QName(XcalTags.namespace, pname.toLowerCase());

    xml.openTag(tag);
    xml.property(ptype, val);
    xml.closeTag(tag);
  }

  private void xmlParameter(final XmlEmit xml,
                            final Parameter val) {
    final PropertyIndex.ParameterInfoIndex pii =
            PropertyIndex.ParameterInfoIndex.lookupPname(val.getName());

    QName ptype = XcalTags.textVal;

    if (pii != null) {
      final PropertyIndex.DataType dtype = pii.getPtype();
      if (dtype != null) {
        ptype = dtype.getXcalType();
      }
    }

    if (ptype.equals(XcalTags.textVal)) {
      final QName tag = new QName(XcalTags.namespace,
                                  val.getName().toLowerCase());
      xml.property(tag, val.getValue());
    } else {
      final QName tag = openTag(xml, val.getName());
      xml.property(ptype, val.getValue());
      xml.closeTag(tag);
    }
  }

  private QName openTag(final XmlEmit xml,
                        final String name) {
    final QName tag = new QName(XcalTags.namespace, name.toLowerCase());

    xml.openTag(tag);

    return tag;
  }
}
