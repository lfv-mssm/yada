package com.arkatay.yada.codec;

import com.arkatay.yada.base.ItemDispatcher;
import com.lfv.yada.net.Packet;
import com.lfv.yada.net.PacketPool;

/**
 * <p>
 * The JitterBuffer is an implementation of an ItemDispatcher using a circular
 * queue. Only Packet objects are allowed to be added to the queue. The
 * JitterBuffer class re-orders packets according to their sequence number when
 * getting packets from the queue.
 * <p>
 * Copyright &copy; LFV 2006, <a href="http://www.lfv.se">www.lfv.se</a>
 *
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
public abstract class JitterBuffer extends ItemDispatcher {

    protected Packet[] buffer;
    protected int bufferSize;
    protected int bufferSizeMinusOne;
    protected int leftIndex;
    protected int rightIndex;
    protected int sourceUserId;

    private PacketPool pool;

    /**
     * Creates a new instance of the JitterBuffer. The buffer size must
     * be a power of two (8,16,32,64,128 etc) or IllegalArgumentException will
     * be thrown.
     *
     * @param bufferSize size of the circular buffer
     * @throws IllegalArgumentException if the buffer size is not a power of two
     */
    public JitterBuffer(String name, int bufferSize) {
        super(name+"-jitterbuf");
        this.bufferSize = bufferSize;
        this.bufferSizeMinusOne = bufferSize-1;
        // Check for power of two
        if((bufferSize&bufferSizeMinusOne)!=0)
            throw new IllegalArgumentException("The bufferSize parameter must be a power of two (8,16,32,64,128 etc)");
        buffer = new Packet[bufferSize];
        leftIndex = rightIndex = 0;
        sourceUserId = -1;
        pool = PacketPool.getPool();
        // Prevent subclasses from using the super list
        super.list = null;
    }

    /**
     * Posts a packet on the queue. If the filter has been set, only packets from
     * that user is added to the queue.
     *
     * @param  item the packet to add to the queue
     * @return true if the item was added
     */
    @Override
    public synchronized boolean postItem(Object item) {
        Packet packet = (Packet)item;

        // Filter out packets from other users of if filter is set
        if(sourceUserId!=-1) {
            if(packet.getSourceId()!=sourceUserId)
                return false;
        }

        // Add if it fits in the buffer
        if((rightIndex-leftIndex)<bufferSize) {
            buffer[rightIndex&bufferSizeMinusOne] = packet;
            rightIndex++;
            notify();
            return true;
        }

        return false;
    }

    @Override
    protected synchronized void waitItem(int timeoutMillis) throws InterruptedException {
        if((rightIndex-leftIndex)<=0)
            wait(timeoutMillis);
    }

    @Override
    protected synchronized void waitItem() throws InterruptedException {
        while((rightIndex-leftIndex)<=0)
            wait();
    }

    @Override
    protected synchronized Object peekItem() {
        if(leftIndex<rightIndex)
            return buffer[leftIndex&bufferSizeMinusOne];
        return null;
    }

    @Override
    protected synchronized Object getItem() {
        if(leftIndex<rightIndex) {
            Object obj = buffer[leftIndex&bufferSizeMinusOne];
            leftIndex++;
            return obj;
        }
        return null;
    }

    @Override
    public synchronized void clear() {
        // Get all packets and return them to pool
        Packet p = (Packet)getItem();
        while(p!=null) {
            pool.returnPacket(p);
            p = (Packet)getItem();
        }
        leftIndex = rightIndex = 0;
    }

    @Override
    public synchronized int getSize() {
        return (rightIndex-leftIndex);
    }

    /**
     * Cleans up the jitter buffer. Null packet and packets that are too old are
     * thrown away up until the expected sequence number.
     *
     * @param expectedSequenceNbr the sequence number of the packet to clean to
     * @return the length of the queue after cleaning
     */
    protected synchronized int clean(int expectedSequenceNbr) {
        if(rightIndex<leftIndex) {
            log.error("Right index is less than left index, clearing buffer!");
            clear();
            return 0;
        }

        // Remove and return all packets older than the expected
        while(leftIndex<rightIndex) {
            Packet packet = buffer[leftIndex&bufferSizeMinusOne];
            if(packet==null) {
                log.error("Null packet found in jitter buffer, clearing buffer!");
                clear();
                return 0;
            }
            else {
                if(packet.getSequenceNbr()<expectedSequenceNbr) {
                    pool.returnPacket(packet);
                    leftIndex++;
                }
                else
                    break;
            }
        }
        return (rightIndex-leftIndex);
    }

    /**
     * Sets the jitter buffer to filter out all packet from other users than the
     * ones from the specified user.
     *
     * @param sourceUserId the unique user id of the user to keep packets from
     */
    protected synchronized void setFilter(int sourceUserId) {
        this.sourceUserId = sourceUserId;
    }

    /**
     * Clears the jitter buffer filter to accept packets from all users
     *
     */
    protected synchronized void clearFilter() {
        this.sourceUserId = -1;
    }

    /**
     * Gets a packet from the jitter buffer with the expected sequence number.
     * Packets with lower sequence number are discarded because they have
     * probably already been concealed. NOTE: Packets gotten from this function
     * should not be returned to the pool, this is handled by the jitter buffer
     * internally!
     *
     * @param expectedSequenceNbr the expected sequence number of the packet to get
     * @return the packet having the specified sequence number or null if not found
     */
    protected synchronized Packet getPacket(int expectedSequenceNbr) {
        // Look for the expected packet
        int curIndex = leftIndex;
        while(curIndex<rightIndex) {
            Packet packet = buffer[curIndex&bufferSizeMinusOne];

            // Return if found
            if(packet.getSequenceNbr()==expectedSequenceNbr)
                return packet;

            // Go to next one in jitterbuffer
            curIndex++;
        }

        return null;
    }

    /**
     * Returns the last packet in the queue, i.e. the one that was added most
     * recently or null if queue is empty. The packet is not removed from the
     * queue.
     *
     * @return the last packet in the queue
     */
    protected synchronized Packet peekLastPacket() {
        if(leftIndex<rightIndex)
            return buffer[(rightIndex-1)&bufferSizeMinusOne];
        return null;
    }

    /**
     * Prints the contents of the queue for debugging purposes only
     *
     */
    protected void print() {
        System.out.print(" ::: ");
        int i = leftIndex;
        while(i<rightIndex) {
            Packet packet = buffer[i&bufferSizeMinusOne];
            i++;

            if(packet==null)
                System.out.print("[P: null], ");
            else
                System.out.print("[P: "+packet.getSequenceNbr()+","+packet.getSourceId()+"], ");
        }
        System.out.println(" ::: ");
    }
}
