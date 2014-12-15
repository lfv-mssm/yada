package com.lfv.lanzius.server;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ComboBoxModel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import org.jdom.Document;

/**
 * <p>
 * TerminalSwapDialog
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
public class TerminalSwapDialog extends JDialog implements ActionListener {

    private TerminalSwapPanel panel;
    private ComboBoxModel terminal1Model;
    private ComboBoxModel terminal2Model;
    private boolean buttonOkPressed;

    public TerminalSwapDialog(JFrame owner, Document doc) {
        super(owner, "Terminal Swap...", true);
        buttonOkPressed = false;
        terminal1Model = new DocumentComboBoxModel(doc, "TerminalDefs", "Terminal", "T/", 0);
        terminal2Model = new DocumentComboBoxModel(doc, "TerminalDefs", "Terminal", "T/", 0);
        panel          = new TerminalSwapPanel(this, terminal1Model, terminal2Model);
        setContentPane(panel);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
        JRootPane rp = getRootPane();
        if(rp!=null) rp.setDefaultButton(panel.getDefaultButton());
    }

    public boolean showDialog() {
        setVisible(true);
        if(terminal1Model.getSelectedItem()==null)
            return false;
        if(terminal2Model.getSelectedItem()==null)
            return false;
        return buttonOkPressed;
    }

    public int[] getSelectedTerminals() {
        int[] ids = { 0, 0 };
        DocumentModelItem item1 = (DocumentModelItem)terminal1Model.getSelectedItem();
        if(item1!=null) ids[0] = item1.getId();
        DocumentModelItem item2 = (DocumentModelItem)terminal2Model.getSelectedItem();
        if(item2!=null) ids[1] = item2.getId();
        return ids;
    }

    public void actionPerformed(ActionEvent e) {
        buttonOkPressed = e.getActionCommand().equals("OK");
        setVisible(false);
    }
}
