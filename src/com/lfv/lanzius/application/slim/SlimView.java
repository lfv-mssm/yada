package com.lfv.lanzius.application.slim;

import com.lfv.lanzius.Config;
import com.lfv.lanzius.Constants;
import com.lfv.lanzius.DomTools;
import com.lfv.lanzius.application.AbstractView;
import com.lfv.lanzius.application.TerminalProperties;
import com.lfv.lanzius.application.ViewEventHandler;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Iterator;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jdom.Element;

/**
 * <p>
 * SlimView
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
public class SlimView extends AbstractView implements ActionListener, MouseListener {

    private Object statusLock = new Object();

    private SlimContentPane contentPane;
    private TerminalProperties properties;

    enum SlimConfig {
        HORIZ_CONFIG,
        VERT_CONFIG,
    };

    private SlimConfig slimConfig;
    private boolean talkButtonPressed;

    public SlimView(ViewEventHandler viewEventHandler, TerminalProperties properties) {
        super(Config.TITLE+" - Terminal", viewEventHandler);
        this.properties = properties;
        if(properties.getUserInterfaceStyle().equalsIgnoreCase("slim-horiz")) {
                slimConfig = SlimConfig.HORIZ_CONFIG;
        } else {
                slimConfig = SlimConfig.VERT_CONFIG;
        }


        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                eventHandler.closeButtonClicked();
            }
        });

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle maximumWindowBounds = ge.getMaximumWindowBounds();

        if(slimConfig == SlimConfig.HORIZ_CONFIG) {
            contentPane = new SlimHorizContentPane();
            setContentPane((JPanel)contentPane);
            pack();
            Dimension d = getSize();
            setBounds(0,maximumWindowBounds.height-d.height,maximumWindowBounds.width,d.height);
        } else {
            contentPane = new SlimVertContentPane();
            setContentPane((JPanel)contentPane);
            pack();
            Dimension d = getSize();
            setBounds(maximumWindowBounds.width-d.width,0,d.width,maximumWindowBounds.height);
        }
        System.out.println("Not slim-phone config, so adding listeners");
        contentPane.getRadioComboBox().addActionListener(this);
        contentPane.getStateButton().addActionListener(this);
        contentPane.getTalkButton().addMouseListener(this);
        contentPane.getSettingsButton().addActionListener(this);
        contentPane.getRoleComboBox().addActionListener(this);
        contentPane.getDialButton().addActionListener(this);
        contentPane.getHookButton().addActionListener(this);
    }

    private void updatePeerStructure() {

        DefaultComboBoxModel dcb_model = new DefaultComboBoxModel();
        synchronized(lock) {
            // Get all peers for this role
            SlimRoleItem item = (SlimRoleItem)contentPane.getRoleComboBox().getSelectedItem();
            if(item!=null) {
                Iterator iter = item.getPeerList().iterator();
                while(iter.hasNext()) {
                    Element err = (Element)iter.next();
                    int roleId = DomTools.getAttributeInt(err, "id", 0, false);
                    if(roleId>0) {
                        dcb_model.addElement(new SlimPeerItem(err));
                    }
                }
            }
        }
        contentPane.getPeerComboBox().setModel(dcb_model);
    }

    public void updateStructure() {
        DefaultComboBoxModel dcb_model = new DefaultComboBoxModel();

            // Radio
        synchronized(lock) {
            Iterator iter = model.getRootElement().getChild("ChannelSetup").getChildren().iterator();
            while(iter.hasNext()) {
                Element ec = (Element)iter.next();
                int ucid = DomTools.getAttributeInt(ec, "id", 0, true);
                if(ucid>0) {
                    SlimRadioItem item = new SlimRadioItem(ec);
                    if(!item.isHidden())
                        dcb_model.addElement(item);
                }
            }
        }
        contentPane.getRadioComboBox().setModel(dcb_model);


        // Roles
        dcb_model = new DefaultComboBoxModel();
        synchronized(lock) {
            Iterator iter = model.getRootElement().getChild("RoleSetup").getChildren().iterator();
            while(iter.hasNext()) {
                Element er = (Element)iter.next();
                int roleId = DomTools.getAttributeInt(er, "id", 0, false);
                if(roleId>0) {
                    SlimRoleItem item = new SlimRoleItem(er);
                    dcb_model.addElement(item);
                }
            }
        }
        contentPane.getRoleComboBox().setModel(dcb_model);

        // Peers
        updatePeerStructure();

        // Update and repaint
        updateIndicators();
        ((JPanel)contentPane).revalidate();
        ((JPanel)contentPane).repaint();
    }

    public void updateRadioView() {
        updateIndicators();
        ((JPanel)contentPane).repaint();
    }

    public void updatePhoneView() {
        updateRadioView();
    }

    public void updateIndicators() {

        SlimRadioItem item = (SlimRadioItem)contentPane.getRadioComboBox().getModel().getSelectedItem();

        // Update state button
        String state = (item==null)?"off":item.getState();
        contentPane.getStateButton().setText(state.toUpperCase());
        Color c = Color.lightGray;
        if(state.equals("rxtx"))    c = Color.green;
        else if(state.equals("rx")) c = Color.yellow;
        contentPane.getStateButton().setBackground(c);

        // Update busy button
        synchronized(lock) {
            boolean isOutgoing = false;
            Iterator iter = super.model.getRootElement().getChild("ChannelSetup").getChildren().iterator();
            c = Color.lightGray;
            while(iter.hasNext()) {
                Element ec = (Element)iter.next();
                if(DomTools.getAttributeBoolean(ec, "recvp", false, false) ||
                   DomTools.getAttributeBoolean(ec, "recvs", false, false)) {
                    c = Color.yellow;
                }
                else if(DomTools.getAttributeBoolean(ec, "send", false, false)) {
                    c = Color.yellow;
                    isOutgoing = true;
                }
                else if(DomTools.getAttributeBoolean(ec, "fail", false, false)) {
                    c = Color.red;
                    isOutgoing = true;
                    break;
                }
            }
            contentPane.getBusyButton().setBackground(c);
            if(!isOutgoing) c = Color.lightGray;
            contentPane.getTalkButton().setBackground(c);
        }

        // Update dial button
        synchronized(lock) {

            Element eh = super.model.getRootElement().getChild("HookButton");

            // Get texts from model
            String textSource = DomTools.getAttributeString(eh, "src",  "?", false);
            String textDest   = DomTools.getAttributeString(eh, "dest", "?", false);

            // Get state from model
            String s = DomTools.getAttributeString(eh, "state", "idle", false);

            String sDial = "DIAL";
            String sHook = "INTERPHONE";
            Color  cDial = Color.lightGray;

            if(s.equals("dialing")) {
                sDial = textSource + " > " + textDest;
                sHook = "RELEASE";
                cDial = Color.yellow;
            }
            else if(s.equals("busy")) {
                sDial = textSource + " ! " + textDest;
                sHook = "RELEASE";
                cDial = Color.red;
            }
            else if(s.equals("in_call")) {
                sDial = textSource + " - " + textDest;
                sHook = "RELEASE";
                cDial = Color.yellow;
            }
            else if(s.equals("ringing")) {
                sDial = textDest + " < " + textSource;
                sHook = "ANSWER";
                cDial = Color.yellow;
            }

            contentPane.getDialButton().setText(sDial);
            contentPane.getDialButton().setBackground(cDial);
            contentPane.getHookButton().setText(sHook);
        }
    }

    public void updateStatus(String status, boolean error) {
        synchronized(statusLock) {
            JLabel statusLabel = contentPane.getStatusLabel();
            statusLabel.setText(status);
            statusLabel.setVisible(status!=null);
        }
    }

    public void actionPerformed(ActionEvent e) {
        if(e.getSource()==contentPane.getRadioComboBox()) {
            updateRadioView();
        }
        else if(e.getSource()==contentPane.getStateButton()) {

            SlimRadioItem item = (SlimRadioItem)contentPane.getRadioComboBox().getModel().getSelectedItem();
            if(item!=null) {
                if(!item.isLocked()) {
                    String state = item.getState();
                    if(state.equals("off"))
                        eventHandler.rxtxStateUpdated(item.getId(), true, false);
                    else if(state.equals("rx"))
                        eventHandler.rxtxStateUpdated(item.getId(), true, true);
                    else
                        eventHandler.rxtxStateUpdated(item.getId(), false, false);

                    updateRadioView();
                }
            }
        }
        else if(e.getSource()==contentPane.getRoleComboBox()) {
            updatePeerStructure();
            updatePhoneView();
        }
        else if(e.getSource()==contentPane.getDialButton()) {
            SlimPeerItem peerItem = (SlimPeerItem)contentPane.getPeerComboBox().getSelectedItem();

            if(peerItem!=null && peerItem.isActive()) {
                SlimRoleItem roleItem = (SlimRoleItem)contentPane.getRoleComboBox().getSelectedItem();
                if(roleItem!=null) {
                    eventHandler.dialButtonClicked(roleItem.getId(), peerItem.getId());
                    updatePhoneView();
                }
            }
        }
        else if(e.getSource()==contentPane.getHookButton()) {
            eventHandler.hookButtonClicked();
            updatePhoneView();
        }
        else if(e.getSource()==contentPane.getSettingsButton()) {
            eventHandler.settingsDialogOpen();
        }
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

    public void mouseClicked(MouseEvent e) {
    }
    public void mouseEntered(MouseEvent e) {
    }
    public void mouseExited(MouseEvent e) {
    }
}
