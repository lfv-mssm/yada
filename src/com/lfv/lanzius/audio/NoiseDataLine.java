package com.lfv.lanzius.audio;

/**
 * <p>
 * NoiseDataLine
 * <p>
 * Copyright &copy; LFV 2008, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2.1.4 (Lanzius)
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
public class NoiseDataLine extends TargetDataLineAdapter {

    @Override
    public int read(byte[] b, int off, int len) {
        for(int i=0;i<len;i++) {
            b[off+i] = (byte)(Math.random()*15);
        }
        return len;
    }

    @Override
    public int available() {
        // Always one packet available
        return 640;
    }
}
