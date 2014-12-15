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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Calendar;
import java.util.TimeZone;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ServerLogger {

    private Log log;
    private Calendar calendar;
    private PrintStream printer;
    private long startTime;
    private long suspendTime;
    private long resumeTime;
    private String mylogpath;

    public ServerLogger(int groupId, String logpath) {
        // Create a logger for this class
        if (logpath == null) {
           logpath = "data/logs/";
        }
mylogpath = logpath;
        log = LogFactory.getLog(getClass());
        log.info("ServerLogger: logpath=" + logpath);

        // Create a calendar
        startTime = System.currentTimeMillis();
        calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(startTime);
        String filename = "Grp"+groupId+"-"+calendar.get(Calendar.YEAR) +
                s2(calendar.get(Calendar.MONTH)+1) +
                s2(calendar.get(Calendar.DAY_OF_MONTH)) + "-" +
                s2(calendar.get(Calendar.HOUR_OF_DAY)) +
                s2(calendar.get(Calendar.MINUTE))+
                s2(calendar.get(Calendar.SECOND))+".log";
        try {
            printer = new PrintStream(new FileOutputStream(logpath+"/"+filename), true);

            log.info("Creating log "+logpath+"/"+filename);
        } catch(FileNotFoundException ex) {
            log.warn("Log file "+filename+" could not be created, logger has been disabled!", ex);
            printer = null;
        }
    }

    public synchronized void close() {
        printer.close();
        printer = null;
        suspendTime = 0;
    }

    public synchronized void suspend() {
        suspendTime = System.currentTimeMillis();
    }

    public synchronized void resume() {
        if(suspendTime>0) {
            startTime += (System.currentTimeMillis()-suspendTime);
            suspendTime = 0;
        }
    }

    public synchronized void resume(int h, int m, int s) {
        // Create a calendar
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, s);
        resumeTime = cal.getTimeInMillis();
        startTime = System.currentTimeMillis();
        suspendTime = 0;
    }

    public synchronized void print(String s) {
        if(printer!=null && s!=null && suspendTime==0) {
            long t = System.currentTimeMillis();

            // UTC time
            calendar.setTimeInMillis(t);
            String st = "UTC "+
                    s2(calendar.get(Calendar.HOUR_OF_DAY))+":"+
                    s2(calendar.get(Calendar.MINUTE))+":"+
                    s2(calendar.get(Calendar.SECOND))+"."+
                    s3(calendar.get(Calendar.MILLISECOND));

            // Simulation time
            calendar.setTimeInMillis(resumeTime + t - startTime);
            st += " SIM "+
                    s2(calendar.get(Calendar.HOUR_OF_DAY))+":"+
                    s2(calendar.get(Calendar.MINUTE))+":"+
                    s2(calendar.get(Calendar.SECOND))+"."+
                    s3(calendar.get(Calendar.MILLISECOND));

            // Print it to the output logfile
            printer.println( st + " | " + s );
        }
    }

    public long getSimTime() {
        return (System.currentTimeMillis()-startTime);
    }
    public String getLogPath() {
        return mylogpath;
    }

    private String s2(int value) {
        if(value<10)
            return "0"+value;
        else
            return String.valueOf(value);
    }
    private String s3(int value) {
        String outpStr;
        outpStr=String.format("%03d",value);
        return outpStr;
    }
}
