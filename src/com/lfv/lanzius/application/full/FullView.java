package com.lfv.lanzius.application.full;

import com.lfv.lanzius.Config;
import com.lfv.lanzius.Constants;
import com.lfv.lanzius.DomTools;
import com.lfv.lanzius.application.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Iterator;
import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jdom.Element;

/**
 * <p>
 * FullView
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
public class FullView extends AbstractView implements ButtonHandler {

    private Object statusLock = new Object();

    private FullContentPane contentPane;
    private TerminalProperties properties;
    private JLabel statusLabel;

    private int selectedRole;

    public FullView(ViewEventHandler viewEventHandler, TerminalProperties properties) {
        super(Config.TITLE+" - Terminal", viewEventHandler);
        this.properties = properties;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                eventHandler.closeButtonClicked();
            }
        });

        // Set own content pane
        contentPane = new FullContentPane();
        setContentPane(contentPane);

        // Add status message
        statusLabel = new JLabel("", JLabel.CENTER);
        statusLabel.setForeground(Color.red);
        statusLabel.setVisible(false);
        contentPane.getStatusPanel().setLayout(new BorderLayout());
        contentPane.getStatusPanel().add(statusLabel,BorderLayout.CENTER);

        // Hide mouse cursor if stated in the properties file
        if(properties.isMouseCursorHidden())
            contentPane.setCursor(InvisibleCursor.getCursor());
String size = properties.getMaxUserInterfaceSize().toLowerCase();
System.out.println("Configured size=" + size + "\n");
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        if(size.equals("full") ) {
//        if(Config.CLIENT_SIZE_FULLSCREEN) {
System.out.println("CLIENT_SIZE_FULLSCREEN|||\n");
            setResizable(false);
            setUndecorated(true);
            setBounds(0,0,screenSize.width,screenSize.height);
        }
        else {
            GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Rectangle maximumWindowBounds = graphicsEnvironment.getMaximumWindowBounds();
            maximumWindowBounds.y = -1;
            if(size.equals("50p")) {
//            if(Config.CLIENT_SIZE_50P_WINDOW) {
                maximumWindowBounds.width/=2;
                maximumWindowBounds.height/=2;
            }
//            else if(Config.CLIENT_SIZE_100P_WINDOW) {
            else if(size.equals("100p")) {
                // Gnome fix, see LanziusServer.java
                maximumWindowBounds.height -= 1;
            }
//            else if(Config.CLIENT_SIZE_800X600_WINDOW) {
            else if (size.contains("x") || size.contains("+")) {
System.out.println("size requested=" + size+ "\n");
                String[] temp;
                String[] resolution;
                maximumWindowBounds.width = 800;
                maximumWindowBounds.height = 600;
// WxH+X+Y
                if (size.contains("+")){
                    if (size.startsWith("+")){
// +X+Y no width/height
                       temp = size.split("+");
                       if (temp.length >= 1) maximumWindowBounds.x = Integer.parseInt(temp[0]);
                       if (temp.length >= 2) maximumWindowBounds.y = Integer.parseInt(temp[1]);
                    } else {
// WxH+X+Y
                       temp = size.split("\\+");
                       resolution = temp[0].split("x");

                       if (resolution.length >= 1) maximumWindowBounds.width =  Integer.parseInt(resolution[0]);
                       if (resolution.length >= 2) maximumWindowBounds.height =  Integer.parseInt(resolution[1]);
                       if (temp.length >= 2) maximumWindowBounds.x = Integer.parseInt(temp[1]);
                       if (temp.length >= 3) maximumWindowBounds.y = Integer.parseInt(temp[2]);
                    }
                } else {
// WxH
                    resolution = size.split("x");
                    if (resolution.length >= 1) maximumWindowBounds.width =  Integer.parseInt(resolution[0]);
                    if (resolution.length >= 2) maximumWindowBounds.height =  Integer.parseInt(resolution[1]);
                }
                System.out.println("W=" + maximumWindowBounds.width + " H=" + maximumWindowBounds.height );
                System.out.println("X=" + maximumWindowBounds.x + " Y=" + maximumWindowBounds.y);

            }
            if (maximumWindowBounds.y == -1){
               maximumWindowBounds.y = screenSize.height-maximumWindowBounds.height;
            }
            setBounds(maximumWindowBounds);
        }
    }

    private JPanel createFiller(int maxHeight, int minHeight) {
        JPanel p = new JPanel();
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE,maxHeight));
        p.setMinimumSize(new Dimension(0,minHeight));
        if(Config.DEBUG)
            p.setBorder(new javax.swing.border.LineBorder(Color.ORANGE));
        return p;
    }

    private void updatePeerStructure() {
        JPanel peerPanel = contentPane.getPeerPanel();
        peerPanel.removeAll();

        // No roles
        if(selectedRole<0) return;

        synchronized(lock) {

            // Get all peers for this role
            int n = 0;
            Element er = (Element) model.getRootElement().getChild("RoleSetup").getChildren().get(selectedRole);
            Iterator iter = er.getChild("PhonePeers").getChildren().iterator();

            while(iter.hasNext()) {

                // Setup row
                JPanel rowPanel = new JPanel(new GridLayout(1,3,4,0));
                rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Constants.FULL_PHONE_MAXHEIGHT));
                rowPanel.setMinimumSize(new Dimension(0, Constants.FULL_PHONE_MINHEIGHT));

                // Always add one full row
                for(int i=0;i<3;i++) {
                    Element err = null;
                    int roleId = 0;

                    if(iter.hasNext()) {
                        err = (Element)iter.next();
                        roleId = DomTools.getAttributeInt(err, "id", 0, false);
                    }

                    // add button
                    rowPanel.add(new FullButton(roleId>0?err:null, this, "PEER",properties.getColorPhoneButtonActive(),properties.getColorPhoneButtonInactive()));
                }

                // Add row
                peerPanel.add(rowPanel);

                // Add empty space
                peerPanel.add(Box.createRigidArea(new Dimension(0,4)));

                n++;
            }

            // Fill out with empty space to get nice scaling
            if(n>0) {
                n = Constants.FULL_PHONE_NROWS-n;
                if(n>0)
                    peerPanel.add(createFiller(n*(Constants.FULL_PHONE_MAXHEIGHT+4), n*(Constants.FULL_PHONE_MINHEIGHT+4)));
            }
        }

        peerPanel.revalidate();
    }

    public void updateStructure() {
        JPanel radioPanel = contentPane.getRadioPanel();
        radioPanel.removeAll();

        synchronized(lock) {

            // Radio
            int n = 0;
            Iterator iter = model.getRootElement().getChild("ChannelSetup").getChildren().iterator();
            while(iter.hasNext()) {
                Element ec = (Element)iter.next();
                int ucid = DomTools.getAttributeInt(ec, "id", 0, true);
                if(ucid>0) {
                    FullChannelButton button = new FullChannelButton(ec, eventHandler, properties);
                    if(!button.isHidden()) {
                        radioPanel.add(button);
                        radioPanel.add(Box.createRigidArea(new Dimension(0,4)));
                        n++;
                    }
                }
            }

            // Fill out with empty space to get nice scaling
            if(n>0) {
                n = Constants.FULL_RADIO_NROWS-n;
                if(n>0)
                    radioPanel.add(createFiller(n*(Constants.FULL_RADIO_MAXHEIGHT+4), n*(Constants.FULL_RADIO_MINHEIGHT+4)));
            }

            // Roles
            JPanel rolePanel = contentPane.getRolePanel();
            rolePanel.removeAll();

            n = 0;
            int currentRole = 0;
            selectedRole = -1;
            iter = model.getRootElement().getChild("RoleSetup").getChildren().iterator();
            while(iter.hasNext()) {

                // Setup row
                JPanel rowPanel = new JPanel(new GridLayout(1,3,4,0));
                rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Constants.FULL_PHONE_MAXHEIGHT));
                rowPanel.setMinimumSize(new Dimension(0, Constants.FULL_PHONE_MINHEIGHT));

                // Always add one full row
                for(int i=0;i<3;i++) {
                    Element er = null;
                    int roleId = 0;

                    // Try to get the next role
                    if(iter.hasNext()) {
                        er = (Element)iter.next();
                        roleId = DomTools.getAttributeInt(er, "id", 0, false);
                    }

                    // If exists, set first selected role
                    if(roleId>0) {
                        if(selectedRole<0)
                            selectedRole = currentRole;
                    }
                    else er = null;

                    // add button
                    rowPanel.add(new FullButton(er, this, "ROLE",properties.getColorPhoneButtonActive(),properties.getColorPhoneButtonInactive()));

                    currentRole++;
                }

                // Add row
                rolePanel.add(rowPanel);

                // Add empty space for next row
                rolePanel.add(Box.createRigidArea(new Dimension(0,4)));

                n++;
            }

            // Fill out with empty space to get nice scaling
            if(n>0) {
                n = Constants.FULL_PHONE_NROWS-n;
                if(n>0)
                    rolePanel.add(createFiller(n*(Constants.FULL_PHONE_MAXHEIGHT+4), n*(Constants.FULL_PHONE_MINHEIGHT+4)));
            }

            // Control panel items
            JPanel talkPanel = contentPane.getTalkPanel();
            talkPanel.removeAll();
            talkPanel.add(new FullTalkButton(model.getRootElement().getChild("TalkButton"), eventHandler, properties));

            Color c = properties.getColorGenericButton();
            JPanel settingsPanel = contentPane.getSettingsPanel();
            settingsPanel.removeAll();
            settingsPanel.add(new FullButton(model.getRootElement().getChild("Settings"), this, "SETTINGS", c, c));

            JPanel hookPanel = contentPane.getHookPanel();
            hookPanel.removeAll();
            hookPanel.add(new FullHookButton(model.getRootElement().getChild("HookButton"), eventHandler, properties));
        }

        updatePeerStructure();

        invalidate();
        updatePhoneView();
    }

    public void updateRadioView() {
        contentPane.getRadioPanel().repaint();
        contentPane.getTalkPanel().repaint();
    }

    public void updatePhoneView() {

        // Update line width according to selectedRole
        synchronized(lock) {
            int i=0;
            Iterator iter = model.getRootElement().getChild("RoleSetup").getChildren().iterator();
            while(iter.hasNext()) {
                Element er = (Element)iter.next();
                er.setAttribute("line", (i==selectedRole)?"2":"1");
                i++;
            }
        }

        contentPane.getRolePanel().repaint();
        contentPane.getPeerPanel().repaint();
        contentPane.getHookPanel().repaint();
    }

    public void updateStatus(String status, boolean error) {
        synchronized(statusLock) {
            statusLabel.setText(status);
            statusLabel.setVisible(status!=null);
        }
    }

    public void buttonClicked(String actionCommand, int id) {
        if(actionCommand.equals("ROLE")) {
            synchronized(lock) {
                int index = DomTools.getChildIndexById(model.getRootElement().getChild("RoleSetup"), id);
                if(index>=0) {
                    selectedRole = index;
                    updatePeerStructure();
                    updatePhoneView();
                }
            }
        }
        else if(actionCommand.equals("PEER")) {
            int roleId = 0;
            synchronized(lock) {
                Element er = (Element) model.getRootElement().getChild("RoleSetup").getChildren().get(selectedRole);
                roleId = DomTools.getAttributeInt(er,"id",0,true);
            }
            eventHandler.dialButtonClicked(roleId, id);
        }
        else if(actionCommand.equals("SETTINGS")) {
            eventHandler.settingsDialogOpen();
        }
    }
}
