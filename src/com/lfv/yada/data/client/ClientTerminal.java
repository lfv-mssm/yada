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
package com.lfv.yada.data.client;

import com.lfv.yada.net.SocketAddress;

public class ClientTerminal {

    private int           terminalId;
    private SocketAddress socketAddress;
    private boolean       unicastConnection;
    private boolean       multicastConnection;

    public ClientTerminal(int terminalId) {
        this.terminalId = terminalId;
    }

    public int getId() {
        return terminalId;
    }

    public synchronized void setSocketAddress(int ip, int port) {
        this.socketAddress = new SocketAddress(ip, port);
    }
    public synchronized SocketAddress getSocketAddress() {
        return socketAddress;
    }

    public synchronized void setUnicastConnection(boolean unicastConnection) {
        this.unicastConnection = unicastConnection;
    }
    public synchronized boolean hasUnicastConnection() {
        return unicastConnection;
    }

    public synchronized void setMulticastConnection(boolean multicastConnection) {
        this.multicastConnection = multicastConnection;
    }
    public synchronized boolean hasMulticastConnection() {
        return multicastConnection;
    }

    public synchronized boolean hasConnection() {
        return (unicastConnection || multicastConnection);
    }

}
