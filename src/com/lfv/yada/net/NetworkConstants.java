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
package com.lfv.yada.net;

public interface NetworkConstants {

    public final int PING_PERIOD          = 10000;

    public final int NOPING_TIMEOUT       = 18000;
    public final int TIMEOUT_CHECK_PERIOD =  5000;

    public final int TRANSACTION_TIMEOUT  =   950;
    public final int TRANSACTION_RETRIES  =     9;

    public final int ISA_TRANSACTION_TIMEOUT = 10000;

}
