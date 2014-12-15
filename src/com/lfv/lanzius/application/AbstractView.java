package com.lfv.lanzius.application;

import javax.swing.JFrame;

import org.jdom.Document;

/**
 * <p>
 * AbstractView
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
public abstract class AbstractView extends JFrame {

    protected ViewEventHandler eventHandler;
    protected Document model;

    protected Object lock = Controller.getInstance();

    public AbstractView(String frameTitle, ViewEventHandler eventHandler) {
        super(frameTitle);
        this.eventHandler = eventHandler;
    }

    public void setModel(Document model) {
        this.model = model;
        updateStructure();
    }

    public abstract void updateStructure();
    public abstract void updateRadioView();
    public abstract void updatePhoneView();

    public abstract void updateStatus(String status, boolean error);
}
