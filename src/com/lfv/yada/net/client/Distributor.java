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
package com.lfv.yada.net.client;

import com.arkatay.yada.base.ItemDispatcher;
import com.lfv.yada.net.Packet;
import com.lfv.yada.net.PacketPool;
import com.lfv.yada.data.client.ClientBundle;
import com.lfv.yada.data.client.ClientChannel;
import com.lfv.yada.data.client.ClientTerminal;
import com.lfv.yada.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Distributor {

    private Log                  log;

    private int                  terminalId;
    private ClientBundle         bundle;
    protected ItemDispatcher     dispatcher;

    private ClientTerminal       confinedRecipient;
    private int                  confinedChannelId;

    private List<Integer>        radioChannelList;
    private List<ClientTerminal> radioUnicastList;
    private boolean              radioUsesMulticast;

    public Distributor(int terminalId, ClientBundle bundle, ItemDispatcher dispatcher) {
        log = LogFactory.getLog(getClass());

        this.terminalId = terminalId;
        this.bundle = bundle;
        this.dispatcher = dispatcher;

        radioChannelList = new LinkedList<Integer>();
        radioUnicastList = new LinkedList<ClientTerminal>();
    }

    public synchronized void setConfinedRecipient(ClientTerminal recipient, int channelId) {
        if(recipient.hasUnicastConnection()) {
            this.confinedRecipient = recipient;
            this.confinedChannelId = channelId;
            if(log.isDebugEnabled()) {
                log.debug("Adding confined recipient "+recipient.getId()+" on channel "+channelId);
            }
        }
        else {
            this.confinedRecipient = null;
            this.confinedChannelId = 0;
            log.warn("Unable to add confined recipient "+recipient.getId()+" to channel "+channelId+", no unicast connection!");
        }
    }

    public synchronized boolean addRadioChannel(int channelId) {

        // Add channel
        ClientChannel channel = bundle.getChannel(channelId);
        if(channel==null)
            return false;

        // Add channel
        Integer i = new Integer(channelId);
        if(!radioChannelList.contains(i)) {
            radioChannelList.add(i);
        }

        // Add all terminals in channel
        Iterator<ClientTerminal> iter = channel.getTerminalCollection().iterator();
        synchronized(channel) {
            while(iter.hasNext()) {
                ClientTerminal recipient = (ClientTerminal)iter.next();

                // Do not add self and only add if terminal has a proper connection
                if((recipient.getId()!=terminalId)&&recipient.hasConnection()) {
                    // Multicast packet
                    if(recipient.hasMulticastConnection()) {
                        if(log.isDebugEnabled())
                            log.debug("Adding radio multicast recipient "+recipient.getId());
                        radioUsesMulticast = true;
                    }

                    // Unicast packet
                    else if(recipient.hasUnicastConnection()) {
                        // No doubles
                        if(!radioUnicastList.contains(recipient)) {
                            if(log.isDebugEnabled())
                                log.debug("Adding radio unicast recipient "+recipient.getId());
                            radioUnicastList.add(recipient);
                        }
                    }
                }
            }
        }

        return true;
    }

    public synchronized Collection<Integer> getRadioChannels() {
        Collection<Integer> c = new ArrayList<Integer>(radioChannelList);
        return c;
    }

    public synchronized void remove(Collection<Integer> channels) {

        if(channels.contains(confinedChannelId)) {
            confinedRecipient = null;
            confinedChannelId = 0;
        }

        if(radioChannelList.removeAll(channels)) {
            // Rebuild
            radioUnicastList.clear();
            radioUsesMulticast = false;
            Iterator<Integer> iter = radioChannelList.iterator();
            while(iter.hasNext()) {
                int channelId = iter.next().intValue();
                addRadioChannel(channelId);
            }
        }
    }

    public synchronized void removeAll() {
        confinedRecipient = null;
        confinedChannelId = 0;

        radioChannelList.clear();
        radioUnicastList.clear();
        radioUsesMulticast = false;
    }

    public synchronized void distributePacket(Packet packet) {

        PacketPool pool = PacketPool.getPool();

        // Add source uuid to packet
        packet.setSourceId(terminalId);

        // Send to phone
        if(confinedRecipient!=null) {
            // Destination address and port
            SocketAddress socketAddress = confinedRecipient.getSocketAddress();
            if(socketAddress!=null) {

                // Copy a packet for the phone
                Packet packetPhone = pool.borrowPacket(packet);

                // Set destination uuid
                packetPhone.setDestId(confinedRecipient.getId());

                // Add the phone channel
                packetPhone.addAttributeInt(Packet.ATTR_CHANNEL_LIST, confinedChannelId);

                // Set address
                packetPhone.getSocketAddress().setAddress(socketAddress);

                // Post packet
                if(!dispatcher.postItem(packetPhone))
                    pool.returnPacket(packetPhone);

                else if(log.isDebugEnabled())
                    log.debug("Distributing packet to confined channel "+confinedChannelId);
            }
        }

        // Send to radio
        // Add the channel list
        packet.addAttributeList(Packet.ATTR_CHANNEL_LIST, radioChannelList);

        // Send unicast packets
        Iterator<ClientTerminal> iter = radioUnicastList.iterator();
        while(iter.hasNext()) {
            ClientTerminal unicastRecipient = iter.next();

            // Destination address and port
            SocketAddress socketAddress = unicastRecipient.getSocketAddress();
            if(socketAddress!=null) {

                // Make a copy of the packet
                Packet packetCopy = pool.borrowPacket(packet);

                // Set destination uuid
                packetCopy.setDestId(unicastRecipient.getId());

                // Set address
                packetCopy.getSocketAddress().setAddress(socketAddress);

                // Post packet
                if(!dispatcher.postItem(packetCopy))
                    pool.returnPacket(packetCopy);
                else if(log.isDebugEnabled())
                    log.debug("Distributing unicast packet");
            }
        }

        // Send multicast packet
        if(radioUsesMulticast) {
            packet.setFlag(Packet.FLAG_MULTICAST);

            // Send the packet
            if(!dispatcher.postItem(packet))
                pool.returnPacket(packet);
            else if(log.isDebugEnabled())
                log.debug("Distributing multicast packet");
        }
        else
            pool.returnPacket(packet);
    }
}

