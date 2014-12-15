package com.lfv.yada.net;

import com.arkatay.yada.base.ItemDispatcher;

/**
 * <p>
 * The PacketBlockDispatcher inherits from ItemDispatcher and implements the
 * threadMain method by waiting for a packet on the queue and calling the
 * abstract method dispatchPacket when a packet becomes available.
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
public abstract class PacketBlockDispatcher extends ItemDispatcher {

    public PacketBlockDispatcher(String name) {
        super(name + "-blockdisp");
    }

    protected boolean threadMain() throws InterruptedException {
        waitItem();
        Packet packet = (Packet)getItem();
        if(packet==null) {
            log.error("Null packet in queue");
            return false;
        }
        dispatchPacket(packet);
        return true;
    }

    protected abstract void dispatchPacket(Packet packet);
}
