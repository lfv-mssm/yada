/** 
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
package com.lfv.lanzius;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.JPanel;

public class VUMeterGrad extends JPanel {

    private final int STEPS = 31;

    private Rectangle rectMain;

    public VUMeterGrad() {
        rectMain = new Rectangle();
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Store attributes
        Color storedColor = g.getColor();

        // Calculate active area without borders
        Insets paintViewInsets = getInsets();
        rectMain.width = getWidth() - (paintViewInsets.left + paintViewInsets.right);
        rectMain.height = getHeight() - (paintViewInsets.top + paintViewInsets.bottom);
        rectMain.x = paintViewInsets.left;
        rectMain.y = paintViewInsets.top;

        g.setColor(Color.black);
        int d = rectMain.height/4;
        for(int i=0;i<STEPS;i++) {

            int x = (int)((float)(i*rectMain.width)/(float)(STEPS-1));
            if(x<0)x=0;
            if(x>=rectMain.width) x = rectMain.width-1;
            if((i&1)==0)
                g.drawLine(x,rectMain.y,x,rectMain.y+rectMain.height-1);
            else
                g.drawLine(x,rectMain.y+d,x,rectMain.y+rectMain.height-d-1);

        }


        // Restore attributes
        g.setColor(storedColor);
    }
}
