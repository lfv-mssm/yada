package com.lfv.lanzius.application.slim;

import com.lfv.lanzius.DomTools;
import com.lfv.lanzius.application.Controller;
import javax.swing.JPanel;
import org.jdom.Element;

/**
 * <p>
 * SlimRadioItem
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
public class SlimRadioItem extends JPanel {

    private int id;

    private Element model;

    private boolean locked;
    private boolean hidden;

    private Object lock = Controller.getInstance();

    public SlimRadioItem(Element model) {
        super();

        this.model = model;

        synchronized(lock) {
            id     = DomTools.getAttributeInt(model,"id", 0, true);
            locked = DomTools.getAttributeBoolean(model, "locked", false, false);
            hidden = DomTools.getAttributeBoolean(model, "hidden", false, false);
        }
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isHidden() {
        return hidden;
    }

    public int getId() {
        return id;
    }

    public String getState() {
        synchronized(lock) {
            String state = DomTools.getAttributeString(model, "state", "off", false).toLowerCase();
            return state;
        }
    }

    @Override
    public String toString() {
        synchronized(lock) {
            String state = DomTools.getAttributeString(model, "state", "off", false).toUpperCase();
            String name = DomTools.getChildText(model, "Name", "C/"+id, false);
            return "["+state+"] "+name;
        }
    }
}
