package com.lfv.lanzius.server;

import com.lfv.lanzius.DomTools;
import org.jdom.Element;

/**
 * <p>
 * DocumentModelItem
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
public class DocumentModelItem {

    private Element element;
    private int id;
    private String name;

    public DocumentModelItem(Element element, String prefix, String defaultName) {
        this.element = element;
        this.id      = DomTools.getAttributeInt(element, "id", 0, true);
        this.name    = prefix+" "+id+" - " +DomTools.getChildText(element, "Name", defaultName+id, false);
    }

    public Element getElement() {
        return element;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return name;
    }
}
