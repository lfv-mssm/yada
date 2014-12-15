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
package com.lfv.yada.net.server;

import com.arkatay.yada.base.ItemDispatcher;
import com.lfv.lanzius.server.LanziusServer;
import com.lfv.yada.data.server.ServerBundle;
import com.lfv.yada.data.server.ServerTerminal;
import com.lfv.yada.net.BasePacketReceiver;
import com.lfv.yada.net.Packet;
import java.net.DatagramSocket;

public class ServerPacketReceiver extends BasePacketReceiver {

    private ItemDispatcher controlPacketDispatcher;
    private ItemDispatcher sendPacketDispatcher;
    private ServerBundle   bundle;

    public ServerPacketReceiver(DatagramSocket datagramSocket, ServerBundle bundle) {
        super(datagramSocket, null);
        this.bundle = bundle;
    }

    public void setControlPacketDispatcher(ItemDispatcher dispatcher) {
        this.controlPacketDispatcher = dispatcher;
    }

    public void setSendPacketDispatcher(ItemDispatcher dispatcher) {
        this.sendPacketDispatcher = dispatcher;
    }

    protected boolean receivePacket(Packet packet, boolean multicast) {

        // If the packet is a connection packet, relay it directly to the destination
        if((packet.getType()&Packet.TYPE_FIELD)==Packet.CONNECT) {
            int destTerminalId = packet.getDestId();
            if(destTerminalId>0) {
                ServerTerminal destTerminal = bundle.getTerminal(destTerminalId);
                if(destTerminal!=null) {
                    packet.getSocketAddress().setAddress(destTerminal.getSocketAddress());
                    return sendPacketDispatcher.postItem(packet);
                }
                else {
                    log.error("Unable to relay packet to terminal "+destTerminalId+", not found!");
                }
            }
            else {
                log.error("Unable to relay packet to terminal "+destTerminalId+", no id!");
            }

            // Return false to ignore packet if error
            return false;
        }

        // Route the packet to the control dispatcher by posting it on that queue
        return controlPacketDispatcher.postItem(packet);
    }
}
