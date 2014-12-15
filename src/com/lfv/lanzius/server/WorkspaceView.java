package com.lfv.lanzius.server;

import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.ZoomableChart;
import info.monitorenter.gui.chart.IAxis.AxisTitle;
import info.monitorenter.gui.chart.rangepolicies.RangePolicyFixedViewport;
import info.monitorenter.gui.chart.rangepolicies.RangePolicyHighestValues;
import info.monitorenter.gui.chart.traces.Trace2DLtd;
import info.monitorenter.gui.chart.traces.painters.TracePainterVerticalBar;
import info.monitorenter.gui.chart.views.ChartPanel;
import info.monitorenter.util.Range;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextLayout;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;

import com.lfv.lanzius.Constants;
import com.lfv.lanzius.DomTools;

/**
 * <p>
 * WorkspaceView
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
public class WorkspaceView extends JPanel implements MouseListener, Constants {

    /**
         *
         */
        private static final long serialVersionUID = 1L;

        private Log log;

    private LanziusServer server;
    private WorkspacePanel panel;
    private ImageIcon indicatorMonitored;
    private ImageIcon indicatorRelocated;
    private ImageIcon indicatorIsa;

    private HashMap<Integer,Terminal> terminalMap;
    private HashMap<Integer,ITrace2D> isaTraces;
    private List<Group> groupList;

    private int documentVersion;

//    private double isaStartTime;
//    private boolean isaRunning;

    private ZoomableChart isaChart = null;
    private JFrame isaFrame = null;

    Color[] chartColors = {
                Color.RED,
                Color.BLUE,
                Color.GREEN,
                Color.ORANGE,
                Color.PINK,
                Color.CYAN,
                Color.MAGENTA,
                Color.LIGHT_GRAY,
                Color.YELLOW,
                Color.BLACK
    };

    public WorkspaceView(LanziusServer server, WorkspacePanel panel) {
        log = LogFactory.getLog(getClass());
        this.server = server;
        this.panel = panel;
        terminalMap = new HashMap<Integer,Terminal>();
        isaTraces = new HashMap<Integer,ITrace2D>();
        groupList = new LinkedList<Group>();
        indicatorMonitored = new ImageIcon("data/resources/icons/ind_monitored.png");
        indicatorRelocated = new ImageIcon("data/resources/icons/ind_relocated.png");
        indicatorIsa = new ImageIcon("data/resources/icons/ind_isa.png");
        documentVersion = -1;
        setOpaque(true);
        addMouseListener(this);
    }

    /**
     * Set up the ISA chart
     * @param selectionList selected terminals
     */
    public void initIsaChart() {
        if (isaChart == null) {
                // Create a chart:
                isaChart = new ZoomableChart();
        }

        if (isaFrame == null) {
                // Make it visible:
                // Create a frame.
                isaFrame = new JFrame("ISADynamicChart");
                // add the chart to the frame:
                isaChart.getAxisY().setPaintGrid(true);
                isaChart.setGridColor(Color.LIGHT_GRAY);
                isaChart.getAxisX().setRangePolicy(new RangePolicyHighestValues());
                if (server.getIsaExtendedMode()) {
                        isaChart.getAxisY().setRangePolicy(new RangePolicyFixedViewport(new Range(0, 9)));
                } else {
                        isaChart.getAxisY().setRangePolicy(new RangePolicyFixedViewport(new Range(0, 5)));
                }
                isaChart.getAxisX().setAxisTitle(new AxisTitle("Time (minutes)"));
                isaChart.getAxisY().setAxisTitle(new AxisTitle("ISA value"));

                isaFrame.getContentPane().add(isaChart);

                // Add popup menues to the pane
                isaFrame.getContentPane().add(new ChartPanel(isaChart));

                if (server.getIsaExtendedMode()) {
                        isaFrame.setSize(1000,300);
                } else {
                        isaFrame.setSize(1000,205);
                }
                isaFrame.setAlwaysOnTop(true);
                GraphicsEnvironment graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
                Rectangle rect = graphicsEnvironment.getMaximumWindowBounds();
                isaFrame.setLocation(rect.width - 1020, rect.height - 370);
                // Enable the termination button [cross on the upper right edge]:
                isaFrame.addWindowListener(
                    new WindowAdapter(){
                      public void windowClosing(WindowEvent e){
                          panel.updateButtons(false, null);
                      }
                    }
                  );
                server.setIsaStartTime(System.currentTimeMillis());
        }
        isaFrame.setVisible(true);
    }

    public void resetIsaChart() {
        if (isaChart != null) {
                isaChart.removeAllTraces();
        }
        if (isaTraces != null) {
                isaTraces.clear();
        }
        isaFrame = null;
    }

    public void addIsaTrace(int tid) {
        if (!isaTraces.containsKey(tid)) {
                ITrace2D trace = new Trace2DLtd(100);
                trace.setColor(chartColors[isaTraces.size()%10]);
                if (server.getIsaTracePainter().equalsIgnoreCase("bar")) {
                        trace.setTracePainter(new TracePainterVerticalBar(3, isaChart));
                } else {
                        trace.setStroke(new BasicStroke(1.5f));
                }
                trace.setName(terminalMap.get(tid).name);

                // Add the trace to the chart:
                isaChart.addTrace(trace);
                isaTraces.put(tid, trace);
        }
    }

    public void deselectTerminals() {
        Document doc = server.getDocument();
        if(doc==null) return;
        synchronized(doc) {
            Element etd = doc.getRootElement().getChild("TerminalDefs");
            Iterator iter = etd.getChildren().iterator();
            while(iter.hasNext()) {
                Element et = (Element)iter.next();
                et.setAttribute("selected", "false");
            }
            Iterator<Terminal> itert = terminalMap.values().iterator();
            while(itert.hasNext()) {
                itert.next().isSelected = false;
            }
        }
    }

    public void deselectGroups() {
        Document doc = server.getDocument();
        if(doc==null) return;
        synchronized(doc) {
            Element egd = doc.getRootElement().getChild("GroupDefs");
            Iterator iter = egd.getChildren().iterator();
            while(iter.hasNext()) {
                Element eg = (Element)iter.next();
                eg.setAttribute("selected", "false");
            }
            Iterator<Group> iterg = groupList.iterator();
            while(iterg.hasNext()) {
                iterg.next().isSelected = false;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        Document doc = server.getDocument();

        Color storedCol  = g.getColor();
        Font  storedFont = g.getFont();

        // Fill workspace area
        g.setColor(getBackground());
        g.fillRect(0, 0, w, h);

        // Should the cached version be updated?
        int updateDocumentVersion = server.getDocumentVersion();
        boolean update = (documentVersion!=updateDocumentVersion);

        // Check if we have cached the latest document version, otherwise cache the terminals
        if(update) {
            log.debug("Updating view to version "+updateDocumentVersion);
            terminalMap.clear();
            groupList.clear();
            if(doc!=null) {
                synchronized(doc) {

                    // Clear the visible attribute on all groups
                    // except the started or paused ones
                    Element egd = doc.getRootElement().getChild("GroupDefs");
                    Iterator iter = egd.getChildren().iterator();
                    while(iter.hasNext()) {
                        Element eg = (Element)iter.next();
                        boolean isVisible = !DomTools.getAttributeString(eg, "state", "stopped", false).equals("stopped");
                        eg.setAttribute("visible", String.valueOf(isVisible));
                    }

                    // Gather information about terminals and cache it
                    Element etd = doc.getRootElement().getChild("TerminalDefs");
                    iter = etd.getChildren().iterator();
                    while(iter.hasNext()) {
                        Element et = (Element)iter.next();
                        int tid = DomTools.getAttributeInt(et, "id", 0, false);
                        if(tid>0) {
                            // Create terminal and add it to list
                            Terminal t = new Terminal(tid,
                                    DomTools.getAttributeInt(et, "x", 0, false),
                                    DomTools.getAttributeInt(et, "y", 0, false),
                                    DomTools.getChildText(et, "Name", "T/"+tid, false),
                                    DomTools.getAttributeBoolean(et, "online", false, false),
                                    DomTools.getAttributeBoolean(et, "selected", false, false));
                            terminalMap.put(tid, t);

                            // Is the terminal monitored?
                            t.isMonitored = DomTools.getAttributeBoolean(et, "monitored", false, false);

                            // Examine the Player element under PlayerSetup
                            t.groupColor = null;
                            Element ep = DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", String.valueOf(tid));

                            // Has linked player for this terminal
                            if(ep!=null) {
                                StringBuffer sb = new StringBuffer();

                                // Append player name
                                sb.append(DomTools.getChildText(ep, "Name", "P/?", true));
                                sb.append(" (");

                                // Append role list
                                boolean hasRoles = false;
                                Element ers = ep.getChild("RoleSetup");
                                if(ers!=null) {
                                    Iterator iterr = ers.getChildren().iterator();

                                    while(iterr.hasNext()) {
                                        Element er = (Element)iterr.next();
                                        String id = er.getAttributeValue("id");
                                        er = DomTools.getElementFromSection(doc, "RoleDefs", "id", id);
                                        if(er!=null) {
                                            sb.append(DomTools.getChildText(er, "Name", "R/"+id, false));
                                            sb.append(", ");
                                            hasRoles = true;
                                        }
                                    }
                                    if(hasRoles) {
                                        // Trim last comma
                                        int len = sb.length();
                                        sb.setLength(Math.max(len-2,0));
                                    }
                                    sb.append(")");
                                }
                                t.roles = sb.toString();

                                // Is the player relocated?
                                t.isRelocated = DomTools.getAttributeBoolean(ep, "relocated", false, false);

                                // Get group name and color
                                Element eg = DomTools.getElementFromSection(doc, "GroupDefs", "id", DomTools.getAttributeString(ep,"groupid","0",true));
                                t.groupColor = Color.lightGray;
                                if(eg!=null) {
                                    String sc = DomTools.getChildText(eg, "Color", null, false);
                                    if(sc!=null) {
                                        try {
                                            t.groupColor = Color.decode(sc);
                                        } catch(NumberFormatException ex) {
                                            log.warn("Invalid color attribute on Group node, defaulting to grey");
                                        }
                                    }
                                    //t.name += "  "+DomTools.getChildText(eg, "Name", "G/"+eg.getAttributeValue("id"), false);
                                    t.groupName = DomTools.getChildText(eg, "Name", "G/"+eg.getAttributeValue("id"), false);

                                    // This group should now be visible
                                    eg.setAttribute("visible", "true");
                                }
                                else log.warn("Invalid groupid attribute on Player node, defaulting to grey");
                            }
                        }
                        else log.error("Invalid id attribute on Terminal node, skipping");
                    }

                    // Gather information about groups and cache it
                    iter = egd.getChildren().iterator();
                    while(iter.hasNext()) {
                        Element eg = (Element)iter.next();
                        if(DomTools.getAttributeBoolean(eg,"visible", false, false)) {
                            int gid = DomTools.getAttributeInt(eg, "id", 0, true);
                            if(gid>0) {
                                Group grp = new Group(gid,
                                        DomTools.getChildText(eg, "Name", "G/"+gid, false),
                                        DomTools.getAttributeBoolean(eg, "selected", false, false));
                                groupList.add(grp);

                                // group color
                                String sc = DomTools.getChildText(eg, "Color", null, false);
                                if(sc!=null) {
                                    try {
                                        grp.color = Color.decode(sc);
                                    } catch(NumberFormatException ex) {
                                        log.warn("Invalid color attribute on Group node, defaulting to grey");
                                    }
                                }

                                // state color
                                grp.stateColor = Color.red;
                                String state = DomTools.getAttributeString(eg, "state", "stopped", false);
                                if(state.equals("started")) grp.stateColor = Color.green;
                                else if(state.equals("paused")) grp.stateColor = Color.orange;
                            }
                        }
                    }
                }
            }
        }

        if(doc==null) {
            g.setColor(Color.black);
            String text = "No configuration loaded. Select 'Load configuration...' from the file menu.";
            g.drawString(text, (w-SwingUtilities.computeStringWidth(g.getFontMetrics(),text))/2, h*5/12);
        }
        else {
            g.setFont(new Font("Dialog", Font.BOLD, 13));

            Iterator<Terminal> itert = terminalMap.values().iterator();
            while(itert.hasNext()) {
                Terminal t = itert.next();

                // Draw box
                int b = t.isSelected?SERVERVIEW_SELECTION_BORDER:1;
                g.setColor(Color.black);
                g.fillRect(t.x+SERVERVIEW_SELECTION_BORDER-b, t.y+SERVERVIEW_SELECTION_BORDER-b,
                        SERVERVIEW_TERMINAL_WIDTH+2*b, SERVERVIEW_TERMINAL_HEIGHT+2*b);
                g.setColor(t.groupColor==null?Color.white:t.groupColor);
                g.fillRect(t.x+SERVERVIEW_SELECTION_BORDER, t.y+SERVERVIEW_SELECTION_BORDER,
                        SERVERVIEW_TERMINAL_WIDTH, SERVERVIEW_TERMINAL_HEIGHT);

                // Inner areas
                Rectangle r = new Rectangle(
                        t.x+SERVERVIEW_SELECTION_BORDER+SERVERVIEW_TERMINAL_BORDER,
                        t.y+SERVERVIEW_SELECTION_BORDER+SERVERVIEW_TERMINAL_BORDER,
                        SERVERVIEW_TERMINAL_WIDTH-2*SERVERVIEW_TERMINAL_BORDER, g.getFontMetrics().getHeight()+4);

                g.setColor(Color.white);
                g.fillRect(r.x,r.y,r.width,r.height);
                g.fillRect(r.x,r.y+r.height+SERVERVIEW_TERMINAL_BORDER+2,
                        r.width,SERVERVIEW_TERMINAL_HEIGHT-3*SERVERVIEW_TERMINAL_BORDER-r.height-2);
                g.setColor(Color.black);
                g.drawRect(r.x,r.y,r.width,r.height);
                g.drawRect(r.x,r.y+r.height+SERVERVIEW_TERMINAL_BORDER+2,
                        r.width,SERVERVIEW_TERMINAL_HEIGHT-3*SERVERVIEW_TERMINAL_BORDER-r.height-2);

                // Name of terminal and group
                if (server.isaClient(t.tid)) {
                        g.drawImage(indicatorIsa.getImage(), r.x+r.width-20, r.y+SERVERVIEW_TERMINAL_HEIGHT-26, null);
                }
                g.drawString(t.name + " " + t.groupName, r.x+4, r.y+r.height-4);

                double px = r.x+4;
                double py = r.y+r.height+SERVERVIEW_TERMINAL_BORDER+5;

                // Draw monitored indicator
                if(t.isMonitored) {
                    g.drawImage(indicatorMonitored.getImage(), r.x+r.width-9, r.y+3, null);
                }

                // Draw relocated indicator
                if(t.isRelocated) {
                    g.drawImage(indicatorRelocated.getImage(), r.x+r.width-9, r.y+13, null);
                }

                // Draw online indicator
                r.setBounds(r.x, r.y+r.height, r.width, 3);
                g.setColor(t.isOnline?Color.green:Color.red);
                g.fillRect(r.x,r.y,r.width,r.height);
                g.setColor(Color.black);
                g.drawRect(r.x,r.y,r.width,r.height);

                // Roles
                if(t.roles.length()>0) {
                    LineBreakMeasurer lbm = new LineBreakMeasurer(
                            new AttributedString(t.roles).getIterator(),
                            new FontRenderContext(null, false, true));

                    TextLayout layout;
                    while((layout = lbm.nextLayout(SERVERVIEW_TERMINAL_WIDTH-2*SERVERVIEW_TERMINAL_BORDER)) != null) {
                        if(py<t.y+SERVERVIEW_TERMINAL_HEIGHT)
                        {
                            py += layout.getAscent();
                            layout.draw((Graphics2D)g, (int)px, (int)py);
                            py += layout.getDescent() + layout.getLeading();
                        }
                    }
                }
            }

            // Draw group indicators
            int nbrGroupsInRow = w/(2*Constants.SERVERVIEW_SELECTION_BORDER+2+Constants.SERVERVIEW_GROUP_WIDTH);
            if(nbrGroupsInRow<1) nbrGroupsInRow=1;
            int nbrGroupRows   = (groupList.size()+nbrGroupsInRow-1)/nbrGroupsInRow;

            int innerWidth  = Constants.SERVERVIEW_GROUP_WIDTH;
            int innerHeight = g.getFontMetrics().getHeight()+5;

            int outerWidth  = innerWidth+2*Constants.SERVERVIEW_SELECTION_BORDER+2;
            int outerHeight = innerHeight+2*Constants.SERVERVIEW_SELECTION_BORDER+2;

            int x = 0;
            int y = h-outerHeight*nbrGroupRows;

            g.setColor(Color.white);
            g.fillRect(0, y, w, h-y);
            g.setColor(Color.black);
            g.drawLine(0,y-1,w-1,y-1);

            Iterator<Group> iterg = groupList.iterator();
            while(iterg.hasNext()) {
                Group grp = iterg.next();

                // Group box
                grp.boundingRect.setBounds(x,y,outerWidth,outerHeight);
                int b = grp.isSelected?Constants.SERVERVIEW_SELECTION_BORDER:1;
                g.setColor(Color.black);
                g.fillRect(x+Constants.SERVERVIEW_SELECTION_BORDER-b+1, y+Constants.SERVERVIEW_SELECTION_BORDER-b+1,
                        innerWidth+2*b, innerHeight+2*b);
                g.setColor(grp.color);
                g.fillRect(x+Constants.SERVERVIEW_SELECTION_BORDER+1, y+Constants.SERVERVIEW_SELECTION_BORDER+1,
                        innerWidth, innerHeight);

                g.setColor(Color.black);
                g.drawString(grp.name,
                        x+Constants.SERVERVIEW_SELECTION_BORDER+4,
                        y+Constants.SERVERVIEW_SELECTION_BORDER+innerHeight-4+1);

                // Draw started indicator
                g.setColor(grp.stateColor);
                g.fillRect(x+Constants.SERVERVIEW_SELECTION_BORDER+1, y+Constants.SERVERVIEW_SELECTION_BORDER+1, innerWidth, 2);
                g.setColor(Color.black);
                g.drawLine(x+Constants.SERVERVIEW_SELECTION_BORDER+1, y+Constants.SERVERVIEW_SELECTION_BORDER+3,
                        x+Constants.SERVERVIEW_SELECTION_BORDER+1+innerWidth, y+Constants.SERVERVIEW_SELECTION_BORDER+3);

                x += outerWidth;
                if((x+outerWidth)>w) {
                    x = 0;
                    y += outerHeight;
                }
            }
        }

        // Store cached version
        documentVersion = updateDocumentVersion;

        g.setColor(storedCol);
        g.setFont(storedFont);
    }

    public void mousePressed(MouseEvent e) {
        Point p = e.getPoint();
        if(e.getButton()==MouseEvent.BUTTON1) {

            Document doc = server.getDocument();
            if(doc==null) return;

            List<Integer> selectionList = new LinkedList<Integer>();

            boolean selectionChanged = false;

            // Clicked on a group?
            Iterator<Group> iterg = groupList.iterator();
            while(iterg.hasNext()) {
                Group g = iterg.next();
                if(g.boundingRect.contains(p)) {
                    g.isSelected = !g.isSelected;
                    synchronized(doc) {
                        Element egd = DomTools.getElementFromSection(doc, "GroupDefs", "id", String.valueOf(g.gid));
                        if(egd!=null)
                            egd.setAttribute("selected", String.valueOf(g.isSelected));
                    }
                    selectionChanged = true;
                }
                if(g.isSelected)
                    selectionList.add(g.gid);
            }

            if(selectionChanged) {
                // Update panel and view
                deselectTerminals();
                panel.updateButtons(false, selectionList);
                repaint();
                return;
            }


            // Clicked a terminal?
            selectionList.clear();
            Iterator<Terminal> itert = terminalMap.values().iterator();
            while(itert.hasNext()) {
                Terminal t = itert.next();
                if((p.x>=t.x)&&
                        (p.x<t.x+SERVERVIEW_TERMINAL_WIDTH)&&
                        (p.y>=t.y)&&
                        (p.y<t.y+SERVERVIEW_TERMINAL_HEIGHT)) {
                    t.isSelected = !t.isSelected;
                    synchronized(doc) {
                        Element etd = DomTools.getElementFromSection(doc, "TerminalDefs", "id", String.valueOf(t.tid));
                        if(etd!=null)
                            etd.setAttribute("selected", String.valueOf(t.isSelected));
                    }
                    selectionChanged = true;
                }
                if(t.isSelected)
                    selectionList.add(t.tid);
            }

            if(selectionChanged) {
                // Update panel and view
                deselectGroups();
                panel.updateButtons(true, selectionList);
                repaint();
            }

            // Clicked on the workspace, deselect all
            else {
                panel.deselectAll();
            }
        }
    }

    public void addIsaValue(long time, int tid, int value) {
        Terminal t = terminalMap.get(tid);
        if (isaTraces.containsKey(t.tid)) {
                isaTraces.get(t.tid).addPoint(
                                ((double)time/60000), // minutes since start (SIM time)
                                new Integer(value));
        }
    }

    public void mouseClicked(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}

    private class Terminal {

        private int tid,x,y;
        private String name,groupName,roles;
        private Color groupColor;
        private boolean isOnline;
        private boolean isSelected;
        private boolean isRelocated;
        private boolean isMonitored;

        private Terminal(int tid, int x, int y, String name, boolean isOnline, boolean isSelected) {
            this.tid = tid;
            this.x = x;
            this.y = y;
            this.name = name;
            this.groupName = "";
            this.isOnline = isOnline;
            this.isSelected = isSelected;
            this.roles = "";
        }
    }

    private class Group {

        private int gid;
        private String name;
        private Color color;
        private Color stateColor;
        private boolean isSelected;
        private Rectangle boundingRect;

        private Group(int gid, String name, boolean isSelected) {
            this.gid = gid;
            this.name = name;
            this.isSelected = isSelected;
            this.color = Color.lightGray;
            this.stateColor = Color.red;
            boundingRect = new Rectangle();
        }
    }
}
