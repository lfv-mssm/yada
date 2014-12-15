package com.lfv.lanzius.server;

import com.lfv.lanzius.Config;
import com.lfv.lanzius.DomTools;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;

/**
 * <p>
 * TerminalView
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
public class WorkspacePanel extends JPanel implements ActionListener {

    /**
         *
         */
        private static final long serialVersionUID = 1L;

        private Log log;

    private LanziusServer server;

    private WorkspaceView view;

    private boolean isServerStarted;

    private boolean isTerminalSelection;
    private List<Integer> selectionList;

    private JToolBar  toolBar;
    private ImageIcon iconServerOn;
    private ImageIcon iconServerOff;
    private JButton   buttonServer;
    private JButton   buttonLoad;
    private JButton   buttonLink;
    private JButton   buttonUnlink;
    private JButton   buttonSwap;
    private JButton   buttonMonitor;
    private JButton   buttonStart;
    private JButton   buttonStop;
    private JButton       buttonIsaStart;
    //private JButton   buttonIsaToggle;

    public WorkspacePanel(LanziusServer server) {
        log = LogFactory.getLog(getClass());
        this.server = server;
        initComponents();
    }

    private void initComponents() {

        view = new WorkspaceView(server, this);

        toolBar = new JToolBar(Config.TITLE+" - Toolbar");

        buttonServer = new JButton();
        buttonLink = new JButton();
        buttonLoad = new JButton();
        buttonUnlink = new JButton();
        buttonSwap = new JButton();
        buttonMonitor = new JButton();
        buttonStart = new JButton();
        buttonStop = new JButton();
        buttonIsaStart = new JButton();
        //buttonIsaToggle = new JButton();

        setLayout(new BorderLayout());

        iconServerOn  = new ImageIcon("data/resources/icons/Server_on.png");
        iconServerOff = new ImageIcon("data/resources/icons/Server_off.png");

        buttonServer.setFont(new Font("Dialog", 0, 10));
        buttonServer.setIcon(iconServerOff);
        buttonServer.setText("Server");
        buttonServer.setDisabledIcon(iconServerOff);
        buttonServer.setEnabled(false);
        buttonServer.setFocusPainted(false);
        buttonServer.setHorizontalTextPosition(SwingConstants.CENTER);
        buttonServer.setIconTextGap(0);
        buttonServer.setVerticalTextPosition(SwingConstants.BOTTOM);

        toolBar.add(buttonServer);

        toolBar.addSeparator(new Dimension(40,1));

        buttonLoad.setFont(new Font("Dialog", 0, 10));
        buttonLoad.setIcon(new ImageIcon("data/resources/icons/Load.png"));
        buttonLoad.setText("Load");
        buttonLoad.setToolTipText("Load an exercise xml file");
        buttonLoad.setDisabledIcon(new ImageIcon("data/resources/icons/Load_gray.png"));
        buttonLoad.setEnabled(false);
        buttonLoad.setFocusPainted(false);
        buttonLoad.setHorizontalTextPosition(SwingConstants.CENTER);
        buttonLoad.setIconTextGap(0);
        buttonLoad.setVerticalTextPosition(SwingConstants.BOTTOM);
        buttonLoad.addActionListener(this);
        toolBar.add(buttonLoad);

        toolBar.addSeparator();
        buttonLink.setFont(new Font("Dialog", 0, 10));
        buttonLink.setIcon(new ImageIcon("data/resources/icons/Link.png"));
        buttonLink.setText("Link");
        buttonLink.setToolTipText("Link one or more players to the selected terminal");
        buttonLink.setDisabledIcon(new ImageIcon("data/resources/icons/Link_gray.png"));
        buttonLink.setEnabled(false);
        buttonLink.setFocusPainted(false);
        buttonLink.setHorizontalTextPosition(SwingConstants.CENTER);
        buttonLink.setIconTextGap(0);
        buttonLink.setVerticalTextPosition(SwingConstants.BOTTOM);
        buttonLink.addActionListener(this);
        toolBar.add(buttonLink);

        buttonUnlink.setFont(new Font("Dialog", 0, 10));
        buttonUnlink.setIcon(new ImageIcon("data/resources/icons/Unlink.png"));
        buttonUnlink.setText("Unlink");
        buttonUnlink.setToolTipText("Unlink players from selected terminal(s) or group(s)");
        buttonUnlink.setDisabledIcon(new ImageIcon("data/resources/icons/Unlink_gray.png"));
        buttonUnlink.setEnabled(false);
        buttonUnlink.setFocusPainted(false);
        buttonUnlink.setHorizontalTextPosition(SwingConstants.CENTER);
        buttonUnlink.setIconTextGap(0);
        buttonUnlink.setVerticalTextPosition(SwingConstants.BOTTOM);
        buttonUnlink.addActionListener(this);
        toolBar.add(buttonUnlink);

        buttonSwap.setFont(new Font("Dialog", 0, 10));
        buttonSwap.setIcon(new ImageIcon("data/resources/icons/Swap.png"));
        buttonSwap.setText("Swap");
        buttonSwap.setToolTipText("Swap the players linked to the two selected terminals");
        buttonSwap.setDisabledIcon(new ImageIcon("data/resources/icons/Swap_gray.png"));
        buttonSwap.setEnabled(false);
        buttonSwap.setFocusPainted(false);
        buttonSwap.setHorizontalTextPosition(SwingConstants.CENTER);
        buttonSwap.setIconTextGap(0);
        buttonSwap.setVerticalTextPosition(SwingConstants.BOTTOM);
        buttonSwap.addActionListener(this);
        toolBar.add(buttonSwap);

        toolBar.addSeparator();

        buttonMonitor.setFont(new Font("Dialog", 0, 10));
        buttonMonitor.setIcon(new ImageIcon("data/resources/icons/Listen.png"));
        buttonMonitor.setText("Monitor");
        buttonMonitor.setToolTipText("Listen and record the selected terminal");
        buttonMonitor.setDisabledIcon(new ImageIcon("data/resources/icons/Listen_gray.png"));
        buttonMonitor.setEnabled(false);
        buttonMonitor.setFocusPainted(false);
        buttonMonitor.setHorizontalTextPosition(SwingConstants.CENTER);
        buttonMonitor.setIconTextGap(0);
        buttonMonitor.setVerticalTextPosition(SwingConstants.BOTTOM);
        buttonMonitor.addActionListener(this);
        toolBar.add(buttonMonitor);

        toolBar.addSeparator();

        buttonStart.setFont(new Font("Dialog", 0, 10));
        buttonStart.setIcon(new ImageIcon("data/resources/icons/Start.png"));
        buttonStart.setText("Start");
        buttonStart.setToolTipText("Start the selected group(s)");
        buttonStart.setDisabledIcon(new ImageIcon("data/resources/icons/Start_gray.png"));
        buttonStart.setEnabled(false);
        buttonStart.setFocusPainted(false);
        buttonStart.setHorizontalTextPosition(SwingConstants.CENTER);
        buttonStart.setIconTextGap(0);
        buttonStart.setVerticalTextPosition(SwingConstants.BOTTOM);
        buttonStart.addActionListener(this);
        toolBar.add(buttonStart);

        buttonStop.setFont(new Font("Dialog", 0, 10));
        buttonStop.setIcon(new ImageIcon("data/resources/icons/Stop.png"));
        buttonStop.setText("Stop");
        buttonStop.setToolTipText("Stop the selected group(s)");
        buttonStop.setDisabledIcon(new ImageIcon("data/resources/icons/Stop_gray.png"));
        buttonStop.setEnabled(false);
        buttonStop.setFocusPainted(false);
        buttonStop.setHorizontalTextPosition(SwingConstants.CENTER);
        buttonStop.setIconTextGap(0);
        buttonStop.setVerticalTextPosition(SwingConstants.BOTTOM);
        buttonStop.addActionListener(this);
        toolBar.add(buttonStop);

        toolBar.addSeparator();

        buttonIsaStart.setFont(new Font("Dialog", 0, 10));
        buttonIsaStart.setIcon(new ImageIcon("data/resources/icons/StartISA.png"));
        buttonIsaStart.setText("ISA Start/Stop");
        buttonIsaStart.setToolTipText("Start measure work load for selected clients");
        buttonIsaStart.setDisabledIcon(new ImageIcon("data/resources/icons/StartISA_gray.png"));
        buttonIsaStart.setEnabled(true);
        buttonIsaStart.setFocusPainted(false);
        buttonIsaStart.setHorizontalTextPosition(SwingConstants.CENTER);
        buttonIsaStart.setIconTextGap(0);
        buttonIsaStart.setVerticalTextPosition(SwingConstants.BOTTOM);
        buttonIsaStart.addActionListener(this);
        toolBar.add(buttonIsaStart);

        /*
        buttonIsaToggle.setFont(new Font("Dialog", 0, 10));
        buttonIsaToggle.setIcon(new ImageIcon("data/resources/icons/StopISA.png"));
        buttonIsaToggle.setText("Toggle ISA");
        buttonIsaToggle.setToolTipText("Start/Stop measure work load for selected clients");
        buttonIsaToggle.setDisabledIcon(new ImageIcon("data/resources/icons/StopISA_gray.png"));
        buttonIsaToggle.setEnabled(false);
        buttonIsaToggle.setFocusPainted(false);
        buttonIsaToggle.setHorizontalTextPosition(SwingConstants.CENTER);
        buttonIsaToggle.setIconTextGap(0);
        buttonIsaToggle.setVerticalTextPosition(SwingConstants.BOTTOM);
        buttonIsaToggle.addActionListener(this);
        toolBar.add(buttonIsaToggle);
        */

        add(toolBar, BorderLayout.NORTH);
        add(view, BorderLayout.CENTER);
    }

    public void setServerStartedDate(Date serverStartedDate) {
        isServerStarted = (serverStartedDate!=null);
        buttonServer.setDisabledIcon(isServerStarted?iconServerOn:iconServerOff);
        buttonServer.setToolTipText(isServerStarted?"Server started "+serverStartedDate:null);
        buttonLoad.setEnabled(isServerStarted);
        updateButtons(false,null);
        if(!isServerStarted) {
            view.deselectTerminals();
            view.deselectGroups();
            view.repaint();
            updateButtons(false,null);
        }
    }

    public void resetIsaChart() {
        view.resetIsaChart();
    }

    public void deselectAll() {
        view.deselectTerminals();
        view.deselectGroups();
        view.repaint();

        buttonLink.setEnabled(false);
        buttonUnlink.setEnabled(false);
        buttonSwap.setEnabled(false);
        buttonMonitor.setEnabled(false);
        buttonStart.setEnabled(false);
        buttonStop.setEnabled(false);

        selectionList = null;
    }

    public void updateButtons(boolean isTerminalSelection, List<Integer> selectionList) {
        Document doc = server.getDocument();

        int nbrSelectedItems = 0;
        if(selectionList!=null) {
            this.isTerminalSelection = isTerminalSelection;
            this.selectionList = selectionList;
            nbrSelectedItems = selectionList.size();
        }
        else {
            isTerminalSelection = this.isTerminalSelection;
            selectionList = this.selectionList;
            if(selectionList!=null)
                nbrSelectedItems = selectionList.size();
        }

        // Create a "linked" array, must have at least 2 fields
        boolean[] linkedArray = new boolean[Math.max(nbrSelectedItems,2)];
        linkedArray[0] = false;
        linkedArray[1] = false;

        // Check each terminal for linked players
        int k=0;
        boolean allTerminalsLinked = true;
        boolean allGroupsStarted   = true;
        boolean allGroupsStopped   = true;
        boolean anyGroupLinked     = false;
        if(selectionList!=null) {
            Iterator<Integer> iter = selectionList.iterator();
            synchronized(doc) {
                if(isTerminalSelection) {
                    while(iter.hasNext()) {
                        Integer tid = iter.next();
                        boolean b = (DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", tid.toString())!=null);
                        allTerminalsLinked = allTerminalsLinked && b;
                        linkedArray[k++] = b;
                    }
                }
                else {
                    while(iter.hasNext()) {
                        Integer gid = iter.next();
                        Element eg = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(gid));
                        String state = DomTools.getAttributeString(eg, "state", "stopped", false);
                        allGroupsStarted = allGroupsStarted && (state.equals("started")||state.equals("paused"));
                        allGroupsStopped = allGroupsStopped && (state.equals("stopped")||state.equals("paused"));
                        anyGroupLinked   = anyGroupLinked || (DomTools.getElementFromSection(doc, "PlayerSetup", "groupid", gid.toString())!=null);
                    }
                }
            }
        }

        // Some nice logic to decide button states
        buttonLink.setEnabled(isServerStarted&&isTerminalSelection&&nbrSelectedItems==1&&!linkedArray[0]);
        buttonUnlink.setEnabled(
                isServerStarted&&
                (isTerminalSelection&&allTerminalsLinked&&nbrSelectedItems>0 || // Terminal(s)
                !isTerminalSelection&&anyGroupLinked&&nbrSelectedItems>0)); // Group
        buttonSwap.setEnabled(isServerStarted&&isTerminalSelection&&nbrSelectedItems==2&&(linkedArray[0]||linkedArray[1]));
        buttonMonitor.setEnabled(isServerStarted&&isTerminalSelection&&nbrSelectedItems==1);
        buttonStart.setEnabled(isServerStarted&&!isTerminalSelection&&allGroupsStopped&&nbrSelectedItems>0);
        buttonStop.setEnabled(isServerStarted&&!isTerminalSelection&&allGroupsStarted&&nbrSelectedItems>0);
        buttonIsaStart.setEnabled(isServerStarted);
    }

    public void actionPerformed(ActionEvent e) {

        JButton button = (JButton)e.getSource();

        //if(button==buttonServer) {
        //    JOptionPane.showMessageDialog(server.getFrame(),"Use the menu to stop the server!","Info!",JOptionPane.INFORMATION_MESSAGE);
        //}
        //else

        if(button==buttonLoad) {
            server.menuChoiceLoadExercise();
            deselectAll();
        }

        else if(button==buttonLink) {
            try {
                if(isTerminalSelection) {
                    if(server.menuChoiceTerminalLink(selectionList.get(0).intValue()))
                        deselectAll();
                }
                else deselectAll();
            }
            catch(ArrayIndexOutOfBoundsException ex) {
                log.warn("Invalid selection list when linking from workspace panel");
            }
        }

        else if(button==buttonUnlink) {
            if(isTerminalSelection) {
                if(server.menuChoiceTerminalUnlink(selectionList))
                    deselectAll();
            }
            else {
                boolean deselectAll = false;
                Iterator<Integer> iteri = selectionList.iterator();
                while(iteri.hasNext()) {
                    boolean b = server.menuChoiceTerminalUnlinkGroup(iteri.next().intValue());
                    deselectAll = deselectAll || b;
                }

                if(deselectAll)
                    deselectAll();
            }
        }

        else if(button==buttonSwap) {
            try {
                if(isTerminalSelection)
                    server.menuChoiceTerminalSwap(selectionList.get(0).intValue(), selectionList.get(1).intValue());
                deselectAll();
            }
            catch(ArrayIndexOutOfBoundsException ex) {
                log.warn("Invalid selection list when swapping from workspace panel");
            }
        }

        else if(button==buttonMonitor) {
            server.menuChoiceTerminalMonitor(selectionList.get(0).intValue());
            deselectAll();
        }

        else if(button==buttonStart) {
            boolean deselectAll = false;
            Iterator<Integer> iteri = selectionList.iterator();
            while(iteri.hasNext()) {
                boolean b = server.menuChoiceGroupStart(iteri.next().intValue());
                deselectAll = deselectAll || b;
            }

            if(deselectAll)
                deselectAll();
        }

        else if(button==buttonStop) {
            boolean deselectAll = false;
            Iterator<Integer> iteri = selectionList.iterator();
            while(iteri.hasNext()) {
                boolean b = server.menuChoiceGroupStop(iteri.next().intValue());
                deselectAll = deselectAll || b;
            }

            if(deselectAll)
                deselectAll();
        }

        else if(button==buttonIsaStart) {
                view.initIsaChart();
                if (selectionList != null) {
                        Iterator<Integer> i = selectionList.iterator();
                        int tid;
                        while (i.hasNext()) {
                                tid = i.next().intValue();
                                if (server.isaClient(tid)) {
                                        //view.removeIsaTrace(tid);
                                        server.isaStartStop(tid);
                                } else if (server.isaStartStop(tid)) {
                                        view.addIsaTrace(tid);
                                }
                        }
                        updateButtons(false, null);
                        repaint();
                }
        }
    }

    public void addIsaValue(long time, int tid, int value) {
        view.addIsaValue(time, tid, value);
        repaint();
    }
}
