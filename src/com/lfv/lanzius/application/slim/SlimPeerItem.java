package com.lfv.lanzius.application.slim;

import com.lfv.lanzius.DomTools;
import com.lfv.lanzius.application.Controller;
import org.jdom.Element;

/**
 * <p>
 * SlimPeerItem
 * <p>
 * Copyright &copy; LFV 2007, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2 (Lanzius)
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
public class SlimPeerItem {

    private Element model;

    private int id;
    private String name;

    private Object lock = Controller.getInstance();

    public SlimPeerItem(Element model) {
        this.model = model;
        synchronized (lock) {
            id = DomTools.getAttributeInt(model,"id", 0, true);
            name = DomTools.getChildText(model, "Name", "R/"+id, false);
        }
    }

    public int getId() {
        return id;
    }

    public boolean isActive() {
        synchronized (lock) {
            return DomTools.getAttributeBoolean(model, "active", true, false);
        }
    }

    @Override
    public String toString() {
        String state;
        synchronized (lock) {
            if(!DomTools.getAttributeBoolean(model, "active", true, false))
                state = "[OFF] ";
            else if(DomTools.getAttributeInt(model,"line", 1, false)>1)
                state = "[BUSY] ";
            else
                state = "[ON] ";
        }
        return state + name;
    }
}
