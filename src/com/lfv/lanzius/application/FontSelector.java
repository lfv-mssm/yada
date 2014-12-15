package com.lfv.lanzius.application;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;

/**
 * <p>
 * FontSelector
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
public class FontSelector {

    private static Font[] fontArray = {
        new Font("Dialog", Font.BOLD,  24),
        new Font("Dialog", Font.BOLD,  22),
        new Font("Dialog", Font.BOLD,  20),
        new Font("Dialog", Font.BOLD,  18),
        new Font("Dialog", Font.BOLD,  17),
        new Font("Dialog", Font.BOLD,  14),
        new Font("Dialog", Font.BOLD,  12),
        new Font("Dialog", Font.BOLD,  10)
    };

    public static Font getFont(String text, Graphics graphics, Rectangle outerBounds, Rectangle textBounds) {

        // Smallest is default, if no one fits inside outer bounds
        Font selectedFont = fontArray[fontArray.length-1];

        // Store outerBounds position
        int x = outerBounds.x;
        int y = outerBounds.y;

        // Translate to origo
        outerBounds.x = outerBounds.y = 0;
        textBounds.x  = textBounds.y  = 0;

        // One pixel border
        outerBounds.width  -= 2;
        outerBounds.height -= 2;

        // Find best fit
        for(Font f : fontArray) {

            // Get text bounds
            Rectangle r = graphics.getFontMetrics(f).getStringBounds(text,graphics).getBounds();
            textBounds.width  = r.width;
            textBounds.height = r.height;

            // Does it fit?
            if(outerBounds.contains(textBounds)) {
                selectedFont = f;
                break;
            }
        }

        // Position and size of text
        if(textBounds!=null) {
           textBounds.x = x + Math.max(0,(outerBounds.width-textBounds.width)/2) + 1;
           textBounds.y = y + outerBounds.height/2 + textBounds.height/4 + 1;
        }

        // Restore outerBounds
        outerBounds.x = x;
        outerBounds.y = y;
        outerBounds.width  += 2;
        outerBounds.height += 2;

        // Return font
        return selectedFont;
    }
}
