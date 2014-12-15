package com.lfv.yada.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.MulticastSocket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <p>
 * The BasePacketReceiver is an abstract base class for receiving unicast
 * and multicast packets both on the server and on the client side. The
 * subclass must implement the receivePacket method.
 * <p>
 * Copyright &copy; LFV 2006, <a href="http://www.lfv.se">www.lfv.se</a>
 * @author  Andreas Alptun
 * @version Yada 1.0
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
public abstract class BasePacketReceiver {
    protected Log log;
    private UnicastReceiver unicastReceiver;
    private MulticastReceiver multicastReceiver;

    /**
     * Creates a BasePacketReceiver instance. The sockets are instanciated externally
     * and must be passed in the constructor. The multicast socket may be null.
     */
    public BasePacketReceiver(DatagramSocket unicastSocket, MulticastSocket multicastSocket) {
        // Create a logger for this class
        log = LogFactory.getLog(getClass());

        // Creating a receiver for unicast packets
        unicastReceiver = new UnicastReceiver(unicastSocket);

        if(multicastSocket!=null)
            multicastReceiver = new MulticastReceiver(multicastSocket);
    }

    /**
     * Starts the receiver module. This method starts the receiver threads.
     */
    public void startModule() {
        if(unicastReceiver.isStopping) // or/and multicast...
            throw new IllegalStateException("Trying to re-start the receivers");

        log.debug("Starting unicast receiver at port: "+unicastReceiver.socket.getLocalPort());
        unicastReceiver.start();
        if(multicastReceiver!=null) {
            log.debug("Starting multicast receiver at port: "+multicastReceiver.socket.getLocalPort());
            multicastReceiver.start();
        }
    }

    /**
     * Stops the receiver module. This method stops the receiver threads.
     */
    public void stopModule() {
        log.debug("Stopping receivers");
        if(!unicastReceiver.isStopping) {
            unicastReceiver.isStopping = true;
            unicastReceiver.interrupt();
            unicastReceiver.socket.close();
        }
        if(multicastReceiver!=null) {
            if(!multicastReceiver.isStopping) {
                multicastReceiver.isStopping = true;
                multicastReceiver.interrupt();
                multicastReceiver.socket.close();
            }
        }
    }

    protected abstract boolean receivePacket(Packet Packet, boolean multicast);

    private class UnicastReceiver extends Thread {
        private DatagramSocket socket;
        private DatagramPacket datagramPacket;
        private boolean isStopping;

        private UnicastReceiver(DatagramSocket unicastSocket) {
            super("Tunirecv");
            isStopping = false;
            socket = unicastSocket;
            datagramPacket = new DatagramPacket(new byte[PacketPool.PACKET_LENGTH], PacketPool.PACKET_LENGTH);
        }

        @Override
        public void run() {
            PacketPool pool = PacketPool.getPool();
            while(!isInterrupted()&&!isStopping) {
                try {
                    // Receive a UDP packet
                    socket.receive(datagramPacket);
                    //log.debug("Received packet from "+ datagramPacket.getAddress().getHostAddress() + ":" + datagramPacket.getPort() + " of length " + datagramPacket.getLength());
                    // Copy datagram packet into a yada packet
                    Packet packet = pool.borrowPacket(datagramPacket);
                    // Validate the magic number, ignore packet if not correct
                    if(packet.validate()) {
                        // Give it to the super receiver
                        if(!receivePacket(packet,false)) {
                            // Return the packet to the pool if recieve failed
                            pool.returnPacket(packet);
                        }
                    }
                    else {
                        log.warn("Incoming packet is corrupted, ignoring!");
                        // Return the packet to the pool if corrupted
                        pool.returnPacket(packet);
                    }
                } catch (IOException ex) {
                    if(!(isInterrupted()&&isStopping)) {
                        log.warn("Unicast packet receiver interrupted", ex);
                    }
                }
                if(isInterrupted()&&isStopping) {
                    // It's ok, the dispatcher was manually interrupted
                    log.debug("Packet receiver successfully stopped");
                }
            }
        }
    }
    private class MulticastReceiver extends Thread {
        private MulticastSocket socket;
        private DatagramPacket datagramPacket;
        private boolean isStopping;

        private MulticastReceiver(MulticastSocket multicastSocket) {
            super("Tmultirecv");
            isStopping = false;
            socket = multicastSocket;
            datagramPacket = new DatagramPacket(new byte[PacketPool.PACKET_LENGTH], PacketPool.PACKET_LENGTH);
        }

        @Override
        public void run() {
            PacketPool pool = PacketPool.getPool();
            while(!isInterrupted()&&!isStopping) {
                try {
                    // Receive a UDP packet
                    socket.receive(datagramPacket);
                    //log.debug("Received multicast packet from "+ datagramPacket.getAddress().getHostAddress() + ":" + datagramPacket.getPort() + " of length " + datagramPacket.getLength());
                    // Copy datagram packet into a yada packet
                    Packet packet = pool.borrowPacket(datagramPacket);
                    // Validate the magic number, ignore packet if not correct
                    if(packet.validate()) {
                        // Give it to the super receiver
                        if(!receivePacket(packet,true)) {
                            // Return the packet to the pool if recieve failed
                            pool.returnPacket(packet);
                        }
                    }
                    else {
                        log.warn("Incoming packet is corrupted, ignoring!");
                        // Return the packet to the pool if corrupted
                        pool.returnPacket(packet);
                    }
                } catch (IOException ex) {
                    if(!(isInterrupted()&&isStopping)) {
                        log.warn("Unicast packet receiver interrupted", ex);
                    }
                }
                if(isInterrupted()&&isStopping) {
                    // It's ok, the dispatcher was manually interrupted
                    log.debug("Packet receiver successfully stopped");
                }
            }
        }
    }
}
