package com.arkatay.yada.base;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <p>
 * The Time class finds and uses the most precise timer available in the system.
 * The init function MUST be called before getting time!
 * <p>
 * Copyright &copy; LFV 2006, <a href="http://www.lfv.se">www.lfv.se</a>
 *
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
public class Time {

    private static long initTimeMillis;
    private static boolean useNanoTimeMethod;

    /**
     * Initializes the time system
     *
     */
    public static void init() {
        // Create a logger for this class
        Log log = LogFactory.getLog(Time.class);

        // try the nanoTime method
        useNanoTimeMethod=true;
        try {
            System.nanoTime();
        }
        catch(NoSuchMethodError err) {
            initTimeMillis = System.currentTimeMillis();
            useNanoTimeMethod = false;
            log.warn("!!!");
            log.warn("!!! The java runtime does not support the System.nanoTime method and this means that the");
            log.warn("!!! timer accuracy on some computer may be too low for the codec to function properly!");
            log.warn("!!! Consider using JRE 1.5.x or higher which supports the nanoTime method");
            log.warn("!!!");
        }
    }

    /**
     * Returns the current value of the most precise available system timer in
     * nanoseconds. This method can only be used to measure elapsed time and is
     * related to any other notion of system or wall-clock time. The value
     * returned represents nanoseconds since some fixed but arbitrary time
     * (perhaps in the future, so values may be negative).
     *
     * @return current value of the most precise available system timer in nanoseconds
     */
    public static long getNanos() {
        if(useNanoTimeMethod)
            return System.nanoTime();
        return (System.currentTimeMillis()-initTimeMillis)*1000000L;
    }
}
