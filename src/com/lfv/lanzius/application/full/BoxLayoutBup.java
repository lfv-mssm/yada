package com.lfv.lanzius.application.full;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.BoxLayout;

/**
 * <p>
 * BoxLayoutBup
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
public class BoxLayoutBup extends BoxLayout {

    private Rectangle rect;

    public BoxLayoutBup(Container target) {
        super(target, Y_AXIS);
        rect = new Rectangle();
    }

    public void layoutContainer(Container target) {
        super.layoutContainer(target);
        int iNbrChildren = target.getComponentCount();

        Dimension alloc = target.getSize();
        Insets in = target.getInsets();
        alloc.height -= in.top + in.bottom;

        for (int i=0;i<iNbrChildren;i++) {
            Component c = target.getComponent(i);
            c.getBounds(rect);
            rect.y = alloc.height-rect.height-rect.y;
            c.setBounds(rect);
        }
    }
}
