package com.lfv.lanzius.application.phoneonly;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Iterator;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Element;

import com.lfv.lanzius.Config;
import com.lfv.lanzius.Constants;
import com.lfv.lanzius.DomTools;
import com.lfv.lanzius.application.AbstractView;
import com.lfv.lanzius.application.TerminalProperties;
import com.lfv.lanzius.application.ViewEventHandler;
import com.lfv.lanzius.application.full.ButtonHandler;
import com.lfv.lanzius.application.slim.SlimRoleItem;

/**
 * <p>
 * PhoneOnlyView
 * <p>
 * Copyright &copy; NLR 2011,
 *
 * @author Roalt Aalmoes
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
@SuppressWarnings("serial")
public class PhoneOnlyView extends AbstractView implements ActionListener, MouseListener, ButtonHandler {

    private PhoneOnlyContentPane phoneOnlyContentPane;

    private boolean talkButtonPressed;
        private Log log = LogFactory.getLog(getClass());

    private int selectedRole = 0;

    public PhoneOnlyView(ViewEventHandler viewEventHandler, TerminalProperties properties) {
        super(Config.TITLE+" - Terminal", viewEventHandler);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                eventHandler.closeButtonClicked();
            }
        });

        phoneOnlyContentPane = new PhoneOnlyContentPane();
        this.setBounds(100,100,853,130);
        this.getContentPane().add(phoneOnlyContentPane, BorderLayout.CENTER);
        setContentPane(phoneOnlyContentPane);

        phoneOnlyContentPane.getHookButton().addActionListener(this);
        this.setAlwaysOnTop(true);
        this.setResizable(false);

        log.info("PhoneOnlyView initialized");

    }

    private void updatePeerStructure() {
        phoneOnlyContentPane.removeAllPeers();

        selectedRole = 0; // For now, fix it to '0' (first role) FIXME
        synchronized(lock) {
            Element thisRole = (Element) model.getRootElement().getChild("RoleSetup").getChildren().get(selectedRole);
            final int roleId = DomTools.getAttributeInt(thisRole,"id",0,true);
            @SuppressWarnings("rawtypes")
                        Iterator peerIterator = thisRole.getChild("PhonePeers").getChildren().iterator();
            while(peerIterator.hasNext()) {

                Element err = (Element)peerIterator.next();

                // Get the id and the name from the model
                String name = "";
                int peerId;
                synchronized(lock) {
                    peerId = DomTools.getAttributeInt(err, "id", 0, false);
                    name = DomTools.getChildText(err, "Name", String.valueOf(peerId), false);
                }

                JButton peerCallButton = new JButton(name);
                //peerCallButton.setSize(100, 100);
                //peerCallButton.setMinimumSize(new Dimension(100, 100));
                phoneOnlyContentPane.addPeerJButton(peerCallButton);

                final int finalPeerId = peerId;

                peerCallButton.addActionListener(new ActionListener() {

                                        public void actionPerformed(ActionEvent e) {
                                                log.debug("peerCallButton ActionListener roleId="+roleId+" and peerId="+finalPeerId);
                                                eventHandler.dialButtonClicked(roleId, finalPeerId);
                                        }

                });
                log.debug("Adding peer to call button fore roleId="+roleId);
            }
        }
        log.debug("(debug)Update peer structure");

    }

    public void updateStructure() {
        DefaultComboBoxModel dcb_model = new DefaultComboBoxModel();

        String firstRole = "";
        // Roles
        dcb_model = new DefaultComboBoxModel();
        synchronized(lock) {
            @SuppressWarnings("rawtypes")
                        Iterator iter = model.getRootElement().getChild("RoleSetup").getChildren().iterator();
            while(iter.hasNext()) {
                Element er = (Element)iter.next();
                int roleId = DomTools.getAttributeInt(er, "id", 0, false);
                if(roleId>0) {
                    SlimRoleItem item = new SlimRoleItem(er);
                    if(firstRole.length() == 0) {
                        firstRole = item.toString();
                    }
                    dcb_model.addElement(item);
                }
            }
        }
        phoneOnlyContentPane.setRole(firstRole);
        //contentPane.getRoleJLabel().setModel(dcb_model);

        // Peers
        updatePeerStructure();

        // Update and repaint
        updateIndicators();
        ((JPanel)phoneOnlyContentPane).revalidate();
        ((JPanel)phoneOnlyContentPane).repaint();
    }

    public void updateRadioView() {
        updateIndicators();
        ((JPanel)phoneOnlyContentPane).repaint();
    }

    public void updatePhoneView() {
        updateRadioView();
    }

    public void updateIndicators() {



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

            phoneOnlyContentPane.getDialLabel().setText(sDial);
            phoneOnlyContentPane.getHookButton().setBackground(cDial);
            phoneOnlyContentPane.getHookButton().setText(sHook);
            log.debug("Setting sDial = "+sDial+" setBackground:"+cDial+" setHook="+sHook);
        }
    }

    public void updateStatus(String status, boolean error) {
        // do nothing, no status button
    }

    public void actionPerformed(ActionEvent e) {

        if(e.getSource()==phoneOnlyContentPane.getHookButton()) {
            eventHandler.hookButtonClicked();
            updatePhoneView();
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

        @Override
        public void buttonClicked(String actionCommand, int id) {
                if(actionCommand.equals("PEER")) {
                        log.error("PhoneOnlyView: PEER pressed id="+id);
            int roleId = 0;
            synchronized(lock) {
                Element er = (Element) model.getRootElement().getChild("RoleSetup").getChildren().get(selectedRole);
                roleId = DomTools.getAttributeInt(er,"id",0,true);
            }
            log.debug("buttonClicked PEER: calling eventHandler "+roleId+" to id="+id);
            eventHandler.dialButtonClicked(roleId, id);
                } else {
                        log.error("Unknown button implemented in PhoneOnlyView");
                }
        }
}
