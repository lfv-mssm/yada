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
package com.lfv.yada.net;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

/**
 * The basic structure of a data packet that is sent over the network as a UDP unicast or multicast datagram.
 * All packets are encoded using network ordered binary (big-endian). A magic number and a transaction number are
 * found first in the header. The transaction number is randomized in the request and all responses must carry
 * the same identifier as the request they correspond to. See the YADA Manual for a figure of the packet header
 * format.
 */
public final class Packet {

    public static long randomSeed = 123456789L;


    public static final int DIRECTION_FIELD   = 0x30;
    public static final int TYPE_FIELD        = 0x0F;

    public static final int REQUEST           = 0x10;
    public static final int RESPONSE          = 0x20;

    // TYPES
    public static final int PING              = 0x01;
    public static final int SESSION           = 0x02;
    public static final int UPDATE            = 0x03;
    public static final int INFO              = 0x04;
    public static final int CONNECT           = 0x05;
    public static final int INITIATE          = 0x06;
    public static final int ISA                           = 0x07;
    public static final int DATA              = 0x0F;

    // FLAGS
    public static final int FLAG_START        = 0x01;
    public static final int FLAG_STOP         = 0x02;
    public static final int FLAG_CLOSE        = 0x04;
    public static final int FLAG_RESET        = 0x08;
    public static final int FLAG_PAUSE        = 0x10;
    public static final int FLAG_MULTICAST    = 0x20;
    public static final int FLAG_FORWARD      = 0x40;

    // ATTRIBUTES
    public static final int ATTR_DATA                 = 0x40000000;
    public static final int ATTR_CHANNEL_LIST         = 0x40010000;
    public static final int ATTR_CHANNEL              = 0x40020000;
    public static final int ATTR_IPADDRESS            = 0x40030000;
    public static final int ATTR_IPPORT               = 0x40040000;
    public static final int ATTR_SOURCE_ROLE          = 0x40050000;
    public static final int ATTR_DEST_ROLE            = 0x40060000;

    public static final int ATTR_RADIO_ACQUIRE        = 0x40070000;
    public static final int ATTR_RADIO_ACQUIRE_ACCEPT = 0x40080000;
    public static final int ATTR_RADIO_ACQUIRE_BUSY   = 0x40090000;

    public static final int ATTR_RADIO_RELEASE        = 0x400A0000;
    public static final int ATTR_RADIO_RELEASE_ACCEPT = 0x400B0000;

    public static final int ATTR_PHONE_RING           = 0x400C0000;
    public static final int ATTR_PHONE_RING_ACCEPT    = 0x400D0000;
    public static final int ATTR_PHONE_RING_BUSY      = 0x400E0000;
    public static final int ATTR_PHONE_ANSWER         = 0x400F0000;
    public static final int ATTR_PHONE_ANSWER_ACCEPT  = 0x40100000;
    public static final int ATTR_PHONE_ANSWER_REJECT  = 0x40110000;
    public static final int ATTR_PHONE_HANGUP         = 0x40120000;
    public static final int ATTR_PHONE_HANGUP_ACCEPT  = 0x40130000;

    public static final int ATTR_ISA_PERIOD               = 0x40140000;
    public static final int ATTR_ISA_EXTMODE              = 0x40150000;
    public static final int ATTR_ISA_NUM_CHOICES          = 0x40160000;
    public static final int ATTR_ISA_KEYTEXT0             = 0x40170000;
    public static final int ATTR_ISA_KEYTEXT1             = 0x40180000;
    public static final int ATTR_ISA_KEYTEXT2             = 0x40190000;
    public static final int ATTR_ISA_KEYTEXT3             = 0x401A0000;
    public static final int ATTR_ISA_KEYTEXT4             = 0x401B0000;
    public static final int ATTR_ISA_KEYTEXT5             = 0x401C0000;
    public static final int ATTR_ISA_KEYTEXT6             = 0x401D0000;
    public static final int ATTR_ISA_KEYTEXT7             = 0x401E0000;
    public static final int ATTR_ISA_KEYTEXT8             = 0x401F0000;

    public static final int ATTR_RESET_CHANNELS       = 0x40A00000;

    public static final int ATTR_ROLE_ACTIVITY                = 0x41010000;
    public static final int ATTR_ROLE_ACTIVITY_FLAG_AVAILABLE = 0x01000000;
    public static final int ATTR_ROLE_ACTIVITY_FLAG_INCALL    = 0x02000000;
    public static final int ATTR_ROLE_ACTIVITY_MASK_FLAGS     = 0xff000000;
    public static final int ATTR_ROLE_ACTIVITY_MASK_ID        = 0x00ffffff;

    public static final int ATTR_MONITOR_SOURCE_START         = 0x41020000;
    public static final int ATTR_MONITOR_SOURCE_STOP          = 0x41030000;
    public static final int ATTR_MONITOR_SINK_START           = 0x41040000;
    public static final int ATTR_MONITOR_SINK_STOP            = 0x41050000;

    // FIELDS
    private static final int MAGIC           = 0x59414441;
    private static final int L_HEADER        = 24;
    private static final int P_MAGICNBR      = 0;
    private static final int P_TRANSACTIONID = 4;
    private static final int P_TYPE          = 8;
    private static final int P_FLAGS         = 9;
    private static final int P_ATTRLENGTH    = 10;
    private static final int P_DESTUUID      = 12;
    private static final int P_SOURCEUUID    = 16;
    private static final int P_SEQUENCENBR   = 20;
    private static final int P_SESSIONID     = 20; // shared

    private ByteBuffer buf;
    private DatagramPacket datagramPacket;
    private SocketAddress socketAddr;
    private AttributeData attrData;

    /**
     * Creates a new instance of Packet with a specified length in bytes. A packet should
     * never be created directly using this constructor. Instead it should be borrowed from
     * the packet pool
     * @param  length  size of the packet is bytes
     * @see    PacketPool
     */
    public Packet(int length) {
        // Create an embedded datagram packet
        byte[] b = new byte[length];
        datagramPacket = new DatagramPacket(b, length);
        attrData = new AttributeData();

        // Wrap the packet byte buffer in a ByteBuffer object for
        // simple reading and writing of all primitive types
        this.buf = ByteBuffer.wrap(b);

        this.socketAddr = new SocketAddress();
    }

    /**
     * Initializes the packet, generates a new transaction id and sets up the packet header.
     * This function is called from the packet pool.
     * @param  packetType type of the packet to be initialized
     */
    public void initialize(int packetType) {
        // New transaction id
        int transactionId = Packet.getRandInt();

        // Clear everything
        byte[] bytearray = buf.array();
        for(int i=0;i<L_HEADER;i++)
            bytearray[i] = 0;
        buf.clear();

        // Write Magic number, transaction id and packet type
        buf.putInt(MAGIC).putInt(transactionId).put((byte)packetType);

        // Set position to beginning of attributes
        buf.position(L_HEADER);
    }

    /**
     * Completes the packet, sets up the length of the attributes
     * This function is called by the sender just before the packet is sent
     * on the network.
     */
    public void complete() {
        int totalLength = buf.position();
        buf.putShort(P_ATTRLENGTH,(short)(totalLength-L_HEADER));
    }

    /**
     * Checks the magic number
     * @return true if the magic number matches the one in the packet
     */
    public boolean validate() {
        return (buf.getInt(P_MAGICNBR)==MAGIC);
    }

    public void copyFrom(Packet packet) {
        // Copy packet address
        socketAddr.setAddress(packet.socketAddr);
        // Copy packet raw data
        copyFrom(packet.buf.array(), packet.buf.position());
    }
    public void copyFrom(DatagramPacket packet) {
        // Copy packet address
        socketAddr.setAddress(packet.getAddress(),packet.getPort());
        // Copy packet raw data
        copyFrom(packet.getData(), packet.getLength());
    }

    public int getType() {
        return buf.get(P_TYPE);
    }

    public void setTransactionId(int transactionId) {
        buf.putInt(P_TRANSACTIONID, transactionId);
    }
    public int getTransactionId() {
        return buf.getInt(P_TRANSACTIONID);
    }

    public void setFlag(int flag) {
        int flags = buf.get(P_FLAGS);
        flags |= flag;
        buf.put(P_FLAGS,(byte)(flags&0xFF));
    }
    public void clearFlag(int flag) {
        int flags = buf.get(P_FLAGS);
        flags &= ~flag;
        buf.put(P_FLAGS,(byte)(flags&0xFF));
    }
    public boolean getFlag(int flag) {
        int flags = buf.get(P_FLAGS);
        return (flags&flag)!=0;
    }

    public void setDestId(int id) {
        buf.putInt(P_DESTUUID,id);
    }
    public int getDestId() {
        return buf.getInt(P_DESTUUID);
    }

    public void setSourceId(int id) {
        buf.putInt(P_SOURCEUUID,id);
    }
    public int getSourceId() {
        return buf.getInt(P_SOURCEUUID);
    }

    public void setSequenceNbr(int seqNbr) {
        buf.putInt(P_SEQUENCENBR,seqNbr);
    }
    public int getSequenceNbr() {
        return buf.getInt(P_SEQUENCENBR);
    }
    public void setSessionId(int sessionId) {
        buf.putInt(P_SESSIONID,sessionId);
    }
    public int getSessionId() {
        return buf.getInt(P_SESSIONID);
    }

    public SocketAddress getSocketAddress() {
        return socketAddr;
    }

    public void addAttributeBool(int attrType) {
        buf.putInt(attrType);
    }
    public void addAttributeInt(int attrType, int attrValue) {
        buf.putInt(attrType|4).putInt(attrValue);
    }
    public void addAttributeList(int attrType, int[] attrList) {
        buf.putInt(attrType|(attrList.length*4));
        for(int i=0;i<attrList.length;i++) {
            buf.putInt(attrList[i]);
        }
    }
    public void addAttributeList(int attrType, int[] attrList, int length) {
        buf.putInt(attrType|(length*4));
        for(int i=0;i<length;i++) {
            buf.putInt(attrList[i]);
        }
    }
    public void addAttributeList(int attrType, Collection<Integer> attrList) {
        buf.putInt(attrType|(attrList.size()*4));
        Iterator<Integer> iter = attrList.iterator();
        while (iter.hasNext()) {
            buf.putInt(iter.next().intValue());
        }
    }
    public void addAttributeString(int attrType, String attr) {
        int length= attr.length();
        byte bytes[];
        bytes = attr.getBytes();
        buf.putInt(attrType|(length*4));
        for ( int i = 0; i < length; i++) {
           int val = bytes[i];
           buf.putInt(val);
        }
    }

    public boolean getAttributeBool(int attrType) {
        int i = L_HEADER;
        while(i<buf.position()) {
            int type_len = buf.getInt(i);
            if( (type_len&0xffff0000) == attrType ) {
                return true;
            }
            i += 4 + (type_len&0x0000ffff);
        }
        return false;
    }
    public int getAttributeInt(int attrType) {
        int i = L_HEADER;
        while(i<buf.position()) {
            int type_len = buf.getInt(i);
            if( (type_len&0xffff0000) == attrType ) {
                return buf.getInt(i+4);
            }
            i += 4 + (type_len&0x0000ffff);
        }
        return -1;
    }
    public int[] getAttributeList(int attrType) {
        int i = L_HEADER;
        while(i<buf.position()) {
            int type_len = buf.getInt(i);
            if( (type_len&0xffff0000) == attrType ) {
                // Get the length of the attribute and convert it to number of ints
                int nbrInts = (type_len&0x0000ffff)/4;
                // Allocate a result array of int's
                int[] res = new int[nbrInts];
                // Fill out the result array
                for (int j=0;j<nbrInts;j++) {
                    res[j] = buf.getInt(i+4*(j+1));
                }
                // And return it!
                return res;
            }
            i += 4 + (type_len&0x0000ffff);
        }
        return null;
    }
    public int getAttributeList_Iterator(int attrType) {
        int i = L_HEADER;
        while(i<buf.position()) {
            int type_len = buf.getInt(i);
            if( (type_len&0xffff0000) == attrType ) {
                // Get the length of the attribute and convert it to number of ints
                int nbrInts = (type_len&0x0000ffff)/4;
                // Return an "iterator", pointing at one item before the first
                return (nbrInts<<16)|i;
            }
            i += 4 + (type_len&0x0000ffff);
        }
        return 0;
    }
    public String getAttributeString(int attrType) {
        int i = L_HEADER;
        while(i<buf.position()) {
            int type_len = buf.getInt(i);
            if( (type_len&0xffff0000) == attrType ) {
                // Get the length of the attribute and convert it to number of ints
                int length = (type_len&0x0000ffff)/4;
                byte bytes[] = new byte[length];
                int val;
                for ( int j=0;j<length;j++) {
                   val= buf.getInt(i+4*(j+1));
                   bytes[j] = (byte)val;
                }

                String res = new String(bytes);

                return res;
            }
            i += 4 + (type_len&0x0000ffff);
        }
        return null;
    }
    public int getAttributeList_Next(int iterator) {
        int n = ((iterator&0xffff0000)>>16);
        int i = ((iterator&0x0000ffff)    )+4;
        // No more items
        if(n==0)
            return 0;
        return ((n-1)<<16)|i;
    }
    public int getAttributeList_Item(int iterator) {
        return buf.getInt(iterator&0x0000ffff);
    }

    /**
     * Prepares a data load to be added to the packet. This function sets up an AttributeData object
     * with a byte array, an offset to write to and a maximum length to be written. The data should
     * be placed in the returned object and addAttributeData is then called to actually add the data
     * to the packet. If addAttributeData is not called, nothing is changed in the packet. No other add
     * methods are allowed to be called on the packet between these two calls.
     * @param  dataType  the type of data
     * @return an empty AttributeData container to fill with the actual data
     * @see AttributeData
     */
    public AttributeData prepareAttributeData(int dataType) {
        attrData.setup(dataType, buf.array(), buf.position()+8, buf.remaining()-8);
        return attrData;
    }

    /**
     * Adds the data in the AttributeData object to the packet with the specified length in bytes.
     * The data parameter must be achieved from the prepareAttributeData method. The attribute type
     * is always Packet.ATTR_DATA.
     *
     * @param length  the length of the data in bytes
     * @see AttributeData
     * @throws IllegalArgumentException if the length is negative
     */
    public void addAttributeData(int length) {
        if(length<0) throw new IllegalArgumentException("Length is negative when adding data attribute");
        buf.putInt(Packet.ATTR_DATA|length+4).putInt(attrData.getDataType()).position(buf.position()+length);
    }

    /**
     * Returns the data attribute of the specified type as an AttributeData
     * container. The attribute type is always Packet.ATTR_DATA.
     * @return the attribute data container
     */
    public AttributeData getAttributeData() {
        int i = L_HEADER;
        while(i<this.buf.position()) {
            int type_len = this.buf.getInt(i);
            if( (type_len&0xffff0000) == Packet.ATTR_DATA ) {
                int dataType = this.buf.getInt(i+4);
                attrData.setup(dataType, this.buf.array(), i+8, (type_len&0x0000ffff)-4 );
                return attrData;
            }
            i += 4 + (type_len&0x0000ffff);
        }
        return null;
    }

    /**
     * Returns the embedded datagram packet. The host name is automatically
     * resolved and set up in the datatgram packet when this method is called.
     * @return the embedded datagram packet
     * @throws UnknownHostException
     */
    public DatagramPacket getDatagramPacket() throws UnknownHostException {
        InetAddress addr = socketAddr.getInetAddress();
        int port = socketAddr.getPort();
        datagramPacket.setAddress(addr);
        datagramPacket.setPort(port);
        datagramPacket.setLength(buf.position());
        return datagramPacket;
    }

    @Override
    public String toString() {
        return "P[s-nbr: "+getSequenceNbr()+" transaction: 0x"+Integer.toHexString(getTransactionId()) + ", type: 0x" + Integer.toHexString(getType())+"]";
    }

    // Helpers
    private void copyFrom(byte[] buf, int length) {
        // Copy the byte buffer
        this.buf.position(0);
        this.buf.put(buf,0,length);
    }

    // Static synchronized random function for generating transaction id's
    private static Object lock = new Object();
    private static Random rand = null;

    /**
     * Static transaction id generator
     * @return a new 32 bit transaction id
     */
    public static int getRandInt() {
        synchronized(lock) {
            if(rand==null) rand = new Random(randomSeed);
            return rand.nextInt();
        }
    }
}
