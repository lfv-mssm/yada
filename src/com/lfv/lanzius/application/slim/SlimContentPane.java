package com.lfv.lanzius.application.slim;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;

/**
 * <p>
 * SlimContentPane
 * <p>
 * Copyright &copy; LFV 2007, <a href="http://www.lfv.se">www.lfv.se</a>
 *
 * @author <a href="mailto:andreas@verido.se">Andreas Alptun</a>
 * @version Yada 2 (Lanzius)
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
public interface SlimContentPane {

    public JComboBox getRadioComboBox();
    public JButton getStateButton();
    public JButton getBusyButton();
    public JButton getTalkButton();
    public JComboBox getRoleComboBox();
    public JComboBox getPeerComboBox();
    public JButton getDialButton();
    public JButton getHookButton();
    public JButton getSettingsButton();
    public JLabel  getStatusLabel();

}
