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
import com.lfv.yada.data.client.ClientChannel;
import com.lfv.yada.data.client.ClientTerminal;

public interface ClientNetworkHandler {

    public void serverConnected(boolean accepted);
    public void serverDisconnected();

    public void sessionStart();
    public void sessionStop();
    public void sessionClose();

    public void sessionConnected();
    public void sessionDisconnected();

    public void infoPacket(Packet packet);

    public void radioAcquired(ClientChannel channel, boolean accepted);
    public void radioReleased(ClientChannel channel);

    public void phoneRingOutgoing(ClientTerminal destTerminal, int destRoleId, int sourceRoleId, boolean accepted);
    public void phoneRingIncoming(ClientTerminal sourceTerminal, int sourceRoleId, int destRoleId);
    public void phoneAnswerOutgoing(ClientTerminal destTerminal, int destRoleId, int sourceRoleId, boolean accepted);
    public void phoneAnswerIncoming(ClientTerminal sourceTerminal, int sourceRoleId, int destRoleId);
    public void phoneHangupOutgoing(ClientTerminal destTerminal, int destRoleId, int sourceRoleId);
    public void phoneHangupIncoming(ClientTerminal sourceTerminal, int sourceRoleId, int destRoleId);

    public void timeout();

}
