package com.lfv.yada.net;

import com.lfv.yada.net.Packet;

/**
 * <p>
 * The RequestHandler is used by the session manager to handle an incoming
 * request packet. This interface is implemented by many internal private
 * classes in the ClientNetworkManager and the ServerNetworkManager.
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
public interface RequestHandler {
    /**
     * Handles an incoming request packet
     * @param  requestPacket the incoming request packet
     */
    public void handleRequest(Packet requestPacket);
}
