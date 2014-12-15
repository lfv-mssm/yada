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

import com.lfv.yada.net.SocketAddress;

public class ServerTerminal {

    private int           terminalId;
    private int           sessionId;
    private int           callPeerTerminalId;
    private int[]         channels;
    private long          pingTime;
    private boolean       started;
    private SocketAddress socketAddress;

    public ServerTerminal(int terminalId) {
        this.terminalId = terminalId;
    }

    public int getId() {
        return terminalId;
    }

    public synchronized void setSocketAddress(SocketAddress socketAddress) {
        // Argh... Must copy this one because it comes from a packet
        // hence it is reused.. Sigh.. there went 6 hours...
        // ... and now its not even used anymore, se below...
        this.socketAddress = (socketAddress==null)?null:new SocketAddress(socketAddress);
    }
    public synchronized void setSocketAddress(int ip, int port) {
        this.socketAddress = new SocketAddress(ip, port);
    }
    public synchronized SocketAddress getSocketAddress() {
        return socketAddress;
    }
    public synchronized boolean isConnected() {
        return (socketAddress!=null);
    }

    public synchronized void setPingTime(long pingTime) {
        this.pingTime = pingTime;
    }
    public synchronized long getPingTime() {
        return pingTime;
    }
    public synchronized boolean isOnline() {
        return (pingTime>0);
    }

    public synchronized void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }
    public synchronized int getSessionId() {
        return sessionId;
    }

    public synchronized void setStarted(boolean started) {
        this.started = started;
    }
    public synchronized boolean isStarted() {
        return started;
    }

    public synchronized void setChannels(int[] channels) {
        this.channels = channels;
    }
    public synchronized int[] getChannels() {
        return channels;
    }

    public synchronized void setCallPeer(int terminalId) {
        this.callPeerTerminalId = terminalId;
    }
    public synchronized int getCallPeer() {
        return callPeerTerminalId;
    }
    public synchronized boolean isInCall() {
        return (callPeerTerminalId>0);
    }

}
