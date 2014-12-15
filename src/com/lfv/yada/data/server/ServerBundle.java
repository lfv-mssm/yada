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
package com.lfv.yada.data.server;

import com.lfv.lanzius.Constants;
import com.lfv.lanzius.DomTools;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jdom.Document;
import org.jdom.Element;

public class ServerBundle implements Constants {

    private Log log;
    private Document doc;

    private Map<Integer,ServerChannel>  channelMap;
    private Map<Integer,ServerTerminal> terminalMap;

    public ServerBundle(Document doc) {
        log = LogFactory.getLog(getClass());
        this.doc = doc;

        log.debug("Creating server bundle");
        channelMap   = new TreeMap<Integer,ServerChannel>();
        terminalMap  = new TreeMap<Integer,ServerTerminal>();

        log.debug("Adding COMMON, PHONE and FORWARD channels");
        channelMap.put(CHANNEL_COMMON,  new ServerChannel(CHANNEL_COMMON));
        channelMap.put(CHANNEL_PHONE,   new ServerChannel(CHANNEL_PHONE));
        channelMap.put(CHANNEL_FORWARD, new ServerChannel(CHANNEL_FORWARD));

        synchronized(doc) {

            // Add channels from configuration
            Element egd = doc.getRootElement().getChild("GroupDefs");
            Element ecd = doc.getRootElement().getChild("ChannelDefs");

            Iterator iter = ecd.getChildren().iterator();
            while(iter.hasNext()) {
                Element ec = (Element)iter.next();
                int channelId = DomTools.getAttributeInt(ec, "id", 0, true);
                if(channelId>0) {
                    // Add one channel for each group
                    Iterator iter2 = egd.getChildren().iterator();
                    while(iter2.hasNext()) {
                        Element eg = (Element)iter2.next();
                        int groupId = DomTools.getAttributeInt(eg, "id", 0, true);
                        if(groupId>0) {
                            int globalChannelId = (groupId<<ID_BITSHIFT) | channelId;
                            log.debug("Adding channel "+globalChannelId);
                            channelMap.put(globalChannelId, new ServerChannel(globalChannelId));
                        }
                        else {
                            log.warn("Invalid id attribute on group (must be >0), skipping");
                        }
                    }
                }
                else {
                    log.warn("Invalid id attribute on channel (must be >0), skipping");
                }
            }

            // Add terminals from configuration
            Element etd = doc.getRootElement().getChild("TerminalDefs");
            iter = etd.getChildren().iterator();
            while(iter.hasNext()) {
                Element et = (Element)iter.next();
                int terminalId = DomTools.getAttributeInt(et, "id", 0, true);
                if(terminalId>0) {
                    log.debug("Adding terminal "+terminalId);
                    ServerTerminal terminal = new ServerTerminal(terminalId);
                    terminalMap.put(terminalId, terminal);
                }
                else {
                    log.warn("Invalid id attribute on terminal (must be >0), skipping");
                }
            }
        }
    }

    public ServerChannel getChannel(int channelId) {
        return channelMap.get(channelId);
    }

    public Collection<ServerChannel> getChannelCollection() {
        return channelMap.values();
    }

    public ServerTerminal getTerminal(int terminalId) {
        return terminalMap.get(terminalId);
    }

    public Collection<ServerTerminal> getTerminalCollection() {
        return terminalMap.values();
    }
}
