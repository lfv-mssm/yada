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

import com.lfv.yada.data.server.ServerChannel;
import com.lfv.yada.data.server.ServerTerminal;

public interface ServerNetworkHandler {

    public void terminalOnline(ServerTerminal terminal);
    public void terminalOffline(ServerTerminal terminal);

    public void terminalStarted(ServerTerminal terminal);
    public void terminalStopped(ServerTerminal terminal);

    public void radioAcquired(ServerTerminal terminal, ServerChannel channel, boolean success);
    public void radioReleased(ServerTerminal terminal, ServerChannel channel);

    public void phoneRing(ServerTerminal sourceTerminal, ServerTerminal destTerminal, int sourceRoleId, int destRoleId, boolean success);
    public void phoneAnswer(ServerTerminal sourceTerminal, ServerTerminal destTerminal, int sourceRoleId, int destRoleId, boolean success);
    public void phoneHangup(ServerTerminal sourceTerminal, ServerTerminal destTerminal, int sourceRoleId, int destRoleId);

}
