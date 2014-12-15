package com.lfv.lanzius.application;

/**
 * <p>
 * ViewEventHandler
 * <p>
 * Copyright &copy; LFV 2007, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2.0 (Lanzius)
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
public interface ViewEventHandler {
    public void rxtxStateUpdated(int channelId, boolean rx, boolean tx);
    public void recordStateUpdated(int channelId, boolean recording);
    public void channelButtonPressed(int channelId);
    public void channelButtonReleased(int channelId);
    public void talkButtonPressed(int device);
    public void talkButtonReleased(int device);
    public void dialButtonClicked(int roleId, int peerId);
    public void hookButtonClicked();
    public void settingsDialogOpen();
    public void settingsDialogClose();
    public void settingsValueChanged(int id, int value);
    public void isaValueChosen(int value);
    public void closeButtonClicked();
}
