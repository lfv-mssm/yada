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
 * TerminalUnlinkDialog
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
public class TerminalUnlinkDialog extends JDialog implements ActionListener {

    private ComboBoxPanel panel;
    private ComboBoxModel terminalModel;
    private boolean buttonOkPressed;

    public TerminalUnlinkDialog(JFrame owner, Document doc) {
        super(owner, "Terminal Unlink...", true);
        buttonOkPressed = false;
        terminalModel = new DocumentComboBoxModel(doc, "TerminalDefs", "Terminal", "T/", 0);
        panel = new ComboBoxPanel("Terminal", this, terminalModel);
        setContentPane(panel);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
        JRootPane rp = getRootPane();
        if(rp!=null) rp.setDefaultButton(panel.getDefaultButton());
    }

    public int showDialog() {
        setVisible(true);
        DocumentModelItem item = (DocumentModelItem)terminalModel.getSelectedItem();
        if(item==null) return 0;
        return buttonOkPressed?item.getId():0;
    }

    public void actionPerformed(ActionEvent e) {
        buttonOkPressed = e.getActionCommand().equals("OK");
        setVisible(false);
    }
}
