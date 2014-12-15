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

import com.arkatay.yada.base.HashInteger;
import com.arkatay.yada.base.ItemDispatcher;
import com.lfv.yada.net.BasePacketReceiver;
import com.lfv.yada.net.Packet;
import com.lfv.yada.net.PacketPool;
import com.lfv.lanzius.Constants;
import com.lfv.yada.data.client.ClientTerminal;
import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClientPacketReceiver extends BasePacketReceiver {

    private int terminalId;

    private HashInteger hashIntegerObjU;
    private HashInteger hashIntegerObjM;
    private HashInteger hashIntegerObjF;

    private ItemDispatcher controlPacketDispatcher;
    private ItemDispatcher sendPacketDispatcher;
    private Map<HashInteger,ItemDispatcher> dataPacketDispatcherMap;

    private Set<HashInteger> openChannelSet;
    private Set<HashInteger> preferredChannelSet;

    private ClientTerminal forwardTerminal;
    private int forwardDispatcher;
    private int forwardOtherCount;

    public ClientPacketReceiver(int terminalId, DatagramSocket unicastSocket, MulticastSocket multicastSocket) {
        super(unicastSocket, multicastSocket);
        this.terminalId = terminalId;
        hashIntegerObjU = new HashInteger();
        hashIntegerObjM = new HashInteger();
        hashIntegerObjF = new HashInteger();
        dataPacketDispatcherMap = new HashMap<HashInteger,ItemDispatcher>(64);
        openChannelSet = Collections.synchronizedSet(new HashSet<HashInteger>(64));
        preferredChannelSet = Collections.synchronizedSet(new HashSet<HashInteger>(64));
    }

    public void setControlPacketDispatcher(ItemDispatcher dispatcher) {
        this.controlPacketDispatcher = dispatcher;
    }

    public void setSendPacketDispatcher(ItemDispatcher dispatcher) {
        this.sendPacketDispatcher = dispatcher;
    }

    public void addDataPacketDispatcher(int channelId, ItemDispatcher dispatcher) {
        if(log.isDebugEnabled())
            log.debug("Adding dispatcher "+dispatcher+" to channel "+channelId);
        dataPacketDispatcherMap.put(new HashInteger(channelId), dispatcher);
    }

    public ItemDispatcher getDataPacketDispatcher(int channelId) {
        return (ItemDispatcher)dataPacketDispatcherMap.get(new HashInteger(channelId));
    }

    public Collection<ItemDispatcher> getDataPacketDispatcherCollection() {
    return dataPacketDispatcherMap.values();
    }

    public void removeDataPacketDispatchers() {
        dataPacketDispatcherMap.clear();
    }


    public void openChannel(int channelId) {
        openChannelSet.add(new HashInteger(channelId));
    }

    public void openChannelForward(ClientTerminal forwardTerminal) {
        openChannelSet.add(new HashInteger(Constants.CHANNEL_FORWARD));
        this.forwardTerminal = forwardTerminal;
        this.forwardDispatcher = -1;
        this.forwardOtherCount = 0;
    }

    public void closeChannel(int channelId) {
        openChannelSet.remove(new HashInteger(channelId));
        preferredChannelSet.remove(new HashInteger(channelId));
        if(channelId==Constants.CHANNEL_FORWARD) {
            this.forwardTerminal = null;
            this.forwardDispatcher = -1;
            this.forwardOtherCount = 0;
        }
    }

    public void preferChannel(int channelId) {
        preferredChannelSet.add(new HashInteger(channelId));
    }

    public void unpreferChannel(int channelId) {
        preferredChannelSet.remove(new HashInteger(channelId));
    }


    protected boolean receivePacket(Packet packet, boolean multicast) {

        // Data packets
        if((packet.getType()&Packet.TYPE_FIELD)==Packet.DATA) {

            //log.info("Incoming data packet: "+packet+" multicast: "+multicast);

            // Break if it is my own packet
            if(packet.getSourceId()==terminalId)
                return false;

            //log.info("Channels: "+Arrays.toString(packet.getAttributeList(Packet.ATTR_CHANNEL_LIST)));

            // Check if the packet has any channels
            int iter1,iter2;
            iter1 = iter2 = packet.getAttributeList_Iterator(Packet.ATTR_CHANNEL_LIST);
            if(iter1==0) {
                log.error("Data packet has no channels");
                return false;
            }

            if(packet.getFlag(Packet.FLAG_FORWARD)) {

                if(log.isDebugEnabled())
                    log.debug("Received forward packet from "+packet.getSourceId()+" containing channels "+Arrays.toString(packet.getAttributeList(Packet.ATTR_CHANNEL_LIST)));

                // Is forward channel open, i.e. is the recipient phone line connected
                hashIntegerObjF.setValue(Constants.CHANNEL_FORWARD);
                if(!openChannelSet.contains(hashIntegerObjF)) {
                    return false;
                }

                // Get the forward dispatcher
                ItemDispatcher dispatcher = dataPacketDispatcherMap.get(hashIntegerObjF);
                if(dispatcher==null) {
                    log.error("No forward dispatcher");
                    return false;
                }

                // Are the voice packets already being received through another channel?
                while((iter1=packet.getAttributeList_Next(iter1))!=0) {
                    int j = packet.getAttributeList_Item(iter1);
                    hashIntegerObjF.setValue(j);
                    // If so, return
                    if(openChannelSet.contains(hashIntegerObjF))
                        return false;
                }

                return dispatcher.postItem(packet);
            }

            // We need to use different HashInteger objects here since this function
            // may be called at the same time by the two receivers
            HashInteger hi = multicast?hashIntegerObjM:hashIntegerObjU;

            // First look among the preferred channel dispatchers
            int selectedDispatcher = -1;
            while((iter1=packet.getAttributeList_Next(iter1))!=0) {
                int j = packet.getAttributeList_Item(iter1);
                hi.setValue(j);
                if(preferredChannelSet.contains(hi)) {
                    selectedDispatcher = j;
                    break;
                }
            }

            // No dispatcher found yet, try the open channel dispatchers
            if(selectedDispatcher==-1) {
                while((iter2=packet.getAttributeList_Next(iter2))!=0) {
                    int j = packet.getAttributeList_Item(iter2);
                    hi.setValue(j);
                    if(openChannelSet.contains(hi)) {
                        selectedDispatcher = j;
                        break;
                    }
                }
            }

            // If no open ports for this packet, return
            if(selectedDispatcher==-1)
                return false;

            // Find the correct dispatcher/decoder from the map
            hi.setValue(selectedDispatcher);
            ItemDispatcher dispatcher = dataPacketDispatcherMap.get(hi);

            if(dispatcher!=null) {

                hi.setValue(Constants.CHANNEL_FORWARD);
                // If channel forward is open a phone conversaion is active and incoming packet is for radio
                if(openChannelSet.contains(hi)&&selectedDispatcher>Constants.CHANNEL_RADIO_START) {
                    boolean doForward = false;

                    // Ok, packets are using the same dispatcher, just forward them!
                    if(selectedDispatcher==forwardDispatcher) {
                        forwardOtherCount = 0;
                        doForward = true;
                    }

                    // If forward dispatcher is not in use, set it to the selected
                    else if(forwardDispatcher==-1) {
                        if(forwardTerminal==null)
                            log.error("Forward terminal is null");
                        else if(log.isDebugEnabled())
                            log.debug("Forwarding radio packets on channel "+selectedDispatcher+" to terminal "+forwardTerminal.getId());
                        forwardDispatcher = selectedDispatcher;
                        forwardOtherCount = 0;
                        doForward = true;
                    }

                    // If packets are using another dispatcher, increase count and
                    // reset if reached limit. Max 500ms without any packets from
                    // the initiator will lead to a reset
                    else {
                        forwardOtherCount++;
                        if(forwardOtherCount>25) {
                            forwardDispatcher = selectedDispatcher;
                            forwardOtherCount = 0;
                            doForward = true;
                        }
                    }

                    if(doForward) {
                        // If terminal has a connection to forward to and that terminal is not self
                        if(forwardTerminal!=null&&forwardTerminal.hasUnicastConnection()&&forwardTerminal.getId()!=terminalId&&sendPacketDispatcher!=null) {
                            if(log.isDebugEnabled())
                                log.debug("Forwarding to terminal "+forwardTerminal.getId()+" @ address "+forwardTerminal.getSocketAddress());

                            // Copy a packet for forwarding
                            Packet packetForward = PacketPool.getPool().borrowPacket(packet);

                            // Clear multicast flag
                            packetForward.clearFlag(Packet.FLAG_MULTICAST);

                            // Set forward flag
                            packetForward.setFlag(Packet.FLAG_FORWARD);

                            // If input spurt is being reset, also reset the used forward dispatcher
                            if(packetForward.getFlag(Packet.FLAG_RESET)) {
                                forwardDispatcher = -1;
                                forwardOtherCount = 0;
                            }

                            // Set source uuid, unique for each dispatcher
                            packetForward.setSourceId(terminalId);

                            // Set destination uuid
                            packetForward.setDestId(forwardTerminal.getId());

                            // Set destination address
                            packetForward.getSocketAddress().setAddress(forwardTerminal.getSocketAddress());

                            // Send packet
                            if(!sendPacketDispatcher.postItem(packetForward)) {
                                PacketPool.getPool().returnPacket(packetForward);
                            }
                        }
                        else
                            log.error("Unable to forward data packet, no terminal, no connection or no packet dispatcher!");
                    }
                }

                // Route data packets to the correct data dispatcher
                // Now the packet dispatcher is responsible for returning the packet to the pool
                // If dispatcher returns false, the calling function will return the packet
                return dispatcher.postItem(packet);
            }
            else {
                log.error("No dispatcher for channel "+selectedDispatcher);
                return false;
            }
        }

        // Control packets
        else {
            // Check that the packet is for this client
            if(packet.getDestId()!=terminalId) {
                return false;
            }

            // Route control packets to the control dispatcher
            // Now the packet dispatcher is responsible for returning the packet to the pool
            return controlPacketDispatcher.postItem(packet);
        }
    }
}

