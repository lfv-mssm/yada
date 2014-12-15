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
package com.lfv.lanzius.application;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JPanel;

public class Slider extends JPanel implements MouseListener, MouseMotionListener {

    private int id;
    private ViewEventHandler handler;
    private Configurator conf;

    private int value;
    private int lastValue;

    private Color knobColor;
    private Color pressedColor;

    private Point clickPoint;
    private Rectangle rectMain;
    private Rectangle rectRail;
    private Rectangle rectKnob;
    private Rectangle rectText;

    public Slider() {
        knobColor = Color.lightGray;
        rectMain = new Rectangle();
        rectRail = new Rectangle();
        rectKnob = new Rectangle();
        rectText = new Rectangle();
        pressedColor = new Color(0xB8CFE5);
        setOpaque(false);
        setValue(0);
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public void init(int id, int value, ViewEventHandler handler, Configurator conf) {
        this.id = id;
        this.value = value;
        this.lastValue = 0;
        this.handler = handler;
        this.conf = conf;
    }

    public boolean setValue(int value) {
        this.lastValue = this.value;
        this.value = value;
        repaint();
        return (lastValue!=value);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Store attributes
        Color storedColor = g.getColor();
        Font  storedFont  = g.getFont();

        // Calculate active area without borders
        Insets paintViewInsets = getInsets();
        rectMain.width = getWidth() - (paintViewInsets.left + paintViewInsets.right);
        rectMain.height = getHeight() - (paintViewInsets.top + paintViewInsets.bottom) - 4;
        rectMain.x = paintViewInsets.left;
        rectMain.y = paintViewInsets.top;

        // Area of rail
        rectRail.setBounds(rectMain);
        rectRail.height *= 0.3; // 30% of rectMain
        rectRail.y += (rectMain.height-rectRail.height)/2; // Center vertically

        // Area of knob
        rectKnob.width = rectMain.height;
        rectKnob.height = rectMain.height;
        if(conf!=null)
            rectKnob.x = rectRail.x + value*(rectRail.width-rectKnob.width)/(conf.getNbrSteps()-1);
        else
            rectKnob.x = rectRail.x;
        rectKnob.y = rectMain.y;

        // Draw rail and knob
        g.setColor(Color.black);
        g.drawRect(rectRail.x, rectRail.y, rectRail.width-1, rectRail.height-1);
        g.setColor(knobColor);
        g.fillRect(rectKnob.x, rectKnob.y, rectKnob.width-1, rectKnob.height-1);
        g.setColor(Color.black);
        g.drawRect(rectKnob.x, rectKnob.y, rectKnob.width-1, rectKnob.height-1);

        // Draw text
        String text;
        if(conf!=null)
            text = conf.convertValue(value);
        else
            text = String.valueOf(value);

        g.setFont(FontSelector.getFont(text, g, rectKnob, rectText));
        g.drawString(text, rectText.x, rectText.y);

        // Draw ticks
        g.setColor(Color.lightGray);
        rectMain.height += 4;
        if(conf!=null) {
            int steps = conf.getNbrSteps()-1;
            for(int i=0;i<=steps;i+=conf.getSpacing()) {
                int x = rectMain.x + i*(rectRail.width-rectKnob.width)/steps + rectKnob.width/2;
                g.drawLine(x,rectMain.y+rectMain.height,x,rectMain.y+rectMain.height-3);
            }
        }

        // Restore attributes
        g.setColor(storedColor);
        g.setFont(storedFont);
    }

    public void mousePressed(MouseEvent e) {
        clickPoint = new Point(e.getPoint());
        knobColor = pressedColor;
        if(rectKnob.contains(e.getPoint())) {
            clickPoint.x -= rectKnob.x;
        }
        else {
            clickPoint.x = rectKnob.width/2;
            mouseDragged(e);
        }
        repaint();
    }

    public void mouseDragged(MouseEvent e) {
        if(clickPoint!=null&&conf!=null) {
            int s = (conf.getNbrSteps()-1);
            int d = (rectRail.width-rectKnob.width);
            int x = (s*(e.getX() - clickPoint.x)+d/2)/d;
            if(x<0) x = 0;
            if(x>s) x = s;
            if(setValue(x)) {
                if(handler!=null)
                    handler.settingsValueChanged(id, x);
            }
        }
    }

    public void mouseReleased(MouseEvent e) {
        clickPoint = null;
        knobColor = Color.lightGray;
        repaint();
    }

    // unused
    public void mouseMoved(MouseEvent e) {
    }
    public void mouseClicked(MouseEvent e) {
    }
    public void mouseEntered(MouseEvent e) {
    }
    public void mouseExited(MouseEvent e) {
    }

    public interface Configurator {
        public int getNbrSteps();
        public int getSpacing();
        public String convertValue(int value);
    }
}
