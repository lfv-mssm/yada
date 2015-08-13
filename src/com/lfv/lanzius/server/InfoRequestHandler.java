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

import com.lfv.lanzius.Constants;
import com.lfv.lanzius.DomTools;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.jdom.Document;
import org.jdom.Element;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.handler.AbstractHandler;

/**
 * The InfoRequestHandler is handling the /xml context path of the http server. It
 * uses the template engine Velocity (http://jakarta.apache.org/velocity) to
 * build up xml files from document.
 */
public class InfoRequestHandler extends AbstractHandler {

    private LanziusServer server;
    private Template userTemplate;
    private List<Role> roleDefsList;
    private Log log;

    /**
     * Create a new instance of InfoRequestHandler.
     *
     */
    public InfoRequestHandler(LanziusServer server, Log log) throws Exception {
        this.server = server;
        this.log = log;
        userTemplate = Velocity.getTemplate("data/resources/player.vm");
    }

    /**
     * Handle an incoming http request and let the Velocity module create the
     * response from user and channel structures and xml templates located on
     * the server data/resources folder.
     *
     */
    public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException, ServletException {
        Request base_request = (request instanceof Request) ? (Request)request:HttpConnection.getCurrentConnection().getRequest();
        log.debug("Incoming request: "+target);

        // Create a velocity context
        VelocityContext context = new VelocityContext();

        // Get output writer
        PrintWriter writer = response.getWriter();
        try {
            base_request.setHandled(true);
            if(target.equals("/info")) {
                Document doc = server.getDocument();

                if(doc==null || !server.isServerStarted()) {
                    log.warn("Trying to get info from xml server but the server is not started yet!");
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                }
                else {
                    String tid_s = request.getParameter("terminal");
                    log.debug("Incoming info request for terminal: "+tid_s);

                    // Lists for velocity merge
                    List<Channel> channelList = new LinkedList<Channel>();
                    List<Role>    roleList    = new LinkedList<Role>();

                    synchronized(doc) {
                        // Get Player element from PlayerSetup
                        Element ep = DomTools.getElementFromSection(doc, "PlayerSetup", "terminalid", tid_s);
                        if(ep!=null) {
                            int gid = DomTools.getAttributeInt(ep, "groupid", 0, true);

                            // Names, Group and Codec
                            context.put("TerminalName", DomTools.getChildText(DomTools.getElementFromSection(doc,"TerminalDefs","id", tid_s),"Name","T/"+tid_s,false));
                            context.put("PlayerName", DomTools.getChildText(ep,"Name","",false));
                            context.put("Group", DomTools.getChildText(DomTools.getElementFromSection(doc,"GroupDefs","id", String.valueOf(gid)),"Name","G/"+gid,false));
                            context.put("Codec", DomTools.getAttributeString(doc.getRootElement(),"codec", "null", false));

                            // Role defs list
                            if(roleDefsList==null) {
                                roleDefsList = new LinkedList<Role>();
                                // Populate
                                Iterator iter = doc.getRootElement().getChild("RoleDefs").getChildren().iterator();
                                while(iter.hasNext()) {
                                    Element er = (Element)iter.next();
                                    int id = DomTools.getAttributeInt(er, "id", 0, false);
                                    if(id>0) {
                                        Role r = new Role(DomTools.getChildText(er, "Name", "Withheld", false));
                                        r.setId(id);
                                        roleDefsList.add(r);
                                    }
                                }
                            }

                            context.put("RoleDefsList", roleDefsList);

                            // Channels
                            boolean addedMonitor = false;
                            Element ecs = ep.getChild("ChannelSetup");
                            Iterator iter = ecs.getChildren().iterator();
                            while(iter.hasNext()) {
                                Element ecr = (Element)iter.next();
                                int cid = DomTools.getAttributeInt(ecr,"id",0,true);
                                Element ec = DomTools.getElementFromSection(doc,"ChannelDefs","id",String.valueOf(cid));
                                if(ec!=null) {
                                    // Collect all attributes and textnodes into the Channel struct
                                    // with correct attribute priority:
                                    // high:  Channel node under ChannelSetup
                                    // med:   Channel node under ChannelDefs
                                    // low:   Player (groupId)
                                    Channel ch = new Channel(DomTools.getChildText(ec,"Name","C/"+cid,false));

                                    // Calculate which group the channel belongs to and set client cid
                                    int gidc = DomTools.getPrioritizedAttribute("groupid", gid, ecr, ec);
                                    ch.setId((gidc<<Constants.ID_BITSHIFT) | cid);

                                    // All other attrs
                                    ch.setState(DomTools.getPrioritizedAttribute("state", "off", ecr, ec));
                                    ch.setLocked(DomTools.getPrioritizedAttribute("locked", "false", ecr, ec));
                                    ch.setRecordable(DomTools.getPrioritizedAttribute("recordable", "false", ecr, ec)); 
                                    ch.setAutorec(DomTools.getPrioritizedAttribute("autorec", "false", ecr, ec));
                                    if (ch.getAutorec().equals("true")) {
                                    	// Recordable must be true for autorec to show recording status in GUI
                                    	ch.setRecordable("true");
                                    }
                                    ch.setHidden(DomTools.getPrioritizedAttribute("hidden", "false", ecr, ec));
                                    ch.setMonitor(DomTools.getPrioritizedAttribute("monitor", "false", ecr, ec));

                                    // Prevent duplicate monitors and monitor and state rxtx combination
                                    if(ch.getMonitor().equals("true")) {
                                        if(addedMonitor) {
                                            ch.setMonitor("false");
                                        }
                                        else {
                                            if(ch.getState().equals("rxtx")) {
                                                ch.setState("rx");
                                            }
                                            addedMonitor = true;
                                        }
                                    }

                                    // Show group name on channel?
                                    if(DomTools.getPrioritizedAttribute("showgroup", "false", ecr, ec).equals("true")) {
                                        String groupName = DomTools.getChildText(DomTools.getElementFromSection(doc,"GroupDefs","id", String.valueOf(gidc)),"Name","G/"+gidc,false);
                                        ch.setName(groupName+"/"+ch.getName());
                                    }

                                    channelList.add(ch);
                                }
                                else log.error("Missing Channel node in ChannelDefs ("+cid+")");
                            }

                            // Roles
                            Element ers = ep.getChild("RoleSetup");
                            iter = ers.getChildren().iterator();
                            while(iter.hasNext()) {
                                Element err = (Element)iter.next();
                                int rid = DomTools.getAttributeInt(err, "id", 0, false);

                                // Empty slot
                                if(rid==0) {
                                    roleList.add(new Role("empty_slot"));
                                }
                                else {
                                    Element er = DomTools.getElementFromSection(doc,"RoleDefs","id",String.valueOf(rid));
                                    Role r = new Role(DomTools.getChildText(er,"Name","R/"+rid,false));

                                    int uid  = DomTools.getAttributeInt(err,"uid",0,true);

                                    // Debug check, calculate which group the role belongs to (already done in linkTerminal)
                                    int uidc = (DomTools.getPrioritizedAttribute("groupid", gid, err, er)<<Constants.ID_BITSHIFT) | rid;
                                    if(uid!=uidc)
                                        log.error("UIDs mismatch when requesting xml info ("+uid+" != "+uidc+")");

                                    //  Set client rid
                                    r.setId(uid);

                                    // Peers
                                    List<Peer> l = r.getPeerList();
                                    Element eps = er.getChild("PhoneSetup");
                                    if(eps!=null) {
                                        Iterator iter2 = eps.getChildren().iterator();
                                        while(iter2.hasNext()) {
                                            Element erpr = (Element)iter2.next();
                                            int rpid = DomTools.getAttributeInt(erpr,"id",0,false);

                                            // Empty slot
                                            if(rpid==0) {
                                                l.add(new Peer("empty_slot"));
                                            }
                                            else {
                                                Element erp = DomTools.getElementFromSection(doc,"RoleDefs","id",String.valueOf(rpid));
                                                Peer p = new Peer(DomTools.getChildText(erp,"Name","R/"+rpid,false));

                                                // Calculate which group the role belongs to and set client rpid
                                                int gidc = DomTools.getPrioritizedAttribute("groupid", gid, erpr, erp);
                                                p.setId((gidc<<Constants.ID_BITSHIFT) | rpid);

                                                // Show group name on peer?
                                                if(DomTools.getPrioritizedAttribute("showgroup", "false", erpr, erp).equals("true")) {
                                                    String groupName = DomTools.getChildText(DomTools.getElementFromSection(doc,"GroupDefs","id", String.valueOf(gidc)),"Name","G/"+gidc,false);
                                                    p.setName(groupName+"/"+p.getName());
                                                }

                                                // Add to phone peer list, never add self!
                                                if(r.getId()!=p.getId())
                                                    l.add(p);
                                            }
                                        }
                                    }
                                    else
                                        log.warn("Missing PhoneSetup for Role "+rid+"!");

                                    roleList.add(r);
                                }
                            }
                        }
                        else {
                            log.warn("Incoming info request for a terminal that does not exist ("+tid_s+")");
                            context.put("Name", "Unknown");
                            context.put("Group", "Unknown");
                            context.put("Codec", "Unknown");
                        }
                    }

                    context.put("ChannelList", channelList);
                    context.put("RoleList", roleList);

                    userTemplate.merge(context, writer);
                }
            }
            else if(target.equals("/isa")) {
                log.debug("Received ISA request");
                response.sendError(HttpServletResponse.SC_OK);
                int terminalId = Integer.parseInt(request.getParameter("terminal"));
                int value = Integer.parseInt(request.getParameter("val"));
                int time = Integer.parseInt(request.getParameter("t"));
                log.debug("ISA request, terminal = " + terminalId + ", val = " + value + ", time = " + time);
                server.addIsaValue(terminalId, value, time);
            }
            else
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (ResourceNotFoundException ex) {
            log.error("Resource not found", ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (ParseErrorException ex) {
            log.error("Parse Error", ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (MethodInvocationException ex) {
            log.error("Method Invocation Error", ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            log.error("Exception in request! Correct configuration?", ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        writer.close();
    }

    public class Channel {

        private int id;
        private String state;
        private String locked;
        private String recordable;
        private String autorec;
        private String hidden;
        private String name;
        private String monitor;

        private Channel(String name) {
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getState() {
            return state;
        }

        public String getLocked() {
            return locked;
        }

        public String getRecordable() {
            return recordable;
        }
        
        public String getAutorec() {
            return autorec;
        }        

        public String getHidden() {
            return hidden;
        }

        public String getName() {
            return name;
        }

        public String getMonitor() {
            return monitor;
        }

        private void setId(int id) {
            this.id = id;
        }

        private void setState(String state) {
            this.state = state;
        }

        private void setLocked(String locked) {
            this.locked = locked;
        }

        private void setRecordable(String recordable) {
            this.recordable = recordable;
        }
        
        private void setAutorec(String autoRecord) {
            this.autorec = autoRecord;
        }        

        private void setHidden(String hidden) {
            this.hidden = hidden;
        }

        private void setName(String name) {
            this.name = name;
        }

        private void setMonitor(String monitor) {
            this.monitor = monitor;
        }
    }

    public class Role {
        private int id;
        private String name;
        private List<Peer> peerList;

        private Role(String name) {
            this.name = name;
            peerList = new LinkedList<Peer>();
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<Peer> getPeerList() {
            return peerList;
        }

        private void setId(int id) {
            this.id = id;
        }
    }

    public class Peer {
        private int id;
        private String name;

        private Peer(String name) {
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        private void setId(int id) {
            this.id = id;
        }

        private void setName(String name) {
            this.name = name;
        }
    }
}
