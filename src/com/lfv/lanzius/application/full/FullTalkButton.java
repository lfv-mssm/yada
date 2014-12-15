package com.lfv.lanzius.application.full;

import com.lfv.lanzius.Constants;
import com.lfv.lanzius.application.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import org.jdom.Element;

/**
 * <p>
 * FullTalkButton
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
public class FullTalkButton extends JPanel implements MouseListener {

    private Element model;
    private ViewEventHandler eventHandler;
    private TerminalProperties properties;

    private Rectangle rectMain;
    private Rectangle rectIndi;
    private Rectangle rectTop;
    private Rectangle rectText;

    private boolean talkButtonPressed;

    private Object lock = Controller.getInstance();

    public FullTalkButton(Element model, ViewEventHandler eventHandler, TerminalProperties properties) {
        super();

        this.model = model;
        this.eventHandler = eventHandler;
        this.properties = properties;

        rectMain = new Rectangle();
        rectIndi = new Rectangle();
        rectTop  = new Rectangle();
        rectText = new Rectangle();

        setBorder(new LineBorder(Color.black));
        setOpaque(true);

        addMouseListener(this);
    }

    @Override
    protected void paintComponent(Graphics g) {

        // Store attributes
        Color storedColor = g.getColor();
        Font  storedFont  = g.getFont();

        // Draw background with state color
        g.setColor(properties.getColorRadioOff());
        g.fillRect(0,0,getWidth(),getHeight());

        // Calculate active area without borders
        Insets paintViewInsets = getInsets();
        rectMain.x = paintViewInsets.left;
        rectMain.y = paintViewInsets.top;
        rectMain.width = getWidth() - (paintViewInsets.left + paintViewInsets.right);
        rectMain.height = getHeight() - (paintViewInsets.top + paintViewInsets.bottom);

        g.setColor(properties.getColorRadioRxTx());
        g.fillRect(rectMain.x, rectMain.y, rectMain.width, rectMain.height);

        // Calculate size of busy indicator
        rectIndi.width  = (int)(rectMain.width  * 0.6);
        rectIndi.height = (int)(rectMain.height * 0.2);
        rectIndi.width  = Math.max(rectIndi.width,  40);
        rectIndi.height = Math.max(rectIndi.height, 8);
        rectIndi.width  = Math.min(rectIndi.width, 220);
        rectIndi.height = Math.min(rectIndi.height, 30);
        rectIndi.x = (rectMain.width-rectIndi.width)/2;
        rectIndi.y = (rectMain.height-rectIndi.height)/2;

        // Bounds for the text
        rectTop.setBounds(rectMain);
        rectTop.height = rectIndi.y;

        // Draw indicator
        synchronized (lock) {
            String attr = model.getAttributeValue("state");
            g.setColor(properties.getColorRadioIndicatorIdle());
            if(attr!=null) {
                if(attr.equals("send")) {
                    g.setColor(properties.getColorRadioIndicatorBusy());
                }
                else if(attr.equals("fail")) {
                    g.setColor(properties.getColorRadioIndicatorFail());
                }
            }
        }

        g.fillRect(rectMain.x+rectIndi.x, rectMain.y+rectIndi.y, rectIndi.width, rectIndi.height);
        g.setColor(Color.black);
        g.drawRect(rectMain.x+rectIndi.x, rectMain.y+rectIndi.y, rectIndi.width, rectIndi.height);

        // Draw the string
        String text = "TALK";
        g.setFont(FontSelector.getFont(text, g, rectTop, rectText));
        g.drawString(text, rectText.x, rectText.y);

        // Restore attributes
        g.setColor(storedColor);
        g.setFont(storedFont);
    }

    public void mousePressed(MouseEvent e) {
        // Left mouse button only
        if(e.getButton()!=MouseEvent.BUTTON1) return;

        eventHandler.talkButtonPressed(Constants.DEVICE_MOUSE);
        talkButtonPressed = true;
    }
    public void mouseReleased(MouseEvent e) {
        // Left mouse button only
        if(e.getButton()!=MouseEvent.BUTTON1) return;

        if(talkButtonPressed) {
            eventHandler.talkButtonReleased(Constants.DEVICE_MOUSE);
            talkButtonPressed = false;
        }
    }

    public void mouseClicked(MouseEvent e) {    }
    public void mouseEntered(MouseEvent e) {    }
    public void mouseExited(MouseEvent e) {    }
}