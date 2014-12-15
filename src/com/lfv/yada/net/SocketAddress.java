package com.lfv.yada.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * <p>
 * The SocketAddress represents an ip address and a tcp or udp port.
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
public class SocketAddress {
    private int address;
    private int port;

    /**
     * Creates an undefined socket address
     */
    public SocketAddress() {
    }

    /**
     * Creates a socket address from another socket address
     */
    public SocketAddress(SocketAddress socket) {
        setAddress(socket);
    }

    /**
     * Creates a socket address from an address and a port
     * @param  address an ip address in 32-bit int format
     */
    public SocketAddress(int address, int port) {
        setAddress(address,port);
    }

    /**
     * Creates a socket address from an address and a port
     * @param  ipAddress an ip address as an InetAddress object
     */
    public SocketAddress(InetAddress ipAddress, int port) {
        setAddress(ipAddress,port);
    }

    /**
     * Creates a socket address from an address and a port
     * @param  name an ip address as a String
     */
    public SocketAddress(String name, int port) throws UnknownHostException {
        this.port = 0;
        this.address = 0;
        if(name!=null&&port>0) {
            InetAddress ia = InetAddress.getByName(name);
            byte[] b = ia.getAddress();
            this.address = convertBytesToInt(b);
            this.port = port;
        }
    }

    /**
     * Sets the socket address from another socket address
     */
    public void setAddress(SocketAddress socket) {
        this.address = socket.address;
        this.port = socket.port;
    }

    /**
     * Sets the socket address from an address and a port
     * @param  address an ip address in 32-bit int format
     */
    public void setAddress(int address, int port) {
        this.address = address;
        this.port = port;
    }

    /**
     * Sets the socket address from an address and a port
     * @param  address an ip address as an InetAddress object
     */
    public void setAddress(InetAddress address, int port) {
        byte[] ipb = address.getAddress();
        this.address = convertBytesToInt(ipb);
        this.port = port;
    }

    /**
     * Gets the address part from the socket address
     * @return the ip address in the socket address as a 32-bit value
     */
    public int getAddress() {
        return address;
    }

    /**
     * Gets the port from the socket address
     * @return the port in this socket address
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the address part from the socket address
     * @return the ip address in the socket address as an InetAddress object
     */
    public InetAddress getInetAddress() throws UnknownHostException {
        if(address==0||port==0) return null;
        return InetAddress.getByAddress(convertIntToBytes(address));
    }

    @Override
    public String toString() {
        byte[] ipb = convertIntToBytes(address);
        return (ipb[0]&0xff)+"."+(ipb[1]&0xff)+"."+(ipb[2]&0xff)+"."+(ipb[3]&0xff)+":"+port;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof SocketAddress)) return false;
        SocketAddress otherSocketAddr = (SocketAddress)obj;
        return ((otherSocketAddr.address==this.address)&&(otherSocketAddr.port==this.port));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + this.address;
        hash = 97 * hash + this.port;
        return hash;
    }

    // Helpers
    private int convertBytesToInt(byte[] b) {
        return
                ((((int)b[0])<<24)&0xff000000) |
                ((((int)b[1])<<16)&0xff0000) |
                ((((int)b[2])<<8)&0xff00) |
                ((((int)b[3]))&0xff);
    }
    private byte[] convertIntToBytes(int i) {
        byte[] b = new byte[4];
        b[0] = (byte)((i >> 24) & 0xff);
        b[1] = (byte)((i >> 16) & 0xff);
        b[2] = (byte)((i >>  8) & 0xff);
        b[3] = (byte)((i      ) & 0xff);
        return b;
    }
}
