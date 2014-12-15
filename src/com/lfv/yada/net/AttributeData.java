package com.lfv.yada.net;

/**
 * <p>
 * Each packet contains an AttributeData structure that acts as a container
 * for the data attribute.
 * <p>
 * Copyright &copy; LFV 2006, <a href="http://www.lfv.se">www.lfv.se</a>
 * @author  Andreas Alptun
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
 * @see     Packet
 */
public class AttributeData {

    private int dataType;
    private byte[] buf;
    private int offset;
    private int length;

    public AttributeData() {
        setup(0, null, 0, 0);
    }
    public AttributeData(int dataType, byte[] buf, int offset, int length) {
        setup(dataType, buf, offset, length);
    }

    public void setup(int dataType, byte[] buf, int offset, int length) {
        this.dataType = dataType;
        this.buf = buf;
        this.offset = offset;
        this.length = length;
    }

    public int getDataType() {
        return dataType;
    }
    public byte[] getBuffer() {
        return buf;
    }
    public int getOffset() {
        return offset;
    }
    public int getLength() {
        return length;
    }
}
