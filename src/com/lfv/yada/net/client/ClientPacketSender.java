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

import com.lfv.yada.net.Packet;
import com.lfv.yada.net.PacketBlockDispatcher;
import com.lfv.yada.net.PacketPool;
import com.lfv.yada.net.SocketAddress;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.MulticastSocket;

public class ClientPacketSender extends PacketBlockDispatcher {

    private DatagramSocket   unicastSocket;
    private MulticastSocket  multicastSocket;
    private SocketAddress    multicastSocketAddr;

    public ClientPacketSender(DatagramSocket unicastSocket, MulticastSocket multicastSocket, SocketAddress multicastSocketAddr) {
        super("Tpacketsender");
        this.unicastSocket = unicastSocket;
        this.multicastSocket = multicastSocket;
        this.multicastSocketAddr = new SocketAddress(multicastSocketAddr);
    }

    protected void dispatchPacket(Packet packet) {

        // Finalize the packet
        packet.complete();

        // Try to send to the recipient
        if(packet.getFlag(Packet.FLAG_MULTICAST)) {
            try {
                packet.getSocketAddress().setAddress(multicastSocketAddr);
                multicastSocket.send(packet.getDatagramPacket());
            } catch(IOException ex) {
                log.warn("Multicast sender failure, packet was not sent ("+ex.getMessage()+")");
            }
        }
        else {
            try {
                unicastSocket.send(packet.getDatagramPacket());
            } catch(IOException ex) {
                log.warn("Unicast sender failure, packet was not sent ("+ex.getMessage()+")");
            }
        }

        // Return packet to its pool
        PacketPool.getPool().returnPacket(packet);
    }
}
