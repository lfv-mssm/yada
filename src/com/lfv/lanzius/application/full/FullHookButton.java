package com.lfv.lanzius.application.full;

import com.lfv.lanzius.DomTools;
import com.lfv.lanzius.application.Controller;
import com.lfv.lanzius.application.FontSelector;
import com.lfv.lanzius.application.TerminalProperties;
import com.lfv.lanzius.application.ViewEventHandler;
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
 * FullHookButton
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
public class FullHookButton extends JPanel implements MouseListener {

    private static Timer timer = new Timer("DSfullhook", true);

    private final int STATE_IDLE    = 0;
    private final int STATE_RINGING = 1;
    private final int STATE_DIALING = 2;
    private final int STATE_BUSY    = 3;
    private final int STATE_IN_CALL = 4;

    private final int HOLD_TIME_FOR_CLOSE = 3000;

    private Element model;
    private ViewEventHandler eventHandler;
    private TerminalProperties properties;

    private long pressedTime;

    private boolean blinkFilled;
    private TimerTask blinkTask;

    private Rectangle rectMain;
    private Rectangle rectSrc;
    private Rectangle rectDest;
    private Rectangle rectActn;
    private Rectangle rectText;

    private Object lock = Controller.getInstance();

    public FullHookButton(Element model, ViewEventHandler eventHandler, TerminalProperties properties) {
        super();

        this.model = model;
        this.eventHandler = eventHandler;
        this.properties = properties;

        rectMain = new Rectangle();
        rectSrc  = new Rectangle();
        rectDest = new Rectangle();
        rectActn = new Rectangle();
        rectText = new Rectangle();

        setBorder(new LineBorder(Color.black));
        setOpaque(true);
        addMouseListener(this);
    }

    private synchronized void toggleBlink() {
        blinkFilled = !blinkFilled;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Store attributes
        Color storedColor = g.getColor();
        Font  storedFont  = g.getFont();

        int state = STATE_IDLE;

        String textSource = null;
        String textDest   = null;

        synchronized(lock) {
            // Get texts from model
            textSource = DomTools.getAttributeString(model, "src",  null, false);
            textDest   = DomTools.getAttributeString(model, "dest", null, false);

            // Get state from model
            String s = DomTools.getAttributeString(model, "state", "idle", false);

            if(s.equals("dialing"))
                state = STATE_DIALING;
            else if(s.equals("busy"))
                state = STATE_BUSY;
            else if(s.equals("in_call"))
                state = STATE_IN_CALL;
            else if(s.equals("ringing"))
                state = STATE_RINGING;
        }

        // Create a new blink task
        if(state==STATE_RINGING&&blinkTask==null) {
            blinkFilled = true;
            blinkTask = new TimerTask() {
                public void run() {
                    toggleBlink();
                }
            };
            timer.scheduleAtFixedRate(blinkTask,350,350);
        }

        // Stop blinking task
        if(state!=STATE_RINGING&&blinkTask!=null) {
            blinkTask.cancel();
            blinkTask = null;
        }

        // Draw background
        if(state>=STATE_DIALING||(state==STATE_RINGING&&blinkFilled))
            g.setColor(properties.getColorPhoneHookButtonCalling());
        else
            g.setColor(properties.getColorGenericButton());
        g.fillRect(0,0,getWidth(),getHeight());

        // Calculate active area without borders
        Insets paintViewInsets = getInsets();
        rectMain.x = paintViewInsets.left;
        rectMain.y = paintViewInsets.top;
        rectMain.width = getWidth() - (paintViewInsets.left + paintViewInsets.right);
        rectMain.height = getHeight() - (paintViewInsets.top + paintViewInsets.bottom);

        // Calculate source text area
        rectSrc.x      = rectMain.x-1;
        rectSrc.y      = rectMain.y-1;
        rectSrc.width  = (int)Math.round(rectMain.width  * 0.4);
        rectSrc.height = (int)Math.round(rectMain.height * 0.3);

        // Calculate dest text area
        rectDest.width  = rectSrc.width;
        rectDest.height = rectSrc.height;
        rectDest.x      = rectMain.width - rectDest.width + 1;
        rectDest.y      = rectMain.height - rectDest.height + 1;

        // Calculate action text area
        rectActn.x      = rectMain.x;
        rectActn.y      = rectSrc.y + rectSrc.height;
        rectActn.width  = rectMain.width;
        rectActn.height = (int)Math.round(rectMain.height * 0.4);

        // Fill the source and dest areas
        if((state==STATE_RINGING&&blinkFilled)||state==STATE_DIALING)
            g.setColor(properties.getColorGenericButton());
        else if(state==STATE_BUSY)
            g.setColor(properties.getColorPhoneHookButtonBusy());
        if(state!=STATE_IDLE&&state!=STATE_IN_CALL) {
            g.fillRect(rectSrc.x, rectSrc.y, rectSrc.width, rectSrc.height);
            g.fillRect(rectDest.x, rectDest.y, rectDest.width, rectDest.height);
        }

        g.setColor(Color.black);

        String textAction = "INTERPHONE";
        if(state==STATE_IDLE) {
            Color c = properties.getColorGenericButton();
            g.setColor(new Color(3*c.getRed()/4,3*c.getGreen()/4,3*c.getBlue()/4));
        }
        else {
            textAction = (state==STATE_RINGING)?"ANSWER":"RELEASE";
            g.drawRect(rectSrc.x, rectSrc.y, rectSrc.width, rectSrc.height);
            g.drawRect(rectDest.x, rectDest.y, rectDest.width, rectDest.height);
            if(textSource!=null) {
                g.setFont(FontSelector.getFont(textSource,g,rectSrc,rectText));
                g.drawString(textSource, rectText.x, rectText.y);
            }

            if(textDest!=null) {
                g.setFont(FontSelector.getFont(textDest,g,rectDest,rectText));
                g.drawString(textDest, rectText.x, rectText.y);
            }
        }

        g.setFont(FontSelector.getFont(textAction,g,rectActn,rectText));
        g.drawString(textAction, rectText.x, rectText.y);

        // Restore attributes
        g.setColor(storedColor);
        g.setFont(storedFont);
    }

    public void mousePressed(MouseEvent e) {
        // Left mouse button only
        if(e.getButton()!=MouseEvent.BUTTON1) return;

        pressedTime = System.currentTimeMillis();

        if(eventHandler!=null) {
            eventHandler.hookButtonClicked();
        }
    }

    public void mouseReleased(MouseEvent e) {
        // Left mouse button only
        if(e.getButton()!=MouseEvent.BUTTON1) return;

        if(System.currentTimeMillis()>(pressedTime+HOLD_TIME_FOR_CLOSE)) {
            eventHandler.closeButtonClicked();
        }

        pressedTime = 0;
    }

    public void mouseClicked(MouseEvent e) {    }
    public void mouseEntered(MouseEvent e) {    }
    public void mouseExited(MouseEvent e) {    }
}
