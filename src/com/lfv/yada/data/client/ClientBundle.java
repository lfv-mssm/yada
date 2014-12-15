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

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ClientBundle {
        private List<ClientChannel> channels;

        public ClientBundle() {
                channels = new LinkedList<ClientChannel>();
        }

        public synchronized void addChannel(ClientChannel channel) {
                if (channel != null) {
                        channels.add(channel);
                }
        }

        public synchronized ClientChannel getChannel(int channelId) {
                Iterator<ClientChannel> iter = channels.iterator();
                while (iter.hasNext()) {
                        ClientChannel c = iter.next();
                        if (c.getId() == channelId) {
                                return c;
                        }
                }

                return null;
        }

        public synchronized Collection<ClientChannel> getChannelCollection() {
                return channels;
        }

        public synchronized void clear() {
                channels.clear();
        }

        public synchronized ClientTerminal getTerminal(int terminalId) {
                Iterator<ClientChannel> iter = channels.iterator();
                while (iter.hasNext()) {
                        ClientChannel c = iter.next();
                        ClientTerminal t = c.getTerminal(terminalId);
                        if (t != null)
                                return t;
                }

                return null;
        }
}
