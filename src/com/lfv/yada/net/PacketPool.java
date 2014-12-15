package com.lfv.yada.net;

import java.net.DatagramPacket;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.StackObjectPool;

/**
 * <p>
 * The PacketPool holds a number of packets so that the system does not have to
 * allocate new packets every time it is needed, but can recycle packets instead.
 * New packets are allocated when there are not enough packets in the pool so there
 * is not really an upper packet limit.
 * <p>
 * Copyright &copy; LFV 2006, <a href="http://www.lfv.se">www.lfv.se</a>
 * @author <a href="mailto:info@arkatay.com">Andreas Alptun</a>
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
public final class PacketPool {
    public static final int PACKET_LENGTH = 1024;

    private static PacketPool packetPool;

    private static List<DebugEntry> debugList;

    private Log log;
    private ObjectPool pool;

    private PacketPool() {
        // Create a logger for this class
        log = LogFactory.getLog(getClass());
        pool = new StackObjectPool(new BasePoolableObjectFactory() {
            public Object makeObject() throws Exception {
                return new Packet(PACKET_LENGTH);
            }
        });

        //debugList = new LinkedList<DebugEntry>();
    }

    /**
     * Static function for getting the pool anywhere in the system
     * @return a singleton instance of the packet pool
     */
    public static PacketPool getPool() {
        if(packetPool == null)
            packetPool = new PacketPool();
        return packetPool;
    }

    /**
     * Borrows a packet from the pool and sets it up with packet type only
     * @param  packetType the type of the packet to borrow
     */
    public synchronized Packet borrowPacket(int packetType) {
        try {
            Packet packetCopy = (Packet)pool.borrowObject();
            packetCopy.initialize(packetType);
            if(debugList!=null) {
                debugList.add(new DebugEntry(packetCopy));
                log.info("Borrowing packet with type 0x"+Integer.toHexString(packetType));
            }
            return packetCopy;
        } catch(Exception ex) {
            log.error("Could not borrow packet from pool", ex);
        }
        return null;
    }

    /**
     * Borrows a packet from the pool and copies everything from the specified
     * packet.
     * @param  packet the packet to copy from
     */
    public synchronized Packet borrowPacket(Packet packet) {
        try {
            Packet packetCopy = (Packet)pool.borrowObject();
            packetCopy.copyFrom(packet);
            if(debugList!=null) {
                debugList.add(new DebugEntry(packetCopy));
                log.info("Borrowing packet copy with type 0x"+Integer.toHexString(packet.getType()));
            }
            return packetCopy;
        } catch(Exception ex) {
            log.error("Could not borrow packet from pool", ex);
        }
        return null;
    }

    /**
     * Borrows a packet from the pool and copies everything from the specified
     * datagram packet.
     * @param  packet the datagram packet to copy from
     */
    public synchronized Packet borrowPacket(DatagramPacket packet) {
        try {
            Packet packetCopy = (Packet)pool.borrowObject();
            packetCopy.copyFrom(packet);
            if(debugList!=null) {
                debugList.add(new DebugEntry(packetCopy));
                log.info("Borrowing datagram packet copy with type 0x"+Integer.toHexString(packetCopy.getType()));
            }
            return packetCopy;
        } catch(Exception ex) {
            log.error("Could not borrow packet from pool", ex);
        }
        return null;
    }

    /**
     * Returns the specified packet to the pool when it's not longer needed.
     * @param  packet the packet to return to the pool
     */
    public synchronized void returnPacket(Packet packet) {
        if(packet!=null) {
            try {
                if(debugList!=null) {
                    boolean found = false;
                    Iterator<DebugEntry> iter = debugList.iterator();
                    while(iter.hasNext()) {
                        DebugEntry entry = iter.next();
                        if(entry.id==packet.hashCode()) {
                            log.info("Packet 0x"+Integer.toHexString(packet.hashCode())+" of type 0x"+Integer.toHexString(packet.getType())+" was returned successfully; "+Thread.currentThread().toString());
                            iter.remove();
                            found = true;
                            break;
                        }
                    }

                    if(!found)
                        log.error("Returning un-borrowed packet 0x"+Integer.toHexString(packet.hashCode())+" of type 0x"+Integer.toHexString(packet.getType())+"; "+Thread.currentThread().toString(), new Exception());
                }
                pool.returnObject(packet);

                //log.info("Returning packet with type 0x"+Integer.toHexString(packet.getType()));
            } catch(Exception ex) {
                log.error("Could not return packet to pool", ex);
            }
        }
    }

    public synchronized void printInfoMessage() {
        log.info("PacketPool has "+pool.getNumActive()+" un-returned packet(s) and "+pool.getNumIdle()+" unused packet(s) in pool");
        if(pool.getNumActive()>5000)
            log.warn("Many un-returned packets in pool (>5000)!");

        if(debugList!=null) {
            Iterator<DebugEntry> iter = debugList.iterator();
            while(iter.hasNext()) {
                DebugEntry entry = iter.next();
                log.warn("Unreturned packet 0x"+Integer.toHexString(entry.id)+" of type 0x"+Integer.toHexString(entry.type), entry.exception);
            }
        }
    }

    private class DebugEntry {
        private int id;
        private int type;
        private Exception exception;
        private DebugEntry(Packet p) {
            this.id = p.hashCode();
            this.type = p.getType();
            this.exception = new Exception();
        }
    }
}
