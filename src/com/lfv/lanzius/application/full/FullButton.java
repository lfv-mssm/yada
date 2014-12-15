package com.lfv.lanzius.application.full;

import com.lfv.lanzius.DomTools;
import com.lfv.lanzius.application.*;
import com.lfv.lanzius.application.FontSelector;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import org.jdom.Element;

/**
 * <p>
 * FullButton
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
public class FullButton extends JPanel implements MouseListener {

    private static Timer timer = new Timer("DSfullbutton", true);

    private Element model;
    private ButtonHandler buttonHandler;
    private String actionCommand;

    private int lineWidth = 1;
    private boolean textVisible;
    private TimerTask blinkTask;

    private Rectangle rectMain;
    private Rectangle rectText;

    private Color activeColor;
    private Color inactiveColor;

    private Object lock = Controller.getInstance();

    public FullButton(Element model, ButtonHandler buttonHandler, String actionCommand, Color activeColor, Color inactiveColor) {
        super();

        if(model!=null) {
            this.model = model;
            this.buttonHandler = buttonHandler;
            this.actionCommand = actionCommand;
            this.activeColor = activeColor;
            this.inactiveColor = inactiveColor;

            rectMain = new Rectangle();
            rectText = new Rectangle();

            setBorder(new LineBorder(Color.black, lineWidth));
            setOpaque(true);
            addMouseListener(this);

            textVisible = true;
        }
    }

    private synchronized void toggleBlink() {
        textVisible = !textVisible;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {

        if(model!=null) {
            // Store attributes
            Color storedColor = g.getColor();
            Font  storedFont  = g.getFont();

            boolean isActive = true;
            boolean isBlinking = false;
            double  hfac = 1.0;
            synchronized(lock) {
                // Get visibility values from model
                isActive   = DomTools.getAttributeBoolean(model, "active", true, false);
                isBlinking = DomTools.getAttributeBoolean(model, "blink", false, false)&&isActive;
                try {
                    hfac = Double.valueOf(DomTools.getAttributeString(model, "hfac", "1.0", false));
                } catch(NumberFormatException ex) {}

                // Update line thickness
                int w = DomTools.getAttributeInt(model,"line", 1, false);
                if(!isActive) w = 1;
                if(w!=lineWidth) {
                    lineWidth = w;
                    setBorder(new LineBorder(Color.black, lineWidth));
                }
            }


            // Create a new blink task
            if(isBlinking&&blinkTask==null) {
                textVisible = true;
                blinkTask = new TimerTask() {
                    public void run() {
                        toggleBlink();
                    }
                };
                timer.scheduleAtFixedRate(blinkTask,350,350);
            }

            // Stop blinking task
            if(!isBlinking&&blinkTask!=null) {
                blinkTask.cancel();
                blinkTask = null;
            }

            // Draw background
            g.setColor(isActive?activeColor:inactiveColor);

            g.fillRect(0,0,getWidth(),getHeight());

            // Calculate active area without borders
            Insets paintViewInsets = getInsets();
            rectMain.width = getWidth() - (paintViewInsets.left + paintViewInsets.right);
            rectMain.height = getHeight() - (paintViewInsets.top + paintViewInsets.bottom);
            rectMain.x = paintViewInsets.left;
            rectMain.y = (int)Math.round(paintViewInsets.top + (1-hfac)*rectMain.height/2);
            rectMain.height = (int)Math.round(rectMain.height*hfac);

            // Get the id and the name from the model
            String name = "";
            synchronized(lock) {
                int id = DomTools.getAttributeInt(model, "id", 0, false);
                name = DomTools.getChildText(model, "Name", String.valueOf(id), false);
            }

            synchronized(this) {
                if(blinkTask==null)
                    textVisible = true;

                if(textVisible) {
                    // Draw the string for the status box
                    if(isActive) {
                        g.setColor(Color.black);
                    }
                    else {
                        Color c = inactiveColor;
                        g.setColor(new Color(c.getRed()/2,c.getGreen()/2,c.getBlue()/2));
                    }

                    g.setFont(FontSelector.getFont(name,g,rectMain,rectText));
                    g.drawString(name, rectText.x, rectText.y);
                }
            }

            // Restore attributes
            g.setColor(storedColor);
            g.setFont(storedFont);
        }
    }

    public void mousePressed(MouseEvent e) {
        // Left mouse button only
        if(e.getButton()!=MouseEvent.BUTTON1) return;

        if(buttonHandler!=null) {
            // Do not call handler if button is not active
            synchronized(lock) {
                if(!DomTools.getAttributeBoolean(model, "active", true, false))
                    return;
            }
            buttonHandler.buttonClicked(actionCommand, DomTools.getAttributeInt(model, "id", 0, false));
        }
    }

    public void mouseReleased(MouseEvent e) {    }
    public void mouseClicked(MouseEvent e) {    }
    public void mouseEntered(MouseEvent e) {    }
    public void mouseExited(MouseEvent e) {    }
}
