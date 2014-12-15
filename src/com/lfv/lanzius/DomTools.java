package com.lfv.lanzius;

import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Document;
import org.jdom.Element;

/**
 * <p>
 * DomTools
 * <p>
 * Copyright &copy; LFV 2007, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2.0 (Lanzius)
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
public class DomTools {

    private static Log log = LogFactory.getLog(DomTools.class );

    public static Element getElementFromSection(Document doc, String section, String key, String value) {
        if(section==null||key==null||value==null)
            return null;

        Element e = doc.getRootElement();
        if(e!=null) {
            e = e.getChild(section);
            if(e!=null) {
                Iterator iter = e.getChildren().iterator();
                while(iter.hasNext()) {
                    e = (Element)iter.next();
                    String s = e.getAttributeValue(key);
                    if((s!=null)&&(s.equals(value)))
                        return e;
                }
            }
        }

        return null;
    }

    public static Element getElementFromSection(Document doc, String section, int index) {
        if(section==null)
            return null;

        Element e = doc.getRootElement();
        if(e!=null) {
            e = e.getChild(section);
            if(e!=null) {
                List list = e.getChildren();
                try {
                    return (Element)list.get(index);
                } catch(IndexOutOfBoundsException ex) {
                    return null;
                }
            }
        }

        return null;
    }

    public static int getNbrElementsInSection(Document doc, String section) {
        if(section==null)
            return 0;

        Element e = doc.getRootElement();
        if(e!=null) {
            e = e.getChild(section);
            if(e!=null) {
                return e.getChildren().size();
            }
        }

        return 0;
    }

    public static Element getChildById(Element element, int id) {
        String ids = String.valueOf(id);
        Iterator iter = element.getChildren().iterator();
        while(iter.hasNext()) {
            Element er = (Element)iter.next();
            String value = er.getAttributeValue("id");
            if(value!=null&&value.equals(ids))
                return er;
        }

        return null;
    }

    public static int getChildIndexById(Element element, int id) {
        int index = 0;
        String ids = String.valueOf(id);
        Iterator iter = element.getChildren().iterator();
        while(iter.hasNext()) {
            Element er = (Element)iter.next();
            String value = er.getAttributeValue("id");
            if(value!=null&&value.equals(ids))
                return index;
            index++;
        }

        return -1;
    }

    public static String getAttributeString(Element element, String attributeName, String defaultValue, boolean warn) {

        if(element==null || attributeName==null) {
            if(warn) log.warn("getAttribute - Parameter is null, defaulting!", new Exception());
            return defaultValue;
        }

        String value = element.getAttributeValue(attributeName);

        if(value==null) {
            if(warn)
                log.warn("getAttributeString - Value does not exist, defaulting!", new Exception());

            return defaultValue;
        }

        return value;
    }

    public static int getAttributeInt(Element element, String attributeName, int defaultValue, boolean warn) {

        String value = getAttributeString(element, attributeName, String.valueOf(defaultValue), warn);
        try {
            return Integer.parseInt(value);
        } catch(NumberFormatException ex) {
            if(warn) log.warn("getAttributeInt - Number format is invalid, defaulting!", new Exception());
        }
        return defaultValue;
    }

    public static boolean getAttributeBoolean(Element element, String attributeName, boolean defaultValue, boolean warn) {
        return getAttributeString(element, attributeName, String.valueOf(defaultValue), warn).equals("true");
    }

    public static String getChildText(Element element, String childName, String defaultValue, boolean warn) {

        if(element==null || childName==null) {
            if(warn) log.warn("getChildText - Parameter is null, defaulting!", new Exception());
            return defaultValue;
        }

        String value = element.getChildTextTrim(childName);

        if(value==null) {
            if(warn) log.warn("getChildText - Child element does not exist, defaulting!", new Exception());
            return defaultValue;
        }

        return value;
    }

    public static String getPrioritizedAttribute(String name, String defaultValue, Element elemHigh, Element elemLow) {

        // Try to get the attribute value from the element with highest priority
        String value = elemHigh.getAttributeValue(name);
        if(value!=null) return value;

        // Second try, get it from the element with lowest priority
        value = elemLow.getAttributeValue(name);
        if(value!=null) return value;

        // Return the default value
        return defaultValue;
    }

    public static int getPrioritizedAttribute(String name, int defaultValue, Element elemHigh, Element elemLow) {

        // Try to get the attribute value from the element with highest priority
        try {
            Attribute attr = elemHigh.getAttribute(name);
            if(attr!=null) return attr.getIntValue();
        } catch(DataConversionException ex) {
            log.warn("getPrioritizedAttribute - Invalid format, expected int value from high priority element!", ex);
        }

        // Second try, get it from the element with lowest priority
        try {
            Attribute attr = elemLow.getAttribute(name);
            if(attr!=null) return attr.getIntValue();
        } catch(DataConversionException ex) {
            log.warn("getPrioritizedAttribute - Invalid format, expected int value from low priority element!", ex);
        }

        // Return the default value
        return defaultValue;
    }
}
