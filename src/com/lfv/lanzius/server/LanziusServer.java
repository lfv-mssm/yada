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
package com.lfv.lanzius.server;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.app.Velocity;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;

import com.lfv.lanzius.Config;
import com.lfv.lanzius.Constants;
import com.lfv.lanzius.DomTools;
import com.lfv.yada.data.server.ServerBundle;
import com.lfv.yada.data.server.ServerChannel;
import com.lfv.yada.data.server.ServerTerminal;
import com.lfv.yada.net.Packet;
import com.lfv.yada.net.server.ServerLogger;
import com.lfv.yada.net.server.ServerNetworkHandler;
import com.lfv.yada.net.server.ServerNetworkManager;
import com.lfv.yada.net.server.ServerTranslator;

public class LanziusServer implements ActionListener, ServerNetworkHandler, ServerTranslator, Constants {

    private Log log;

    // Graphical
    private JFrame frame;
    private WorkspacePanel panel;

    // Document
    private Document doc;
    private int docVersion;

    // Control
    private boolean isConfigLoaded;
    private Date serverStartedDate;
    private boolean isSwapping;
    private Element swapPlayer1;
    private Element swapPlayer2;

    // Monitor
    private int monitoredTerminalId;
    private int monitoringTerminalId;
    private int monitorChannelId;

    // Logging
    private boolean logEvents;
    private String logPath;
    private Map<Integer,ServerLogger> loggerMap;

    // Network
    private Server               httpServer;
    private Connector            httpConnector;
    private ServerNetworkManager networkManager;

    // Date
    private ServerBundle bundle;

    // Menu items
    private JMenuItem itemLoadConfig;
    private JMenuItem itemLoadExercise;
    private JMenuItem itemExit;

    private JMenuItem itemServerStart;
    private JMenuItem itemServerStop;
    private JMenuItem itemServerRestart;
    private JCheckBoxMenuItem itemServerMonitor;

    private JMenuItem itemTerminalLink;
    private JMenuItem itemTerminalUnlink;
    private JMenuItem itemTerminalUnlinkAll;
    private JMenuItem itemTerminalSwap;

    private JMenuItem itemGroupStart;
    private JMenuItem itemGroupPause;
    private JMenuItem itemGroupStop;

    private final int ALL = -1;

    private final int ATTR_ID = 0x01;

    private final int RESULT_LINK_OK                         =  0;
    private final int RESULT_LINK_ERROR_PLAYER_LINKED        =  1<<ID_BITSHIFT;
    private final int RESULT_LINK_ERROR_ROLE_LINKED          =  2<<ID_BITSHIFT;
    private final int RESULT_LINK_ERROR_TERMINAL_LINKED      = -1;
    private final int RESULT_LINK_ERROR_NO_PLAYERS           = -2;
    private final int RESULT_LINK_ERROR_NOT_ENOUGH_TERMINALS = -3;

    // ISA
    private long isaStartTime;
    private String isaTracePainter = "line";
    private HashSet<Integer> isaClients;
    private int isaPeriod;
    private int isaNumChoices = 6;
    private String[] isakeytext = { "1", "2", "3", "4", "5", "6", "7", "8", "9" };
    private boolean isaExtendedMode;

    private Timer networkStatusTimer = null;

    private File exerciseFile;

    private Properties serverProperties;

    public LanziusServer() {
        // Create a logger for the server
        log = LogFactory.getLog(getClass());
    }

    public void init() {

        log.info(Config.VERSION+"\n");

        docVersion = 0;

        frame = new JFrame(Config.TITLE+" - Server Control Panel");

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                actionPerformed(new ActionEvent(itemExit,0,null));
            }
        });

        // Create graphical terminal view
        panel = new WorkspacePanel(this);
        frame.getContentPane().add(panel);

        // Create a menu bar
        JMenuBar menuBar = new JMenuBar();

        // FILE
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        // Load configuration
        itemLoadConfig = new JMenuItem("Load configuration...");
        itemLoadConfig.addActionListener(this);
        fileMenu.add(itemLoadConfig);
        // Load terminal setup
        itemLoadExercise = new JMenuItem("Load exercise...");
        itemLoadExercise.addActionListener(this);
        fileMenu.add(itemLoadExercise);
        fileMenu.addSeparator();
        // Exit
        itemExit = new JMenuItem("Exit");
        itemExit.addActionListener(this);
        fileMenu.add(itemExit);
        menuBar.add(fileMenu);

        // SERVER
        JMenu serverMenu = new JMenu("Server");
        serverMenu.setMnemonic(KeyEvent.VK_S);
        // Start
        itemServerStart = new JMenuItem("Start");
        itemServerStart.addActionListener(this);
        serverMenu.add(itemServerStart);
        // Stop
        itemServerStop = new JMenuItem("Stop");
        itemServerStop.addActionListener(this);
        serverMenu.add(itemServerStop);
        // Restart
        itemServerRestart = new JMenuItem("Restart");
        itemServerRestart.addActionListener(this);
        itemServerRestart.setEnabled(false);
        serverMenu.add(itemServerRestart);
        // Monitor network connection
        itemServerMonitor = new JCheckBoxMenuItem("Monitor network");
        itemServerMonitor.addActionListener(this);
        itemServerMonitor.setState(false);
        serverMenu.add(itemServerMonitor);
        menuBar.add(serverMenu);

        // TERMINAL
        JMenu terminalMenu = new JMenu("Terminal");
        terminalMenu.setMnemonic(KeyEvent.VK_T);
        itemTerminalLink = new JMenuItem("Link...");
        itemTerminalLink.addActionListener(this);
        terminalMenu.add(itemTerminalLink);
        itemTerminalUnlink = new JMenuItem("Unlink...");
        itemTerminalUnlink.addActionListener(this);
        terminalMenu.add(itemTerminalUnlink);
        itemTerminalUnlinkAll = new JMenuItem("Unlink All");
        itemTerminalUnlinkAll.addActionListener(this);
        terminalMenu.add(itemTerminalUnlinkAll);
        itemTerminalSwap = new JMenuItem("Swap...");
        itemTerminalSwap.addActionListener(this);
        terminalMenu.add(itemTerminalSwap);
        menuBar.add(terminalMenu);

        // GROUP
        JMenu groupMenu = new JMenu("Group");
        groupMenu.setMnemonic(KeyEvent.VK_G);
        itemGroupStart = new JMenuItem("Start...");
        itemGroupStart.addActionListener(this);
        groupMenu.add(itemGroupStart);
        itemGroupPause = new JMenuItem("Pause...");
        itemGroupPause.addActionListener(this);
        groupMenu.add(itemGroupPause);
        itemGroupStop = new JMenuItem("Stop...");
        itemGroupStop.addActionListener(this);
        groupMenu.add(itemGroupStop);
        menuBar.add(groupMenu);

        frame.setJMenuBar(menuBar);

        GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle maximumWindowBounds = graphicsEnvironment.getMaximumWindowBounds();

        if(Config.SERVER_SIZE_FULLSCREEN) {
            maximumWindowBounds.setLocation(0,0);
            maximumWindowBounds.setSize(Toolkit.getDefaultToolkit().getScreenSize());
            frame.setResizable(false);
            frame.setUndecorated(true);
        }
        else if(Config.SERVER_SIZE_100P_WINDOW) {
            // Fixes a bug in linux using gnome. With the line below the upper and
            // lower bars are respected
            maximumWindowBounds.height-=1;
        }
        else if(Config.SERVER_SIZE_75P_WINDOW) {
            maximumWindowBounds.width*=0.75;
            maximumWindowBounds.height*=0.75;
        }
        else if(Config.SERVER_SIZE_50P_WINDOW) {
            maximumWindowBounds.width/=2;
            maximumWindowBounds.height/=2;
        }

        frame.setBounds(maximumWindowBounds);
        frame.setVisible(true);

        log.info("Starting control panel");

        // Autostart for debugging
        if(Config.SERVER_AUTOLOAD_CONFIGURATION!=null)
            actionPerformed(new ActionEvent(itemLoadConfig,0,null));

        if(Config.SERVER_AUTOSTART_SERVER)
            actionPerformed(new ActionEvent(itemServerStart,0,null));

        if(Config.SERVER_AUTOLOAD_EXERCISE!=null)
            actionPerformed(new ActionEvent(itemLoadExercise,0,null));

        if(Config.SERVER_AUTOSTART_GROUP>0)
            actionPerformed(new ActionEvent(itemGroupStart,0,null));

        try {
                        // Read the property files
                        serverProperties = new Properties();
                        serverProperties.loadFromXML(new FileInputStream("data/properties/serverproperties.xml"));
                        int rcPort = Integer.parseInt(serverProperties.getProperty("RemoteControlPort","0"));
                        if (rcPort > 0) {
                                groupRemoteControlListener(rcPort);
                        }
                        isaPeriod = Integer.parseInt(serverProperties.getProperty("ISAPeriod","60"));
                        isaNumChoices = Integer.parseInt(serverProperties.getProperty("ISANumChoices","6"));
                        for ( int i = 0; i < 9; i++ ) {
                           String tag = "ISAKeyText" + Integer.toString(i);
                           String def_val = Integer.toString(i+1);
                           isakeytext[i] = serverProperties.getProperty(tag, def_val);
                        }
                        isaExtendedMode = serverProperties.getProperty("ISAExtendedMode","false").equalsIgnoreCase("true");
                } catch (Exception e) {
                        log.error("Unable to start remote control listener");
                        log.error(e.getMessage());
                }
                isaClients = new HashSet<Integer>();
    }

    private void groupRemoteControlListener(final int port) {
        new Thread("RemoteControlThread") {
                public void run() {
                        try {
                        ServerSocket serverSocket = new ServerSocket(port);
                        Socket socket = null;
                        boolean listening = true;
                        String inputLine;
                            while (listening) {
                                socket = serverSocket.accept();
                                    BufferedReader in = new BufferedReader(
                                                    new InputStreamReader(
                                                    socket.getInputStream()));
                                    inputLine = in.readLine();
                                    log.debug("Remote control thread received: " + inputLine);
                                    try {
                                        String arglist[];
                                        arglist = inputLine.split(" ");
                                        String command = arglist[0];


                                        if (command.equals("play") ) {
                                           String newtime;
                                           String comment;

                                           if (arglist.length >= 2) newtime = arglist[1];
                                           else newtime = null;
                                           menuChoiceGroupStart(1, false, newtime);
                                           if (arglist.length >= 3) comment = arglist[2];
                                           else comment = null;

                                           if (comment != null) {
                                              ServerLogger logger = loggerMap.get(1);
                                              if (logger!=null) {
                                                 logger.print(comment);
                                              }
                                           }
                                        } else if (command.equals("stop")) {
                                           menuChoiceGroupStop(1, false);
                                        } else if (command.equals("pause")) {
                                           String comment;
                                           if (arglist.length >= 2) comment = arglist[1];
                                           else comment = null;
                                           if (comment != null) {
                                               ServerLogger logger = loggerMap.get(1);
                                           if (logger != null) {
                                                 logger.print(comment);
                                              }
                                           }
                                           menuChoiceGroupPause(1);
                                        } else if (inputLine.startsWith("logpath")) {
                                           String newlogPath;
                                           newlogPath = inputLine.substring(inputLine.indexOf(' ')+1);
                                           ServerLogger logger = loggerMap.get(1);
                                           if(logger!=null) {
                                              log.debug("ServerLogger.logpath=" + logger.getLogPath() + " new path="+ newlogPath);
                                              if (newlogPath != logger.getLogPath()) {
                                                 log.debug("ServerLogger.logpath changed!!!!");

                                                 loggerMap.remove(1);
                                                 logger.print("GROUP STOP id[1]");
                                                 logger.close();
                                                 ServerLogger newlogger = new ServerLogger(1, newlogPath);
                                                 loggerMap.put(1, newlogger);
                                                 newlogger.print("GROUP START id[1]");
                                              }
                                           } else {
                                              log.debug("No active logger for group 1");
                                           }

                                           if ((logPath != null) && (!(newlogPath.equals(logPath)) )){
                                              log.debug("logPath changed, stopping Groups");
                                           }
                                           logPath = newlogPath;
                                           log.debug("logPath set to " + logPath);
                                        }
                                    } catch (Exception e) {
                                        log.debug("Remote control action failed");
                                    }
                                    socket.close();
                            }
                            serverSocket.close();
                        } catch (IOException e) {
                            log.error("Failed to start Remote Control listener thread!");
                            log.error(e.getMessage());
                        }
                }
        }.start();
        log.info("Group remote control listening on port " + port + ".");
    }

    public void updateView() {
        if(doc!=null) {
            synchronized(doc) {
                docVersion++;
            }
        }
        else {
            docVersion++;
        }
        panel.repaint();
    }

    private void updateTerminals(int terminalId, int groupId) {
        if(doc==null) return;
        synchronized(doc) {
            // Go through all terminals
            Iterator iter = doc.getRootElement().getChild("TerminalDefs").getChildren().iterator();
            while(iter.hasNext()) {
                Element et = (Element)iter.next();

                // Send messages only if terminal is online
                if(DomTools.getAttributeBoolean(et, "online", false, false)) {
                    int tid = DomTools.getAttributeInt(et, "id", 0, true);

                    // Bother about this terminal?
                    if(terminalId==ALL||terminalId==tid) {

                        // Get linked player
                        Element eps = DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", String.valueOf(tid));

                        // Player is not linked to the terminal, send stop packet
                        if(eps==null) {
                            log.info("Sending stop to terminal "+tid);
                            networkManager.sendSessionRequestStop(tid);
                        }
                        else {
                            // Get group id
                            int gid = DomTools.getAttributeInt(eps, "groupid", 0, true);

                            // Bother about this group?
                            if(groupId==ALL||groupId==gid) {
                                // Get group state
                                Element eg = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(gid));
                                String state = DomTools.getAttributeString(eg, "state", "stopped", false);
                                if(state.equals("started")) {
                                    // send start packet
                                    log.info("Sending start to terminal "+tid);
                                    networkManager.sendSessionRequestStart(tid);                                    
                                }
                                else if(state.equals("stopped")) {
                                    // send stop packet
                                    log.info("Sending stop to terminal "+tid);
                                    networkManager.sendSessionRequestStop(tid);
                                }
                                else if(state.equals("paused")) {
                                    // send stop packet
                                    log.info("Sending stop to terminal "+tid);
                                    networkManager.sendSessionRequestPause(tid);
                                }                                
                            }
                        }
                    }
                }
            }
        }
    }

    private void setTerminalFlag(int terminalId, String flag, boolean value) {
        synchronized(doc) {
            Element et = DomTools.getElementFromSection(doc, "TerminalDefs", "id", String.valueOf(terminalId));
            if(et!=null)
                et.setAttribute(flag, String.valueOf(value));
            else
                log.error("Terminal "+terminalId+" does not exist!");
        }
    }

    private boolean getTerminalFlag(int terminalId, String flag) {
        boolean value = false;
        synchronized(doc) {
            Element et = DomTools.getElementFromSection(doc, "TerminalDefs", "id", String.valueOf(terminalId));
            if(et!=null)
                value = DomTools.getAttributeBoolean(et, flag, false, false);
            else
                log.error("Terminal "+terminalId+" does not exist!");
        }
        return value;
    }

    private void sendMonitorPacket(int attribute) {

        ServerTerminal monitoredTerminal  = bundle.getTerminal(monitoredTerminalId);
        ServerTerminal monitoringTerminal = bundle.getTerminal(monitoringTerminalId);

        if((monitoredTerminal==null) || (monitoringTerminal==null)) {
            log.error("Unable to send monitor packet, terminal does not exist!");
            return;
        }

        Packet p = null;

        // Send from monitored to monitoring
        if((attribute == Packet.ATTR_MONITOR_SINK_START) ||
           (attribute == Packet.ATTR_MONITOR_SINK_STOP)) {
            // Check online status
            if(getTerminalFlag(monitoringTerminalId, "online")) {
                log.debug("Monitor - Sending from monitored to monitoring");
                p = networkManager.prepareInfoPacket(monitoringTerminal, monitoredTerminal);
            }
        }

        // Send from monitoring to monitored
        else {
            // Check online status
            if(getTerminalFlag(monitoredTerminalId, "online")) {
                log.debug("Monitor - Sending from monitoring to monitored");
                p = networkManager.prepareInfoPacket(monitoredTerminal, monitoringTerminal);
            }
        }

        // Send packet
        if(p!=null) {
            p.addAttributeBool(attribute);
            p.addAttributeInt(Packet.ATTR_CHANNEL, monitorChannelId);
            networkManager.sendInfoPacket(p);
        }
    }

    private void monitorStart() {
        if(monitoredTerminalId==0 || monitoringTerminalId==0) return;
        if(log.isDebugEnabled())
            log.debug("Monitor - Start  monitored="+monitoredTerminalId+", monitoring="+monitoringTerminalId+", channel="+monitorChannelId);
        sendMonitorPacket(Packet.ATTR_MONITOR_SINK_START);
        sendMonitorPacket(Packet.ATTR_MONITOR_SOURCE_START);
    }

    private void monitorStop() {
        if(monitoredTerminalId==0 || monitoringTerminalId==0) return;
        if(log.isDebugEnabled())
            log.debug("Monitor - Stop   monitored="+monitoredTerminalId+", monitoring="+monitoringTerminalId+", channel="+monitorChannelId);
        sendMonitorPacket(Packet.ATTR_MONITOR_SOURCE_STOP);
        sendMonitorPacket(Packet.ATTR_MONITOR_SINK_STOP);
    }

    private boolean buildConfigurationDocument(File file) {
        try {
            SAXBuilder saxb = new SAXBuilder();
            Document newdoc = saxb.build(file);
            if(newdoc==null) return false;

            // Quick validate
            Element r = newdoc.getRootElement();
            if(r==null) return false;
            if(!r.getName().equals("Configuration")) return false;
            if(!validateSection(r.getChild("TerminalDefs"), "Terminal", ATTR_ID)) return false;
            if(!validateSection(r.getChild("GroupDefs"),    "Group",    ATTR_ID)) return false;
            if(!validateSection(r.getChild("ChannelDefs"),  "Channel",  ATTR_ID)) return false;
            if(!validateSection(r.getChild("RoleDefs"),     "Role",     ATTR_ID)) return false;
            if(!validateSection(r.getChild("PlayerDefs"),   "Player",   ATTR_ID)) return false;

            // Document is valid, add PlayerSetup node
            Element eps = new Element("PlayerSetup");
            Element epst = new Element("RecycleBin");
            newdoc.getRootElement().addContent(eps);
            newdoc.getRootElement().addContent(epst);

            doc = newdoc;
            synchronized(doc) {
                docVersion++;
            }

        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    private boolean validateSection(Element sectionElement, String expectedChildName, int attributes) {

        // Check that section exists
        if(sectionElement==null) return false;

        // Go through children
        Iterator iter = sectionElement.getChildren().iterator();
        while(iter.hasNext()) {
            Element e = (Element)iter.next();
            // Check that child is expected
            if(!e.getName().equals(expectedChildName)) return false;
            // Check id attribute for existence, isnumber and range (1-4095)
            if((attributes|ATTR_ID)!=0) {
                Attribute a = e.getAttribute("id");
                if(a==null) return false;
                try {
                    int value = a.getIntValue();
                    if(value<=0||value>=(1<<ID_BITSHIFT)) return false;
                } catch(DataConversionException ex) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasStateOr(Element node1, Element node2, String attributeName) {
        if(node1==null||node2==null)
            return false;
        return (node1.getAttribute(attributeName)!=null)||(node2.getAttribute(attributeName)!=null);
    }

    public Document getDocument() {
        return doc;
    }

    public int getDocumentVersion() {
        if(doc!=null) {
            synchronized(doc) {
                return docVersion;
            }
        }
        else {
            return docVersion;
        }
    }

    public JFrame getFrame() {
        return frame;
    }

    public boolean isServerStarted() {
        return (serverStartedDate!=null);
    }

    private int linkTerminal(int terminalId, int groupId, int[] playerIdArray, boolean autoRelocate) {

        synchronized(doc) {
            if(playerIdArray==null||playerIdArray.length==0) return RESULT_LINK_ERROR_NO_PLAYERS;

            // Terminal already linked?
            // Find the next free terminal and set relocated flag
            boolean isRelocated = false;
            Element epd = null;
            if(terminalId>0)
                epd = DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", String.valueOf(terminalId));
            if(epd!=null||terminalId==0) {
                if(autoRelocate) {
                    // Indicate that no terminal has yet been found
                    terminalId = 0;

                    // Loop through all terminals
                    Iterator iter = doc.getRootElement().getChild("TerminalDefs").getChildren().iterator();
                    while(iter.hasNext()) {
                        Element et = (Element)iter.next();
                        String tids = et.getAttributeValue("id");

                        // Is this terminal available?
                        if(DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", tids)==null) {
                            // The string tids is valid, it is a number!
                            terminalId = Integer.parseInt(tids);
                            isRelocated = true;
                            break;
                        }
                    }

                    if(terminalId==0)
                        return RESULT_LINK_ERROR_NOT_ENOUGH_TERMINALS;
                }
                else {
                    if(terminalId==0)
                        log.error("Method linkTerminal called with invalid parameters!");
                    return RESULT_LINK_ERROR_TERMINAL_LINKED;
                }
            }

            // Player(s) already linked to other terminal?
            // Also collect all roles already assigned
            Set<Integer> roles = new TreeSet<Integer>();
            Iterator iter = doc.getRootElement().getChild("PlayerSetup").getChildren().iterator();
            while(iter.hasNext()) {
                Element ep = (Element)iter.next();
                try {
                    if(groupId==ep.getAttribute("groupid").getIntValue()) {
                        Element epid = ep.getChild("PlayerIds");
                        Iterator iter2 = epid.getChildren().iterator();
                        while(iter2.hasNext()) {
                            Element epr = (Element)iter2.next();
                            try {
                                int id = epr.getAttribute("id").getIntValue();
                                for(int i=0;i<playerIdArray.length;i++) {
                                    if(id == playerIdArray[i]) {
                                        return RESULT_LINK_ERROR_PLAYER_LINKED|id;
                                    }
                                }
                            } catch (DataConversionException ex) {
                                log.error("Attribute id on PlayerIds is not a number",ex);
                            }
                        }
                        Element eprs = ep.getChild("RoleSetup");
                        iter2 = eprs.getChildren().iterator();
                        while(iter2.hasNext()) {
                            Element eprr = (Element)iter2.next();
                            int rid = DomTools.getAttributeInt(eprr,"id",0,false);
                            if(rid>0)
                                roles.add(rid);
                        }
                    }
                } catch (DataConversionException ex) {
                    log.error("Attribute groupid on Player is not a number",ex);
                }
            }

            // Get all player elements in PlayerDefs section
            int nbrPlayers = playerIdArray.length;
            Element[] playerElementArray = new Element[nbrPlayers];
            for(int i=0;i<playerIdArray.length;i++) {
                playerElementArray[i] = DomTools.getElementFromSection(doc, "PlayerDefs", "id", String.valueOf(playerIdArray[i]));
                if(playerElementArray[i]==null) {
                    log.error("Player element in PlayerDefs does not exist: "+playerIdArray[i]);
                    nbrPlayers--;
                }
            }

            // Return if no players to link
            if(nbrPlayers<=0) {
                log.error("No players to link to terminal "+terminalId+"!");
                return RESULT_LINK_ERROR_NO_PLAYERS;
            }

            // Remove old player(s) from recycle bin
            while(true) {
                Element ep = DomTools.getElementFromSection(doc, "RecycleBin", "terminalid", String.valueOf(terminalId));
                if(ep==null) break;
                doc.getRootElement().getChild("RecycleBin").removeContent(ep);
            }

            // Create new player node
            Element ep = new Element("Player");
            ep.setAttribute("terminalid", String.valueOf(terminalId));
            ep.setAttribute("groupid", String.valueOf(groupId));
            ep.setAttribute("relocated", String.valueOf(isRelocated));

            // Create merged name
            Element epn = new Element("Name");
            StringBuffer sb = new StringBuffer();
            for(int i=0;i<playerElementArray.length;i++) {
                String s = "###";
                if(playerElementArray[i]!=null)
                    s = DomTools.getChildText(playerElementArray[i], "Name", "P/"+playerIdArray[i], false);
                if(s!=null) {
                    sb.append(s);
                    if(i<playerElementArray.length-1)
                        sb.append(", ");
                }
            }
            epn.setText(sb.toString());
            ep.addContent(epn);

            // Create channels
            Element ecsd = new Element("ChannelSetup");
            for(int i=0;i<playerElementArray.length;i++) {
                if(playerElementArray[i]!=null) {
                    Element ecss = playerElementArray[i].getChild("ChannelSetup");
                    if(ecss!=null) {
                        Iterator iters = ecss.getChildren("ChannelRef").iterator();
                        while(iters.hasNext()) {
                            Element ecrs = (Element)iters.next();

                            // First, check if this element already exists
                            int ids  = DomTools.getAttributeInt(ecrs,"id", 0, true);
                            int gids = DomTools.getAttributeInt(ecrs,"groupid", groupId, false);

                            Element ecrd = null;
                            Iterator iterd = ecsd.getChildren().iterator();
                            while(iterd.hasNext()) {
                                Element e = (Element)iterd.next();
                                int idd  = DomTools.getAttributeInt(e,"id", 0, true);
                                int gidd = DomTools.getAttributeInt(e,"groupid", groupId, false);
                                if((ids==idd)&&(gids==gidd)) {
                                    ecrd = e;
                                    break;
                                }
                            }

                            // Element exists, merge attributes
                            if(ecrd!=null) {

                                // Merge state
                                if(hasStateOr(ecrs,ecrd,"state")) {
                                    String states = DomTools.getAttributeString(ecrs, "state", "off", false);
                                    String stated = DomTools.getAttributeString(ecrd, "state", "off", false);
                                    if(states.equals("rxtx"))
                                        ecrd.setAttribute("state", "rxtx");
                                    else if(states.equals("rx")&&stated.equals("off"))
                                        ecrd.setAttribute("state", "rx");
                                    else
                                        ecrd.setAttribute("state", "off");
                                }

                                // Merge locked
                                if(hasStateOr(ecrs,ecrd,"locked")) {
                                    String lockeds = DomTools.getAttributeString(ecrs, "locked", "false", false);
                                    String lockedd = DomTools.getAttributeString(ecrd, "locked", "false", false);
                                    if(lockeds.equals("false")||lockedd.equals("false"))
                                        ecrd.setAttribute("locked", "false");
                                    else
                                        ecrd.setAttribute("locked", "true");
                                }

                                // Merge hidden
                                if(hasStateOr(ecrs,ecrd,"hidden")) {
                                    String hiddens = DomTools.getAttributeString(ecrs, "hidden", "false", false);
                                    String hiddend = DomTools.getAttributeString(ecrd, "hidden", "false", false);
                                    if(hiddens.equals("false")||hiddend.equals("false"))
                                        ecrd.setAttribute("hidden", "false");
                                    else
                                        ecrd.setAttribute("hidden", "true");
                                }

                                // Merge recordable
                                if(hasStateOr(ecrs,ecrd,"recordable")) {
                                    String recs = DomTools.getAttributeString(ecrs, "recordable", "false", false);
                                    String recd = DomTools.getAttributeString(ecrd, "recordable", "false", false);
                                    if(recs.equals("false")&&recd.equals("false"))
                                        ecrd.setAttribute("recordable", "false");
                                    else
                                        ecrd.setAttribute("recordable", "true");
                                }
                                
                                // Merge auto record
                                if(hasStateOr(ecrs,ecrd,"autorec")) {
                                    String recs = DomTools.getAttributeString(ecrs, "autorec", "false", false);
                                    String recd = DomTools.getAttributeString(ecrd, "autorec", "false", false);
                                    if(recs.equals("false")&&recd.equals("false"))
                                        ecrd.setAttribute("autorec", "false");
                                    else
                                        ecrd.setAttribute("autorec", "true");
                                }                                

                                // Merge monitor
                                if(hasStateOr(ecrs,ecrd,"monitor")) {
                                    boolean mons = DomTools.getAttributeBoolean(ecrs, "monitor", false, false);
                                    boolean mond = DomTools.getAttributeBoolean(ecrd, "monitor", false, false);
                                    ecrd.setAttribute("monitor", String.valueOf(mons||mond));
                                }

                                // Merge showgroup
                                if(hasStateOr(ecrs,ecrd,"showgroup")) {
                                    String sgs = DomTools.getAttributeString(ecrs, "showgroup", "false", false);
                                    String sgd = DomTools.getAttributeString(ecrd, "showgroup", "false", false);
                                    if(sgs.equals("false")&&sgd.equals("false"))
                                        ecrd.setAttribute("showgroup", "false");
                                    else
                                        ecrd.setAttribute("showgroup", "true");
                                }
                            }

                            // Element does not exist, clone it
                            else
                                ecsd.addContent((Element)ecrs.clone());
                        }
                    }
                }
            }
            ep.addContent(ecsd);

            // Check if any of the channels has a monitor
            iter = ecsd.getChildren("ChannelRef").iterator();
            while(iter.hasNext()) {
                Element ecr = (Element)iter.next();
                int cid = DomTools.getAttributeInt(ecr,"id",0,true);
                Element ec = DomTools.getElementFromSection(doc,"ChannelDefs","id",String.valueOf(cid));
                if(ec!=null) {
                    // Check both Channel in ChannelDefs and ChannelRef in ChannelSetup for the monitor attribute
                    if(DomTools.getPrioritizedAttribute("monitor", "false", ecr, ec).equals("true")) {
                        // Find the groupid depending on priority
                        int gid = DomTools.getPrioritizedAttribute("groupid", groupId, ecr, ec);
                        // Store monitoring terminal id
                        monitoringTerminalId = terminalId;
                        // Calculate channel id for the monitor
                        monitorChannelId = (gid<<ID_BITSHIFT) | cid;
                        if(log.isDebugEnabled())
                            log.debug("Monitor - Setting monitoring terminal "+terminalId+" (channel "+cid+", group "+gid+")");
                        monitorStart();
                        break;
                    }
                }
            }

            // Create roles
            Element ersd = new Element("RoleSetup");
            for(int i=0;i<playerElementArray.length;i++) {
                if(playerElementArray[i]!=null) {
                    Element erss = playerElementArray[i].getChild("RoleSetup");
                    if(erss!=null) {
                        Iterator iters = erss.getChildren("RoleRef").iterator();
                        while(iters.hasNext()) {
                            Element errs = (Element)iters.next();

                            int ids  = DomTools.getAttributeInt(errs,"id", 0, false);

                            Element errd = null;
                            if(ids>0) {
                                Iterator iterd = ersd.getChildren().iterator();
                                while(iterd.hasNext()) {
                                    Element e = (Element)iterd.next();
                                    int idd  = DomTools.getAttributeInt(e,"id", 0, false);
                                    if(ids==idd) {
                                        errd = e;
                                        break;
                                    }
                                }
                            }

                            // Element does not exist, clone it
                            if(errd==null) {
                                // Allow rolerefs with id 0 (empty slots) if only one
                                // player is linked. Merged players will result in
                                // undefined slot posistions
                                if(ids>0||playerElementArray.length==1) {
                                    errd = (Element)errs.clone();
                                    ersd.addContent(errd);
                                }
                            }

                            // Here we have errd (RoleRef), calculate uid (unique id)
                            if(ids>0&&errd!=null) {
                                Element elemLow = DomTools.getElementFromSection(doc,"RoleDefs","id",String.valueOf(ids));
                                int gid = DomTools.getPrioritizedAttribute("groupid", groupId, errd, elemLow);
                                errd.setAttribute("uid", String.valueOf((gid<<ID_BITSHIFT) | ids));
                            }
                        }
                    }
                }
            }

            // Role already played by another player?
            iter = ersd.getChildren().iterator();
            while(iter.hasNext()) {
                Element err = (Element)iter.next();
                int rid = DomTools.getAttributeInt(err,"id", 0, false);
                if(rid>0 && roles.contains(rid))
                    return RESULT_LINK_ERROR_ROLE_LINKED|rid;
            }

            // If not, add RoleSetup node
            ep.addContent(ersd);

            // Add playerId's
            Element epid = new Element("PlayerIds");
            for(int i=0;i<playerIdArray.length;i++) {
                Element epr = new Element("PlayerRef");
                epr.setAttribute("id", String.valueOf(playerIdArray[i]));
                epid.addContent(epr);
            }
            ep.addContent(epid);

            // Add Player to PlayerSetup
            doc.getRootElement().getChild("PlayerSetup").addContent(ep);

            // Debug output
            if(Config.SERVER_WRITE_PLAYERSETUP) {
                String fname = "data/development/server_playersetup.xml";
                XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());
                try {
                    Writer wr = new FileWriter(fname);
                    xmlo.output(doc.getRootElement().getChild("PlayerSetup"), wr);
                    wr.close();
                } catch (IOException ex) {
                    log.error("IOException when trying to write model to file "+fname+"!", ex);
                }
                log.info("PlayerSetup successfully written to file "+fname);
            }
        }

        return RESULT_LINK_OK;
    }

    private boolean unlinkTerminal(int terminalId) {

        synchronized(doc) {
            // Remove all
            if(terminalId==ALL) {
                List content = doc.getRootElement().getChild("PlayerSetup").removeContent();
                doc.getRootElement().getChild("RecycleBin").addContent(content);
            }

            // Remove one
            else {
                Element ep = DomTools.getElementFromSection(doc,"PlayerSetup", "terminalid", String.valueOf(terminalId));
                if(ep!=null) {
                    doc.getRootElement().getChild("PlayerSetup").removeContent(ep);
                    doc.getRootElement().getChild("RecycleBin").addContent(ep);
                }
                else return false;
            }

            // Stop monitor
            if(terminalId==ALL || terminalId==monitoringTerminalId) {
                log.debug("Monitor - Removing monitoring terminal "+monitoringTerminalId);
                monitorStop();
                monitoringTerminalId = 0;
            }
        }
        return true;
    }

    private void unlinkTerminal(Element playerElement) {
        if(playerElement!=null) {
            synchronized(doc) {
                if(doc.getRootElement().getChild("PlayerSetup").removeContent(playerElement)) {

                    // Stop monitor
                    int terminalId = DomTools.getAttributeInt(playerElement, "terminalid", 0, true);
                    if(terminalId>0 && terminalId==monitoringTerminalId) {
                        log.debug("Monitor - Removing monitoring terminal "+monitoringTerminalId);
                        monitorStop();
                        monitoringTerminalId = 0;
                    }

                    doc.getRootElement().getChild("RecycleBin").addContent(playerElement);
                }
            }
        }
    }

    private boolean swapTerminals(int terminalId1, int terminalId2) {

        // Dont do anything if same terminal is selected
        if(terminalId1==terminalId2)
            return false;

        synchronized(doc) {

            Element ep1 = DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", String.valueOf(terminalId1));
            Element ep2 = DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", String.valueOf(terminalId2));

            // Dont do anything if neither of the selected terminals has players
            if(ep1==null&&ep2==null)
                return false;

            // Swap the two players
            if(ep1!=null&&ep2!=null) {

                // Lock all other functions while swapping
                isSwapping = true;

                // Clone both players
                swapPlayer1 = (Element)ep1.clone();
                swapPlayer2 = (Element)ep2.clone();

                // Swap the terminal ids
                swapPlayer1.setAttribute("terminalid", String.valueOf(terminalId2));
                swapPlayer2.setAttribute("terminalid", String.valueOf(terminalId1));

                // Remove relocated flag
                swapPlayer1.setAttribute("relocated", "false");
                swapPlayer2.setAttribute("relocated", "false");

                // Store monitoring terminal
                int                                          newMonitoringTerminal = 0;
                if     (terminalId1 == monitoringTerminalId) newMonitoringTerminal = terminalId2;
                else if(terminalId2 == monitoringTerminalId) newMonitoringTerminal = terminalId1;

                // Throw both players on the recycle bin
                unlinkTerminal(terminalId1);
                unlinkTerminal(terminalId2);

                // Update monitor
                if(newMonitoringTerminal>0) {
                    log.debug("Monitor - Setting monitoring terminal "+newMonitoringTerminal);
                    monitoringTerminalId = newMonitoringTerminal;

                    // Do not allow monitoring self
                    if(monitoringTerminalId == monitoredTerminalId) {
                        log.warn("Unable to keep monitored terminal, trying to monitor self!");
                        setTerminalFlag(monitoredTerminalId, "monitored", false);
                        monitoredTerminalId = 0;
                    }
                    else {
                        monitorStart();
                    }
                }

                // Create a re-linker thread
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(DELAY_SWAP);
                            synchronized(doc) {

                                Element eps = doc.getRootElement().getChild("PlayerSetup");
                                Element erb = doc.getRootElement().getChild("RecycleBin");

                                eps.addContent(swapPlayer1);
                                eps.addContent(swapPlayer2);

                                // Remove old from recycle bin
                                Element ep;
                                ep = DomTools.getElementFromSection(doc, "RecycleBin", "terminalid",
                                        DomTools.getAttributeString(swapPlayer1, "terminalid", "0", true));
                                if(ep!=null)
                                    erb.removeContent(ep);
                                ep = DomTools.getElementFromSection(doc, "RecycleBin", "terminalid",
                                        DomTools.getAttributeString(swapPlayer2, "terminalid", "0", true));
                                if(ep!=null)
                                    erb.removeContent(ep);
                            }

                            // Update terminal states
                            updateTerminals(ALL,ALL);

                            // Wait until finished before updating view
                            Thread.sleep(DELAY_START+DELAY_START_VARIANCE);
                            updateView();

                        } catch(Exception ex) {
                            log.error("Unable to swap, re-linker thread failed", ex);
                        }

                        isSwapping = false;
                        swapPlayer1 = null;
                        swapPlayer2 = null;
                    }
                }, "Tlink");

                t.start();
            }

            // Move player 1->2
            else {

                // Swap if direction is 2->1
                if(ep1==null) {
                    Element e = ep1;
                    ep1 = ep2;
                    ep2 = e;
                    int t = terminalId1;
                    terminalId1 = terminalId2;
                    terminalId2 = t;
                }

                // Must make a copy of the source tree to put in the recycle bin
                Element epcopy = (Element)ep1.clone();
                doc.getRootElement().getChild("RecycleBin").addContent(epcopy);

                // The original tree is used with the destination terminal
                ep1.setAttribute("terminalid", String.valueOf(terminalId2));

                // Remove relocated flag
                ep1.setAttribute("relocated", "false");

                // Update monitor
                if(terminalId1 == monitoringTerminalId) {
                    if(log.isDebugEnabled())
                        log.debug("Monitor - Moving monitoring terminal from "+monitoringTerminalId+" to "+terminalId2);
                    monitorStop();
                    monitoringTerminalId = terminalId2;

                    // Do not allow monitoring self
                    if(monitoringTerminalId == monitoredTerminalId) {
                        log.warn("Unable to keep monitored terminal, trying to monitor self!");
                        setTerminalFlag(monitoredTerminalId, "monitored", false);
                        monitoredTerminalId = 0;
                    }
                    else {
                        monitorStart();
                    }
                }
            }
        }

        return true;
    }

    private void sendRoleActivitiyPacket(ServerTerminal destTerminal, ServerTerminal sourceTerminal) {

        if(destTerminal==null||sourceTerminal==null) return;

        Packet p = networkManager.prepareInfoPacket(destTerminal, sourceTerminal);
        if(p==null) return;

        if(log.isDebugEnabled())
            log.debug("Sending role activitiy packet from "+sourceTerminal.getId()+" to "+ destTerminal.getId());

        synchronized(doc) {
            String id_s = String.valueOf(sourceTerminal.getId());

            // Try to get the element from playersetup
            Element ep = DomTools.getElementFromSection(doc,"PlayerSetup", "terminalid", id_s);

            // Look in the recycle bin if not found in PlayerSetup
            if(ep==null)
                ep = DomTools.getElementFromSection(doc,"RecycleBin", "terminalid", id_s);

            if(ep!=null) {
                boolean available = sourceTerminal.isStarted();
                boolean incall    = sourceTerminal.isInCall();
                int n=0, mask=0;

                // Build bitmask
                if(available) mask |= Packet.ATTR_ROLE_ACTIVITY_FLAG_AVAILABLE;
                if(incall)    mask |= Packet.ATTR_ROLE_ACTIVITY_FLAG_INCALL;

                // Build array with roles from source terminal, there might be empty_slot's
                List lrr = ep.getChild("RoleSetup").getChildren();
                int[] uidarray = new int[lrr.size()];

                Iterator iter = lrr.iterator();
                while(iter.hasNext()) {
                    Element err = (Element)iter.next();
                    int uid = DomTools.getAttributeInt(err,"uid",0,false);
                    if(uid>0) {
                        if(log.isDebugEnabled())
                            log.debug("  - uid: "+uid+" online: "+available+", incall: "+incall);
                        uidarray[n++] = mask | uid;
                    }
                }
                p.addAttributeList(Packet.ATTR_ROLE_ACTIVITY, uidarray, n);

                networkManager.sendInfoPacket(p);
            }
            else {
                log.error("Unable to send role activitiy packet! Player node not found!");
            }
        }
    }

    private void updatePhoneLineStatus(ServerTerminal sourceTerminal, ServerTerminal destTerminal) {
        int sourceTerminalId = (sourceTerminal!=null)?sourceTerminal.getId():0;
        int destTerminalId = (destTerminal!=null)?destTerminal.getId():0;

        Collection<ServerTerminal> terminalCollection = bundle.getTerminalCollection();
        Iterator<ServerTerminal> iter = terminalCollection.iterator();
        while(iter.hasNext()) {
            ServerTerminal terminal = iter.next();
            int terminalId = terminal.getId();
            if((terminalId!=sourceTerminalId) &&
               (terminalId!=destTerminalId) &&
                terminal.isStarted() &&
                terminal.isConnected()) {
                sendRoleActivitiyPacket(terminal, sourceTerminal);
                sendRoleActivitiyPacket(terminal, destTerminal);
            }
        }
    }

    private void menuChoiceLoadConfiguration() {
        if(isSwapping) return;
        log.info("Menu: Load configuration");
        JFileChooser fc = new JFileChooser("data/configurations");
        fc.setDialogTitle("Load configuration...");
        fc.setMultiSelectionEnabled(false);
        fc.setFileFilter(new FileFilter() {
            public boolean accept(File f) {
                return !f.isHidden()&&(f.isDirectory()||f.getName().endsWith(".xml"));
            }
            public String getDescription() {
                return "Configuration (*.xml)";
            }
        });

        int returnVal = JFileChooser.APPROVE_OPTION;
        if(Config.SERVER_AUTOLOAD_CONFIGURATION==null)
            returnVal = fc.showOpenDialog(frame);

        if(returnVal==JFileChooser.APPROVE_OPTION) {
            File file;
            if(Config.SERVER_AUTOLOAD_CONFIGURATION==null)
                file = fc.getSelectedFile();
            else
                file = new File(Config.SERVER_AUTOLOAD_CONFIGURATION);
            log.info("Loading configuration "+file);
            if(file.exists()) {
                if(buildConfigurationDocument(file)) {
                    isConfigLoaded = true;
                    updateView();
                }
                else JOptionPane.showMessageDialog(frame,"Invalid configuration file! Make sure that all required tags are defined!","Error!",JOptionPane.ERROR_MESSAGE);
            }
            else JOptionPane.showMessageDialog(frame,"Unable to load configuration! File not found!","Error!",JOptionPane.ERROR_MESSAGE);
        }
    }

    public void menuChoiceLoadExercise() {
        if(isSwapping) return;
        log.info("Menu: Load exercise");
        JFileChooser fc = new JFileChooser("data/exercises");
        fc.setDialogTitle("Load exercise...");
        fc.setMultiSelectionEnabled(false);
        fc.setFileFilter(new FileFilter() {
            public boolean accept(File f) {
                return !f.isHidden()&&(f.isDirectory()||f.getName().endsWith(".xml"));
            }
            public String getDescription() {
                return "Exercise (*.xml)";
            }
        });

        int returnVal = JFileChooser.APPROVE_OPTION;
        if(Config.SERVER_AUTOLOAD_EXERCISE==null)
            returnVal = fc.showOpenDialog(frame);

        if(returnVal==JFileChooser.APPROVE_OPTION) {
            //File file;
            if(Config.SERVER_AUTOLOAD_EXERCISE==null)
                exerciseFile = fc.getSelectedFile();
            else
                exerciseFile = new File(Config.SERVER_AUTOLOAD_EXERCISE);
            log.info("Loading exercise "+exerciseFile);
            if(exerciseFile.exists()) {
                loadExercise(exerciseFile);
            }
            else JOptionPane.showMessageDialog(frame,"Unable to load exercise! File not found!","Error!",JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadExercise(File file) {
        try {
            SAXBuilder saxb = new SAXBuilder();
            Document linkdoc = saxb.build(file);

            Element eroot = linkdoc.getRootElement();
            if(eroot.getName().equals("Exercise")) {

                // Ok, read document
                Iterator iter = linkdoc.getRootElement().getChildren("TerminalLink").iterator();

                int selectedGroup = -1;
                StringBuffer errMsg = new StringBuffer();
                while(iter.hasNext()) {
                    Element epl = (Element)iter.next();

                    // Get the terminal id (param id or terminalid),
                    // Allow zero meaning always relocate
                    int tid = DomTools.getAttributeInt(epl,"id", 0, false);
                    if(tid==0) tid = DomTools.getAttributeInt(epl,"terminalid", 0, false);

                    // Get the group id (param groupid)
                    // Allow zero meaning select group in a dialog input box
                    int gid = DomTools.getAttributeInt(epl,"groupid", 0, false);
                    if(gid==0) {
                        // Show group dialog only once
                        if(selectedGroup==-1) {
                            GroupSelectDialog dlg = new GroupSelectDialog(frame, doc, "Select group...");
                            selectedGroup = dlg.showDialog();
                        }

                        // All following players without given group id joins this selected group
                        // If pressed cancel in the dialog box, no terminals without given group
                        // id will be linked
                        gid = selectedGroup;
                    }

                    if(tid>=0&&gid>0) {
                        List list = epl.getChildren("PlayerRef");
                        int[] pids = new int[list.size()];
                        Iterator iter2 = list.iterator();
                        int i=0;
                        while(iter2.hasNext()) {
                            Element epr = (Element)iter2.next();
                            pids[i++] = DomTools.getAttributeInt(epr,"id", 0, true);
                        }

                        int res = linkTerminal(tid,gid,pids,true);

                        if(res>0) {
                            int id = res&ID_MASK_PLAYER;
                            synchronized(doc) {
                                if((res&RESULT_LINK_ERROR_PLAYER_LINKED)!=0) {
                                    String pname = DomTools.getChildText(DomTools.getElementFromSection(doc,"PlayerDefs","id", String.valueOf(id)),"Name","P/"+id,false);
                                    errMsg.append("Unable to link: Player ");
                                    errMsg.append(pname);
                                    errMsg.append(" (id=");
                                    errMsg.append(id);
                                    errMsg.append(") is already linked to another terminal!\n");
                                }
                                else {
                                    String rname = DomTools.getChildText(DomTools.getElementFromSection(doc,"RoleDefs","id", String.valueOf(id)),"Name","R/"+id,false);
                                    errMsg.append("Unable to link: Role ");
                                    errMsg.append(rname);
                                    errMsg.append(" (id=");
                                    errMsg.append(id);
                                    errMsg.append(") is already linked to another terminal!\n");
                                }
                            }
                        }
                        else if(res==RESULT_LINK_ERROR_TERMINAL_LINKED) {
                            String tname = DomTools.getChildText(DomTools.getElementFromSection(doc,"TerminalDefs","id", String.valueOf(tid)),"Name","T/"+tid,false);
                            errMsg.append("Unable to link: Terminal ");
                            errMsg.append(tname);
                            errMsg.append(" (id=");
                            errMsg.append(tid);
                            errMsg.append(") is in use by another player!\n");
                        }
                        else if(res==RESULT_LINK_ERROR_NO_PLAYERS) {
                            errMsg.append("Unable to link: Player(s) missing in configuration or not defined in exercise!\n");
                        }
                        else if(res==RESULT_LINK_ERROR_NOT_ENOUGH_TERMINALS) {
                            for(int pid : pids) {
                                String pname = DomTools.getChildText(DomTools.getElementFromSection(doc,"PlayerDefs","id", String.valueOf(pid)),"Name","P/"+pid,false);
                                errMsg.append("Unable to link: There is no available terminal for player ");
                                errMsg.append(pname);
                                errMsg.append(" (id=");
                                errMsg.append(pid);
                                errMsg.append(")!\n");
                            }
                        }
                    }
                }
                updateTerminals(ALL,ALL);
                updateView();
                if(errMsg.length()>0) {
                    String e = errMsg.toString().trim();
                    JOptionPane.showMessageDialog(frame,e,"Error!",JOptionPane.ERROR_MESSAGE);
                    log.warn("Warnings when loading exercise file:\n" + e);
                }
            }
            else {
                JOptionPane.showMessageDialog(frame,"Unable to load exercise! Invalid root element!","Error!",JOptionPane.ERROR_MESSAGE);
                log.error("Unable to load exercise! Invalid root element!");
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame,"Unable to load exercise! Syntax error!","Error!",JOptionPane.ERROR_MESSAGE);
            log.error("Unable to load exercise!", ex);
        }
    }

    private void menuChoiceServerStart() {
        if(isSwapping) return;
        log.info("Menu: Starting server");
        try {
            // Create a HTTP server for information exchange
            httpServer = new Server();

            // Setup velocity
            Velocity.init();

            // Read the property files
            Properties serverProperties = new Properties();
            serverProperties.loadFromXML(new FileInputStream("data/properties/serverproperties.xml"));
            logEvents = serverProperties.getProperty("LogEvents","false").equalsIgnoreCase("true");
            if(logEvents) log.info("Enabling event logging");

            // Setup HTTP server
            int httpPort = Integer.parseInt(serverProperties.getProperty("HttpPort","36600"));
            httpConnector = new SelectChannelConnector();
            httpConnector.setPort(httpPort);
            httpServer.setConnectors(new Connector[] {httpConnector});

            // Dynamic context handler for the xml data provider
            ContextHandler xmlContextHandler = new ContextHandler();
            xmlContextHandler.setContextPath("/xml");
            Handler xmlHandler = new InfoRequestHandler(this, log);
            xmlContextHandler.setHandler(xmlHandler);
            httpServer.setHandlers(new Handler[] {xmlContextHandler});

            // Create a bundle
            bundle = new ServerBundle(doc);

            // Create server logger map containing all available loggers
            loggerMap = Collections.synchronizedMap(new TreeMap<Integer,ServerLogger>());

            // Start the UDP server
            int port = Integer.parseInt(serverProperties.getProperty("UdpPort","36604"));
            networkManager = new ServerNetworkManager(port, bundle, this);

            networkManager.setNetworkHandler(this);

            isaTracePainter = serverProperties.getProperty("ISATracePainter","line");
            //panel.resetIsaChart();

        } catch(Exception ex) {
            log.error("Unable to start server! ", ex);
            JOptionPane.showMessageDialog(frame,"Unable to start server! Check the validity of the configuration file, the serverproperties.xml and\nthe networkproperties.ini files!  Also make sure that no other server is already running!","Error!",JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            serverStartedDate = null;

            networkManager.start();
            serverStartedDate = new Date();

            // Start the HTTP server
            httpServer.start();

            // Tell it to the view to show an icon
            panel.setServerStartedDate(serverStartedDate);


        } catch(Exception ex) {
            log.error("Unable to start server!", ex);
            JOptionPane.showMessageDialog(frame,"Unable to start server! Check the validity of the configuration file, the serverproperties.xml and\nthe networkproperties.ini files!  Also make sure that no other server is already running!","Error!",JOptionPane.ERROR_MESSAGE);
            // Network manager has been started, stop it again!
            if(serverStartedDate!=null) {
                networkManager.stop();
                serverStartedDate = null;
            }
        }
    }

    private void menuChoiceServerStop(boolean exiting) {
        if(isSwapping) return;
        log.info("Menu: Stop server");
        int r = JOptionPane.YES_OPTION;
        String s = "Are you sure you want to stop the server? All terminals will be unlinked!";
        if(!exiting)
            r = JOptionPane.showConfirmDialog(frame,s,"Stop?",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);

        if(r==JOptionPane.YES_OPTION) {

            log.info("Stopping server");

            isaStop(0);

            // Unlink all terminals
            unlinkTerminal(ALL);

            // Stop all groups
            synchronized(doc) {
                Iterator iter = doc.getRootElement().getChild("GroupDefs").getChildren().iterator();
                while(iter.hasNext()) {
                    Element eg = (Element)iter.next();
                    eg.setAttribute("state", "stopped");
                }
            }

            // Set all terminals to idle state
            updateTerminals(ALL,ALL);
            updateView();

            // Wait before closing terminals
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {}

            // Close all terminals
            synchronized(doc) {
                Iterator iter = doc.getRootElement().getChild("TerminalDefs").getChildren().iterator();
                while(iter.hasNext()) {
                    Element et = (Element)iter.next();
                    if(DomTools.getAttributeBoolean(et, "online", false, false)) {
                        int tid = DomTools.getAttributeInt(et,"id",0,true);
                        et.setAttribute("online", "false");
                        log.info("Sending close to terminal "+tid);
                        networkManager.sendSessionRequestClose(tid);
                    }
                }
            }
            updateView();

            // Close server
            try {
                Thread.sleep(250);
                networkManager.stop();
                Thread.sleep(250);
                httpServer.stop();
                httpConnector.close();
                Thread.sleep(250);
            } catch (Exception ex) {
                log.warn("Stop server failed!", ex);
            }

            panel.setServerStartedDate(null);
            serverStartedDate = null;
        }
    }

    /**
     * Method used for a quick restart of a running server.
     * The server is stopped, started, the last used exercise file is loaded
     * and the groups that where running when this method was called are started again.
     */
    private void serverRestart() {
        log.info("Restart server");
        List<Integer> activeGroups = new ArrayList<Integer>();
        synchronized(doc) {
            Element egd = doc.getRootElement().getChild("GroupDefs");
            Iterator iter = egd.getChildren().iterator();
            Element eg;
            Integer gid;
            while(iter.hasNext()) {
                eg = (Element)iter.next();
                gid = new Integer(eg.getAttributeValue("id"));
                if (gid != null &&
                                eg.getAttribute("state") != null &&
                                eg.getAttributeValue("state").equalsIgnoreCase("started")) {
                        activeGroups.add(gid);
                }
            }
        }

        log.info("Stopping server");
        menuChoiceServerStop(true);

                log.info("Start server");
                menuChoiceServerStart();

                if (exerciseFile != null) {
                        log.info("Load exercise file " + exerciseFile.getName());
                        loadExercise(exerciseFile);
                }

                for (int i = 0; i < activeGroups.size(); i++) {
                        log.info("Start group " + activeGroups.get(i));
                        menuChoiceGroupStart(activeGroups.get(i).intValue(), false, null);
                }
                log.info("Server restart done");
    }

    private void menuChoiceNetworkMonitor() {
        if (networkStatusTimer != null) {
                networkStatusTimer.cancel();
                networkStatusTimer = null;
                log.debug("Stopped network connection monitoring");
        } else {
                networkStatusTimer = new Timer();
                networkStatusTimer.schedule(
                                new TimerTask() {
                                        public void run() {
                                                if (serverStartedDate != null) {
                                                        log.debug("Check network connection status");
                                                }
                                                if (serverStartedDate != null && networkManager.connectionProblem()) {
                                                        log.error("Possible network connection problem detected, restart server!");
                                                        try {
                                                                serverRestart();
                                                        } catch (Exception e) {
                                                                e.printStackTrace();
                                                        }
                                                }
                                        }
                                }, 30000, 5000);
                log.debug("Started network connection monitoring");
        }
        itemServerMonitor.setSelected(networkStatusTimer != null);
    }

    public boolean menuChoiceTerminalLink(int terminalId) {
        if(isSwapping) return false;
        log.info("Menu: Link terminal");
        TerminalLinkDialog dlg = new TerminalLinkDialog(frame, doc, terminalId);
        if(dlg.showDialog()) {
            int tid = dlg.getSelectedTerminalId();
            int gid = dlg.getSelectedGroupId();
            int res = linkTerminal(tid, gid, dlg.getSelectedPlayerIdArray(),true);
            if(res>0) {
                synchronized(doc) {
                    int id = res&ID_MASK_PLAYER;
                    if((res&RESULT_LINK_ERROR_PLAYER_LINKED)!=0) {
                        String pname = DomTools.getChildText(DomTools.getElementFromSection(doc,"PlayerDefs","id", String.valueOf(id)),"Name","P/"+id,false);
                        JOptionPane.showMessageDialog(frame,"Unable to link: Player "+pname+" (id="+id+") is already linked to another terminal!","Error!",JOptionPane.ERROR_MESSAGE);
                    }
                    else {
                        String rname = DomTools.getChildText(DomTools.getElementFromSection(doc,"RoleDefs","id", String.valueOf(id)),"Name","R/"+id,false);
                        JOptionPane.showMessageDialog(frame,"Unable to link: Role "+rname+" (id="+id+") is already linked to another terminal!","Error!",JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
            else if(res==RESULT_LINK_ERROR_TERMINAL_LINKED) {
                String tname = DomTools.getChildText(DomTools.getElementFromSection(doc,"TerminalDefs","id", String.valueOf(tid)),"Name","T/"+tid,false);
                JOptionPane.showMessageDialog(frame,"Unable to link: Terminal "+tname+" (id="+tid+") is in use by another player!","Error!",JOptionPane.ERROR_MESSAGE);
            }
            else if(res==RESULT_LINK_ERROR_NO_PLAYERS) {
                JOptionPane.showMessageDialog(frame,"Unable to link: No player(s) selected!","Error!",JOptionPane.ERROR_MESSAGE);
            }
            else if(res==RESULT_LINK_ERROR_NOT_ENOUGH_TERMINALS) {
                int[] pids = dlg.getSelectedPlayerIdArray();
                String errMsg = "";
                for(int pid : pids) {
                    String pname = DomTools.getChildText(DomTools.getElementFromSection(doc,"PlayerDefs","id", String.valueOf(pid)),"Name","P/"+pid,false);
                    errMsg += "Unable to link: There is no available terminal for player "+pname+" (id="+pid+")!\n";
                }
                JOptionPane.showMessageDialog(frame,errMsg,"Error!",JOptionPane.ERROR_MESSAGE);
            }
            else {
                updateTerminals(tid,gid);
                updateView();
            }
            return (res==RESULT_LINK_OK);
        }
        return false;
    }

    public boolean menuChoiceTerminalUnlink(int terminalId) {
        if(isSwapping) return false;
        log.info("Menu: Unlink terminal");
        if(terminalId==0) {
            TerminalUnlinkDialog dlg = new TerminalUnlinkDialog(frame, doc);
            terminalId = dlg.showDialog();
        }
        if(terminalId>0) {
            if(unlinkTerminal(terminalId)) {
                updateTerminals(terminalId,ALL);
                updateView();
                return true;
            }
            else
                JOptionPane.showMessageDialog(frame,"The selected terminal is not linked to any player(s)!","Info!",JOptionPane.INFORMATION_MESSAGE);
        }

        return false;
    }

    public boolean menuChoiceTerminalUnlink(List<Integer> terminalIds) {
        if(isSwapping) return false;
        log.info("Menu: Unlink terminals");

        int nbrTerminals = terminalIds.size();
        if(nbrTerminals>1) {
            int r = JOptionPane.showConfirmDialog(frame,"Are you sure you want to unlink "+nbrTerminals+" terminals?","Unlink?",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE);
            if(r!=JOptionPane.YES_OPTION)
                return false;
        }

        boolean update = false;
        Iterator<Integer> iteri = terminalIds.iterator();
        while(iteri.hasNext()) {
            boolean b = unlinkTerminal(iteri.next().intValue());
            update = update || b;
        }

        if(update) {
            updateTerminals(ALL,ALL);
            updateView();
        }

        return update;
    }

    private void menuChoiceTerminalUnlinkAll() {
        if(isSwapping) return;
        log.info("Menu: Unlink all terminals");
        int r = JOptionPane.showConfirmDialog(frame,"Are you sure you want to unlink all terminals?","Unlink all?",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE);
        if(r==JOptionPane.YES_OPTION) {
            unlinkTerminal(ALL);
            updateTerminals(ALL,ALL);
            updateView();
        }
    }

    public boolean menuChoiceTerminalUnlinkGroup(int groupId) {
        if(isSwapping) return false;
        log.info("Menu: Unlink terminals in group");

        String name = "G/"+groupId;
        synchronized(doc) {
            Element eg = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(groupId));
            name = DomTools.getChildText(eg, "Name", "G/"+groupId, false);
        }

        int r = JOptionPane.showConfirmDialog(frame,"Are you sure you want to unlink all terminals in group "+name+"?","Unlink group?",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE);

        if(r==JOptionPane.YES_OPTION) {

            boolean update = false;
            synchronized(doc) {
                while(true) {
                    Element ep = DomTools.getElementFromSection(doc, "PlayerSetup", "groupid", String.valueOf(groupId));
                    if(ep==null) break;
                    unlinkTerminal(ep);
                    update = true;
                }
            }
            if(update) {
                updateTerminals(ALL,ALL);
                updateView();
                return true;
            }
        }

        return false;
    }

    public void menuChoiceTerminalSwap(int terminalId1, int terminalId2) {
        if(isSwapping) return;
        log.info("Menu: Swap terminals");
        if(terminalId1==0&&terminalId2==0) {
            TerminalSwapDialog dlg = new TerminalSwapDialog(frame, doc);
            if(dlg.showDialog()) {
                int[] tids = dlg.getSelectedTerminals();
                terminalId1 = tids[0];
                terminalId2 = tids[1];
            }
        }

        if(swapTerminals(terminalId1, terminalId2)) {
            updateTerminals(ALL,ALL);
            updateView();
        }
    }

    public void menuChoiceTerminalMonitor(int terminalId) {

        // Do not allow monitoring self
        if(terminalId == monitoringTerminalId) {
            log.warn("Unable to set monitored terminal, trying to monitor self!");
            return;
        }

        // Stop currently monitored terminal
        if(monitoredTerminalId!=0) {
            setTerminalFlag(monitoredTerminalId, "monitored", false);
            monitorStop();
        }

        // Start only if selected is not the same as the currently monitored
        if(terminalId != monitoredTerminalId) {
            monitoredTerminalId = terminalId;
            setTerminalFlag(monitoredTerminalId, "monitored", true);
            monitorStart();
        }
        else {
            monitoredTerminalId = 0;
        }

        updateView();
    }

    public boolean menuChoiceGroupStart(int groupId) {
        return menuChoiceGroupStart(groupId, true, null);
    }

    public boolean menuChoiceGroupStart(int groupId, boolean confirm, String timeStr) {
        if(isSwapping) return false;
        log.info("logPath =" + logPath);
        log.info("Menu: Start group time=" + timeStr);

        // Debug autostart group
        if(Config.SERVER_AUTOSTART_GROUP>0)
            groupId = Config.SERVER_AUTOSTART_GROUP;

        boolean showConfirmDialog = (groupId!=0) && confirm;

        // Select dialog if no group id specified
        if(groupId==0) {
            GroupSelectDialog dlg = new GroupSelectDialog(frame, doc, "Start group...");
            groupId = dlg.showDialog();
        }

        if(groupId>0) {
            Element eg = null;
            if(showConfirmDialog&&(Config.SERVER_AUTOSTART_GROUP==0)) {
                String name = "G/"+groupId;
                synchronized(doc) {
                    eg = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(groupId));
                    name = DomTools.getChildText(eg, "Name", "G/"+groupId, false);
                }
                int r = JOptionPane.showConfirmDialog(frame,"Are you sure you want to start group "+name+"?","Start group?",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE);
                if(r!=JOptionPane.YES_OPTION)
                    return false;
            }

            synchronized(doc) {
                if(eg==null) eg = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(groupId));
                String state = DomTools.getAttributeString(eg, "state", "stopped", false);

                if(state.equals("stopped")) {
                    if(eg!=null) {
                        eg.setAttribute("state", "started");
                        updateTerminals(ALL, groupId);
                        updateView();
                        if(logEvents) {

                            ServerLogger logger = new ServerLogger(groupId, logPath);
                            try {
                                logger.resume(Integer.parseInt(timeStr.substring(0,2)),
                                    Integer.parseInt(timeStr.substring(3,5)),
                                    Integer.parseInt(timeStr.substring(6,8)));
                            } catch (Exception e) {
                                logger.resume();
                            }

                            loggerMap.put(groupId,logger);
                            logger.print("GROUP START id["+groupId+"]");
                        }
                        return true;
                    }
                }
                else if(state.equals("paused")) {
                    eg.setAttribute("state", "started");
                    updateTerminals(ALL, groupId);
                    updateView();
                    ServerLogger logger = loggerMap.get(groupId);
                    if(logger!=null) {
                        try {
                            logger.resume(Integer.parseInt(timeStr.substring(0,2)),
                                    Integer.parseInt(timeStr.substring(3,5)),
                                    Integer.parseInt(timeStr.substring(6,8)));
                        } catch (Exception e) {
                            logger.resume();
                        }
                        logger.print("GROUP CONTINUE id["+groupId+"]");
                    }
                    return true;
                }
                else {
                    if (confirm) {
                                JOptionPane.showMessageDialog(frame,"Group is already started!","Info!",JOptionPane.INFORMATION_MESSAGE);
                    }
                    ServerLogger logger = loggerMap.get(groupId);
                    if(logger!=null) {
                        try {
                            logger.resume(Integer.parseInt(timeStr.substring(0,2)),
                                    Integer.parseInt(timeStr.substring(3,5)),
                                    Integer.parseInt(timeStr.substring(6,8)));
                        } catch (Exception e) {
                            logger.resume();
                        }
                    }
                }
            }
        }

        return false;
    }

    private void menuChoiceGroupPause() {
        menuChoiceGroupPause(-1);
    }

    private void menuChoiceGroupPause(int gid) {
        if(isSwapping) return;
        boolean confirm = (gid == -1);
        if (gid == -1) {
                log.info("Menu: Pause group");
                GroupSelectDialog dlg = new GroupSelectDialog(frame, doc, "Pause group...");
                gid = dlg.showDialog();
        }
        if(gid>0) {
            synchronized(doc) {
                Element eg = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(gid));
                String state = DomTools.getAttributeString(eg, "state", "stopped", false);

                if(state.equals("started")) {
                        isaStop(gid);
                    eg.setAttribute("state", "paused");
                    updateTerminals(ALL, gid);
                    updateView();
                    ServerLogger logger = loggerMap.get(gid);
                    if(logger!=null) {
                        logger.print("GROUP PAUSE id["+gid+"]");
                        logger.suspend();
                    }
                }
                else if(state.equals("paused")) {
                    eg.setAttribute("state", "started");
                    updateView();
                    ServerLogger logger = loggerMap.get(gid);
                    if(logger!=null) {
                        logger.resume();
                        logger.print("GROUP CONTINUE id["+gid+"]");
                    }
                }
                else {
                        if (confirm) {
                                JOptionPane.showMessageDialog(frame,"Group is not started!","Info!",JOptionPane.INFORMATION_MESSAGE);
                        }
                }
            }
        }
    }

    public boolean menuChoiceGroupStop(int groupId) {
        return menuChoiceGroupStop(groupId, true);
    }

    public boolean menuChoiceGroupStop(int groupId, boolean confirm) {
        if(isSwapping) return false;
        log.info("Menu: Stop group");

        boolean showConfirmDialog = (groupId!=0) && confirm;

        // Select dialog if no group id specified
        if(groupId==0) {
            GroupSelectDialog dlg = new GroupSelectDialog(frame, doc, "Stop group...");
            groupId = dlg.showDialog();
        }

        if(groupId>0) {
            Element eg = null;
            if(showConfirmDialog) {
                String name = "G/"+groupId;
                synchronized(doc) {
                    eg = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(groupId));
                    name = DomTools.getChildText(eg, "Name", "G/"+groupId, false);
                }
                int r = JOptionPane.showConfirmDialog(frame,"Are you sure you want to stop group "+name+"?","Stop group?",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE);
                if(r!=JOptionPane.YES_OPTION)
                    return false;
            }
            isaStop(groupId);
            synchronized(doc) {
                if(eg==null) eg = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(groupId));
                String state = DomTools.getAttributeString(eg, "state", "stopped", false);

                if(state.equals("started")||state.equals("paused")) {
                    eg.setAttribute("state", "stopped");
                    updateTerminals(ALL, groupId);
                    updateView();
                    ServerLogger logger = loggerMap.get(groupId);
                    if(logger!=null) {
                        logger.print("GROUP STOP id["+groupId+"]");
                        loggerMap.remove(groupId);
                        logger.close();
                    }
                    return true;
                }
                else {
                        if (confirm) {
                                JOptionPane.showMessageDialog(frame,"Group is already stopped!","Info!",JOptionPane.INFORMATION_MESSAGE);
                        }
                }
            }
        }

        return false;
    }

    public void actionPerformed(ActionEvent e) {
        JMenuItem item = (JMenuItem) e.getSource();

        // File menu
        if(item==itemLoadConfig) {
            if(serverStartedDate==null)
                menuChoiceLoadConfiguration();
            else
                JOptionPane.showMessageDialog(frame,"The server must be stopped before loading a new configuration!","Info!",JOptionPane.INFORMATION_MESSAGE);
        }
        else if(item==itemLoadExercise) {
            if(isConfigLoaded && serverStartedDate!=null) {
                menuChoiceLoadExercise();
                panel.deselectAll();
            }
            else
                JOptionPane.showMessageDialog(frame,"Unable to load exercise! Configuration is not loaded or server is not started!","Error!",JOptionPane.ERROR_MESSAGE);
        }
        else if(item==itemExit) {
            if(Config.SERVER_EXIT_DIALOG) {
                if((JOptionPane.showConfirmDialog(frame,"Are you sure you want to exit the server control panel?","Exit?",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION)) {
                    if(serverStartedDate!=null) {
                        menuChoiceServerStop(true);
                    }
                    log.info("Bye!");
                    System.exit(0);
                }
            }
            else {
                if(serverStartedDate!=null) {
                    menuChoiceServerStop(true);
                }
                log.info("Bye!");
                System.exit(0);
            }
        }

        // Server menu
        else if(item==itemServerStart) {
            if(isConfigLoaded) {
                if(serverStartedDate==null) {
                    menuChoiceServerStart();
                        itemServerRestart.setEnabled(true);
                } else
                    JOptionPane.showMessageDialog(frame,"The server is already started!","Info!",JOptionPane.INFORMATION_MESSAGE);
            }
            else JOptionPane.showMessageDialog(frame,"Unable to start server! Configuration file has not been loaded!","Error!",JOptionPane.ERROR_MESSAGE);
        }
        else if(item==itemServerStop) {
            if(isConfigLoaded) {
                if(serverStartedDate!=null) {
                    menuChoiceServerStop(false);
                    itemServerRestart.setEnabled(false);
                } else
                    JOptionPane.showMessageDialog(frame,"The server is already stopped!","Info!",JOptionPane.INFORMATION_MESSAGE);
            }
            else JOptionPane.showMessageDialog(frame,"Unable to stop server! Configuration file has not been loaded!","Error!",JOptionPane.ERROR_MESSAGE);
        }
        else if(item==itemServerRestart) {
            if(isConfigLoaded) {
                if(serverStartedDate!=null)
                    serverRestart();
                else
                    JOptionPane.showMessageDialog(frame,"The server is not running!","Info!",JOptionPane.INFORMATION_MESSAGE);
            }
            else JOptionPane.showMessageDialog(frame,"Unable to restart server! Configuration file has not been loaded!","Error!",JOptionPane.ERROR_MESSAGE);
        }
        else if(item==itemServerMonitor) {
                menuChoiceNetworkMonitor();
        }
        else if(item==itemTerminalLink) {
            if(isConfigLoaded && serverStartedDate!=null) {
                if(menuChoiceTerminalLink(0))
                    panel.deselectAll();
            }
            else
                JOptionPane.showMessageDialog(frame,"Unable to link terminal! Configuration is not loaded or server is not started!","Error!",JOptionPane.ERROR_MESSAGE);
        }
        else if(item==itemTerminalUnlink) {
            if(isConfigLoaded && serverStartedDate!=null) {
                if(menuChoiceTerminalUnlink(0))
                    panel.deselectAll();
            }
            else JOptionPane.showMessageDialog(frame,"Unable to unlink terminal! Configuration is not loaded or server is not started!","Error!",JOptionPane.ERROR_MESSAGE);
        }
        else if(item==itemTerminalUnlinkAll) {
            if(isConfigLoaded && serverStartedDate!=null) {
                menuChoiceTerminalUnlinkAll();
                panel.deselectAll();
            }
            else JOptionPane.showMessageDialog(frame,"Unable to unlink terminals! Configuration is not loaded or server is not started!","Error!",JOptionPane.ERROR_MESSAGE);
        }
        else if(item==itemTerminalSwap) {
            if(isConfigLoaded && serverStartedDate!=null) {
                menuChoiceTerminalSwap(0,0);
                panel.deselectAll();
            }
            else JOptionPane.showMessageDialog(frame,"Unable to move terminal! Configuration is not loaded or server is not started!","Error!",JOptionPane.ERROR_MESSAGE);
        }
        else if(item==itemGroupStart) {
            if(isConfigLoaded && serverStartedDate!=null) {
                if(menuChoiceGroupStart(0))
                    panel.deselectAll();
            }
            else JOptionPane.showMessageDialog(frame,"Unable to start group! Configuration is not loaded or server is not started!","Error!",JOptionPane.ERROR_MESSAGE);
        }
        else if(item==itemGroupPause) {
            if(isConfigLoaded && serverStartedDate!=null) {
                menuChoiceGroupPause();
                panel.deselectAll();
            }
            else JOptionPane.showMessageDialog(frame,"Unable to pause group! Configuration is not loaded or server is not started!","Error!",JOptionPane.ERROR_MESSAGE);
        }
        else if(item==itemGroupStop) {
            if(isConfigLoaded && serverStartedDate!=null) {
                if(menuChoiceGroupStop(0))
                    panel.deselectAll();
            }
            else JOptionPane.showMessageDialog(frame,"Unable to stop group! Configuration is not loaded or server is not started!","Error!",JOptionPane.ERROR_MESSAGE);
        }
    }

    public int translateRoleIdToTerminalId(int roleId) {
        int terminalId = 0;
        synchronized(doc) {
            Iterator iter = doc.getRootElement().getChild("PlayerSetup").getChildren().iterator();
            while(iter.hasNext()) {
                Element ep = (Element)iter.next();
                Iterator iter2 = ep.getChild("RoleSetup").getChildren().iterator();
                while(iter2.hasNext()) {
                    Element err = (Element)iter2.next();
                    if(roleId==DomTools.getAttributeInt(err, "uid", 0, false)) {
                        if(terminalId!=0) {
                            int t = DomTools.getAttributeInt(ep, "terminalid", 0, true);
                            log.warn("Role "+(roleId&ID_MASK_ROLE)+" in group "+((roleId&ID_MASK_GROUP)>>ID_BITSHIFT)+") is being played on " +
                                    "more than one terminal (Terminal "+terminalId+" and "+t+")!");
                        }
                        else
                            terminalId = DomTools.getAttributeInt(ep, "terminalid", 0, true);
                    }
                }
            }
        }
        return terminalId;
    }

    public void terminalOnline(ServerTerminal terminal) {
        synchronized(doc) {
            int tid = terminal.getId();
            Element et = DomTools.getElementFromSection(doc, "TerminalDefs", "id", String.valueOf(tid));
            if(et!=null) {
                // Update model
                et.setAttribute("online", "true");

                if((tid==monitoredTerminalId) || (tid==monitoringTerminalId))
                    monitorStart();

                updateTerminals(tid, ALL);
                updateView();
            }
            else {
                log.warn("Unknown terminal has gone online ("+tid+"), ignoring!");
            }
        }
    }

    public void terminalOffline(ServerTerminal terminal) {
        synchronized(doc) {
            Element et = DomTools.getElementFromSection(doc, "TerminalDefs", "id", String.valueOf(terminal.getId()));
            if(et!=null) {
                // Update model
                et.setAttribute("online", "false");
                updateView();
            }
            else {
                log.warn("Unknown terminal has gone offline ("+terminal.getId()+"), ignoring!");
            }
        }
        isaClients.remove(terminal.getId());
    }

    public void terminalStarted(ServerTerminal terminal) {
        Collection<ServerTerminal> terminalCollection = bundle.getTerminalCollection();
        Iterator<ServerTerminal> iter = terminalCollection.iterator();
        while(iter.hasNext()) {
            ServerTerminal t = iter.next();
            if(terminal!=t && t.isStarted() && t.isConnected()) {
                sendRoleActivitiyPacket(t, terminal);
                sendRoleActivitiyPacket(terminal, t);
            }
        }
    }

    public void terminalStopped(ServerTerminal terminal) {
        Collection<ServerTerminal> terminalCollection = bundle.getTerminalCollection();
        Iterator<ServerTerminal> iter = terminalCollection.iterator();
        while(iter.hasNext()) {
            ServerTerminal t = iter.next();
            if(terminal!=t && t.isStarted() && t.isConnected()) {
                // The terminal who stops should not get an update, timeout will occur!
                sendRoleActivitiyPacket(t, terminal);
            }
        }
    }

    public boolean isaStartStop(final int terminalId) {
                ServerTerminal t = bundle.getTerminal(terminalId);
        // Get linked player
        Element eps = DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", String.valueOf(terminalId));
        // Get group id
        final int groupId = DomTools.getAttributeInt(eps, "groupid", 0, true);
        if (t.isConnected() && t.isStarted()) {
            Element eg = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(groupId));
            String state = DomTools.getAttributeString(eg, "state", "stopped", false);
            if (state.equals("started")) {
                        log.debug("Send ISA request to terminal " + terminalId);
                        networkManager.sendIsaStartRequest(terminalId, isaPeriod, isaNumChoices, isaExtendedMode, isakeytext);
                        if (isaClients.contains(terminalId)) {
                                isaClients.remove(terminalId);
                        } else {
                                isaClients.add(terminalId);
                                return true;
                        }
            }
        } else {
                log.debug("Client " + terminalId + " not connected");
        }
        return false;
    }

    private void isaStop(final int groupId) {
        int terminalId;
        int terminalGid;
        Iterator<Integer> iter = isaClients.iterator();
        Vector<Integer> removeClients = new Vector<Integer>();
        while (iter.hasNext()) {
                terminalId = iter.next();
                if (groupId > 0) {
                        ServerTerminal t = bundle.getTerminal(terminalId);
                    // Get linked player
                    Element eps = DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", String.valueOf(terminalId));
                    // Get group id
                    terminalGid = DomTools.getAttributeInt(eps, "groupid", 0, true);
                    if (terminalGid == groupId) {
                        networkManager.sendIsaStartRequest(terminalId, isaPeriod, isaNumChoices, isaExtendedMode, isakeytext);
                        removeClients.add(terminalId);
                    }
                } else {
                        networkManager.sendIsaStartRequest(terminalId, isaPeriod, isaNumChoices, isaExtendedMode, isakeytext);
                        removeClients.add(terminalId);
                }
        }
        for (int i = 0; i < removeClients.size(); i++) {
                isaClients.remove(removeClients.get(i));
        }
    }

    public boolean isaClient(int terminalId) {
        return isaClients.contains(terminalId);
    }

    public String getIsaTracePainter() {
        return isaTracePainter;
    }

    public boolean getIsaExtendedMode() {
        return isaExtendedMode;
    }

    private void radioPrintLog(ServerTerminal terminal, ServerChannel channel, boolean acquired, boolean success) {
        if(!logEvents) return;

        Element ep = DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", String.valueOf(terminal.getId()));
        if(ep!=null) {
            String text;
            ServerLogger logger;
            int channelId = channel.getId()&ID_MASK_CHANNEL;
            Element ec = DomTools.getElementFromSection(doc, "ChannelDefs", "id", String.valueOf(channelId));
            String channelName = DomTools.getChildText(ec, "Name", "C/"+channelId, true);
            String playerName = DomTools.getChildText(ep, "Name", "P/?", true);
            int groupIdA = DomTools.getAttributeInt(ep, "groupid", 0, true);
            int groupIdB = (channel.getId()&ID_MASK_GROUP)>>ID_BITSHIFT;
            if(acquired) {
                if(success)
                    text = "RADIO ACQUIRESUCCESS ch["+channelName+"] pl["+playerName+"]";
                else
                    text = "RADIO ACQUIREFAIL ch["+channelName+"] pl["+playerName+"]";
            }
            else
                text = "RADIO RELEASE ch["+channelName+"] pl["+playerName+"]";
            logger = loggerMap.get(groupIdA);
            if(logger!=null) {
                logger.print(text);
            }
            if(groupIdA!=groupIdB) {
                logger = loggerMap.get(groupIdB);
                if(logger!=null) {
                    logger.print(text);
                }
            }
        }
    }

    public void radioAcquired(ServerTerminal terminal, ServerChannel channel, boolean success) {
        radioPrintLog(terminal, channel, true, success);
    }

    public void radioReleased(ServerTerminal terminal, ServerChannel channel) {
        radioPrintLog(terminal, channel, false, true);
    }

    private void phonePrintLog(ServerTerminal sourceTerminal, ServerTerminal destTerminal, int sourceRoleId, int destRoleId, int type, boolean success) {
        if(!logEvents) return;
        if((sourceRoleId==0)||(destRoleId==0)) return;

        String text;
        ServerLogger logger;

        Element ers = DomTools.getElementFromSection(doc, "RoleDefs", "id", String.valueOf(sourceRoleId&ID_MASK_ROLE));
        Element erd = DomTools.getElementFromSection(doc, "RoleDefs", "id", String.valueOf(destRoleId&ID_MASK_ROLE));

        String sourceRoleName = DomTools.getChildText(ers, "Name", "R/"+(sourceRoleId&ID_MASK_ROLE), false);
        String destRoleName   = DomTools.getChildText(erd, "Name", "R/"+(destRoleId&ID_MASK_ROLE), false);

        int groupIds = (sourceRoleId&ID_MASK_GROUP)>>ID_BITSHIFT;
        int groupIdd = (destRoleId  &ID_MASK_GROUP)>>ID_BITSHIFT;

        Element egs = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(groupIds));
        Element egd = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(groupIdd));

        sourceRoleName = DomTools.getChildText(egs, "Name", "G/"+(groupIds), false)+"/"+sourceRoleName;
        destRoleName   = DomTools.getChildText(egd, "Name", "G/"+(groupIdd), false)+"/"+destRoleName;

        if(type==1) { // Ring
            if(success)
                text = "PHONE CALLSUCCESS rs["+sourceRoleName+"] rd["+destRoleName+"]";
            else
                text = "PHONE CALLFAIL rs["+sourceRoleName+"] rd["+destRoleName+"]";
        }
        else if(type==2) { // Answer
            if(success)
                text = "PHONE ANSWERSUCCESS rs["+sourceRoleName+"] rd["+destRoleName+"]";
            else
                text = "PHONE ANSWERFAIL rs["+sourceRoleName+"] rd["+destRoleName+"]";
        }
        else // Hang up
            text = "PHONE HANGUP rs["+sourceRoleName+"] rd["+destRoleName+"]";

        logger = loggerMap.get(groupIds);
        if(logger!=null) {
            logger.print(text);
        }
        if(groupIds!=groupIdd) {
            logger = loggerMap.get(groupIdd);
            if(logger!=null) {
                logger.print(text);
            }
        }
    }

    public void phoneRing(ServerTerminal sourceTerminal, ServerTerminal destTerminal, int sourceRoleId, int destRoleId, boolean success) {
        if(success) updatePhoneLineStatus(sourceTerminal, destTerminal);
        phonePrintLog(sourceTerminal, destTerminal, sourceRoleId, destRoleId, 1, success);
    }

    public void phoneAnswer(ServerTerminal sourceTerminal, ServerTerminal destTerminal, int sourceRoleId, int destRoleId, boolean success) {
        phonePrintLog(sourceTerminal, destTerminal, sourceRoleId, destRoleId, 2, success);
    }

    public void phoneHangup(ServerTerminal sourceTerminal, ServerTerminal destTerminal, int sourceRoleId, int destRoleId) {
        updatePhoneLineStatus(sourceTerminal, destTerminal);
        phonePrintLog(sourceTerminal, destTerminal, sourceRoleId, destRoleId, 3, true);
    }

        public void setIsaStartTime(long isaStartTime) {
                this.isaStartTime = isaStartTime;
        }

    public synchronized void addIsaValue(int terminalId, int value, int responseTime) {
        long t = System.currentTimeMillis();
        //log.info(t + " <- Add ISA value, ID: " + requestId + ", map size: " + isaLogMap.size());
        Element ep = DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", String.valueOf(terminalId));
        if(ep!=null) {
            ServerLogger logger;
            String playerName = DomTools.getChildText(ep, "Name", "P/?", true);
            int groupId = DomTools.getAttributeInt(ep, "groupid", 0, true);
            Element eg = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(groupId));
            String state = DomTools.getAttributeString(eg, "state", "stopped", false);
            if (state.equals("started")) {
                        logger = loggerMap.get(groupId);
                        if(logger!=null) {
                                logger.print("ISA RESPONSE val["+value+"] rt["+responseTime+"] pl["+playerName+"]");
                                // Add entry to ISA panel
                                server.panel.addIsaValue(logger.getSimTime(), terminalId, value);
                        } else { // Logging not active, use ISA start time in ISA panel
                                // Add entry to ISA panel
                                server.panel.addIsaValue((t - isaStartTime), terminalId, value);
                        }
            }
        }
    }

    private String s2(int value) {
        if(value<10)
            return "0"+value;
        else
            return String.valueOf(value);
    }

    public static LanziusServer server;
    public static void start() {

        server = new LanziusServer();

        // Make sure we have nice window decorations.
        // Only if workspace is not fullscreen
        if(!Config.SERVER_SIZE_FULLSCREEN)
            JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                server.init();
            }
        });
    }

        public static void start(String configFilename) {
                start();

                File configFile = new File(configFilename);

                if(configFile.exists()) {
            if(server.buildConfigurationDocument(configFile)) {
                server.isConfigLoaded = true;
                // updateView() probably not needed as it is just started.
                server.menuChoiceServerStart();
                server.itemServerRestart.setEnabled(true);
            } else {
                System.out.println("Error in configuration file:"+configFile);
                System.exit(1);
            }
                } else {
                        System.out.println("Unable to load configuration! File not found:"+configFile);
                        System.exit(1);
                }
        }
        public static void start(String configFilename, String exerciseFilename) {
                start(configFilename);

                File exerciseFile = new File(exerciseFilename);
                if(exerciseFile.exists()) {
                        server.loadExercise(exerciseFile);
                } else {
                        System.out.println("Unable to load exercise! File not found:"+exerciseFile);
                        System.exit(1);
                }
        }
}
