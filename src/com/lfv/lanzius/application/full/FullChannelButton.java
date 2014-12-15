package com.lfv.lanzius.application.full;

import com.lfv.lanzius.Constants;
import com.lfv.lanzius.DomTools;
import com.lfv.lanzius.application.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import org.jdom.Element;

/**
 * <p>
 * FullIndicatorButton
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
public class FullChannelButton extends JPanel implements MouseListener {

    private int channelId;

    private Element model;
    private ViewEventHandler eventHandler;
    private TerminalProperties properties;

    private boolean channelButtonPressed;

    private boolean rx;
    private boolean tx;
    private boolean recording;
    private boolean monitoring;

    private boolean locked;
    private boolean hidden;
    private boolean recordable;
    private boolean monitor;

    private Dimension minSize;
    private Dimension maxSize;

    private Rectangle rectMain;
    private Rectangle rectIndi;
    private Rectangle rectTop;
    private Rectangle rectText;
    private Rectangle rectRx;
    private Rectangle rectTx;
    private Rectangle rectRec;

    private Object lock = Controller.getInstance();

    public FullChannelButton(Element model, ViewEventHandler eventHandler, TerminalProperties properties) {
        super();

        this.model = model;
        this.eventHandler = eventHandler;
        this.properties = properties;

        synchronized(lock) {
            channelId  = DomTools.getAttributeInt(model,"id", 0, true);
            locked     = DomTools.getAttributeBoolean(model, "locked", false, false);
            hidden     = DomTools.getAttributeBoolean(model, "hidden", false, false);
            recordable = DomTools.getAttributeBoolean(model, "recordable", false, false);
            monitor    = DomTools.getAttributeBoolean(model, "monitor", false, false);
        }

        if(hidden) {
            maxSize = new Dimension(0,0);
            minSize = new Dimension(0,0);
        }
        else {
            maxSize = new Dimension(Integer.MAX_VALUE, Constants.FULL_RADIO_MAXHEIGHT);
            minSize = new Dimension(0, Constants.FULL_RADIO_MINHEIGHT);

            rectMain = new Rectangle();
            rectIndi = new Rectangle();
            rectTop  = new Rectangle();
            rectText = new Rectangle();
            rectRx   = new Rectangle();
            rectTx   = new Rectangle();
            rectRec  = new Rectangle();

            setBorder(new LineBorder(Color.black));
            setOpaque(true);

            addMouseListener(this);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if(hidden) return;

        // Get state
        rx=tx=false;
        recording = false;
        synchronized(lock) {
            String attr = model.getAttributeValue("state");
            if(attr!=null) {
                if(attr.equals("rxtx")) {
                    rx = true;
                    tx = !monitor;
                }
                else if(attr.equals("rx")) {
                    rx = true;
                }
            }

            monitoring = DomTools.getAttributeBoolean(model, "monitoring", false, false);
            recording  = DomTools.getAttributeBoolean(model, "recording",  false, false);
        }

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

        // Calculate pos and size for rx and tx buttons
        int w = (int)(rectMain.width  * 0.7);
        int h =       rectMain.height;

        if(monitor) {
            rectTx.setBounds(0,0,0,0);
        }
        else {
            h /= 2;
            rectTx.setBounds(rectMain);
            rectTx.x += w;
            rectTx.width -= w;
            rectTx.y += h;
            rectTx.height -= h;
        }

        rectRx.setBounds(rectMain);
        rectRx.x += w;
        rectRx.width -= w;
        rectRx.height = h;
        rectMain.width = w;

        // Fill rx tx areas
        g.setColor(properties.getColorRadioRxTx());
        if(rx) g.fillRect(rectRx.x, rectRx.y, rectRx.width, rectRx.height);
        if(tx) g.fillRect(rectTx.x, rectTx.y, rectTx.width, rectTx.height);

        // Fill main areas
        if(tx) {
            g.setColor(properties.getColorRadioRxTx());
            g.fillRect(rectMain.x, rectMain.y, rectMain.width, rectMain.height);
        }
        else if(rx) {
            g.setColor(properties.getColorRadioRx());
            g.fillRect(rectMain.x, rectMain.y, rectMain.width, rectMain.height);
        }

        // Draw lines around buttons
        g.setColor(Color.black);
        g.drawLine(rectRx.x, rectMain.y, rectRx.x, rectMain.y+rectMain.height);
        if(!monitor)
            g.drawLine(rectRx.x, rectTx.y, rectRx.x+rectRx.width, rectTx.y);

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
        synchronized(lock) {
            if(DomTools.getAttributeBoolean(model, "fail", false, false)) {
                g.setColor(properties.getColorRadioIndicatorFail());
            }
            else {
                boolean send  = DomTools.getAttributeBoolean(model, "send",  false, false);
                boolean recvp = DomTools.getAttributeBoolean(model, "recvp", false, false);
                boolean recvs = DomTools.getAttributeBoolean(model, "recvs", false, false);
                if((rx&&(recvp||recvs)) || (tx&&send)) {
                    g.setColor(properties.getColorRadioIndicatorBusy());
                }
                else {
                   g.setColor(properties.getColorRadioIndicatorIdle());
                }
            }

            // Draw box
            g.fillRect(rectMain.x+rectIndi.x, rectMain.y+rectIndi.y, rectIndi.width, rectIndi.height);
            g.setColor(Color.black);
            g.drawRect(rectMain.x+rectIndi.x, rectMain.y+rectIndi.y, rectIndi.width, rectIndi.height);

            // Draw the string for the box
            String text = model.getChildTextTrim("Name");
            g.setFont(FontSelector.getFont(text, g, rectTop, rectText));
            g.drawString(text, rectText.x, rectText.y);
        }

        // Draw record field
        if(recordable) {
            rectRec.x      = rectMain.x+rectIndi.x;
            rectRec.y      = rectMain.y+rectIndi.y+rectIndi.height+2;
            rectRec.width  = rectIndi.width;
            rectRec.height = rectMain.y+rectMain.height-rectIndi.y-rectIndi.height-1-4;
            if(recording) {
                if(!monitor || monitoring) {
                    g.setColor(Color.red);
                }
                else {
                    g.setColor(Color.orange);
                }
            }
            else {
                g.setColor(Color.white);
            }
            g.fillRect(rectRec.x, rectRec.y, rectRec.width, rectRec.height);
            g.setColor(Color.black);
            g.drawRect(rectRec.x, rectRec.y, rectRec.width, rectRec.height);

            // Draw the rec string
            String text = "REC";
            rectRec.grow(-2, -1);
            g.setFont(FontSelector.getFont(text, g, rectRec, rectText));
            g.drawString(text, rectText.x, rectText.y);
        }

        // Draw the strings for rx and tx
        g.setColor(Color.black);
        String text = "RX";
        g.setFont(FontSelector.getFont(text, g, rectRx, rectText));
        g.drawString(text, rectText.x, rectText.y);

        if(!monitor) {
            text = "TX";
            g.setFont(FontSelector.getFont(text, g, rectTx, rectText));
            g.drawString(text, rectText.x, rectText.y);
        }

        // Restore attributes
        g.setColor(storedColor);
        g.setFont(storedFont);
    }

    @Override
    public Dimension getMaximumSize() {
        return maxSize;
    }

    @Override
    public Dimension getMinimumSize() {
        return minSize;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void mousePressed(MouseEvent e) {
        // Left mouse button only
        if(e.getButton()!=MouseEvent.BUTTON1) return;

        Point p = e.getPoint();
        if(rectRx.contains(p)) {
            if(!locked)
                eventHandler.rxtxStateUpdated(channelId, !rx, false);
        }
        else if(rectTx.contains(p)) {
            if(!locked && !monitor)
                eventHandler.rxtxStateUpdated(channelId, true, !tx);
        }
        else if(rectMain.contains(p)) {

            if(recordable) {
                if(rectRec.contains(p)) {
                    eventHandler.recordStateUpdated(channelId, !recording);
                    return;
                }
            }

            eventHandler.channelButtonPressed(channelId);
            channelButtonPressed = true;
        }
    }

    public void mouseReleased(MouseEvent e) {
        // Left mouse button only
        if(e.getButton()!=MouseEvent.BUTTON1) return;

        if(channelButtonPressed) {
            eventHandler.channelButtonReleased(channelId);
            channelButtonPressed = false;
        }
    }

    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
}
