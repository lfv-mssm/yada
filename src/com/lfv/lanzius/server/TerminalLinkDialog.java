package com.lfv.lanzius.server;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ComboBoxModel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.ListModel;
import org.jdom.Document;

/**
 * <p>
 * TerminalLinkDialog
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
public class TerminalLinkDialog extends JDialog implements ActionListener {

    private TerminalLinkPanel panel;
    private ComboBoxModel   terminalModel;
    private ComboBoxModel   groupModel;
    private ListModel       playerModel;
    private boolean buttonOkPressed;

    public TerminalLinkDialog(JFrame owner, Document doc, int terminalId) {
        super(owner, "Terminal Link...", true);
        buttonOkPressed = false;
        terminalModel = new DocumentComboBoxModel(doc, "TerminalDefs", "Terminal", "T/", terminalId);
        groupModel    = new DocumentComboBoxModel(doc, "GroupDefs", "Group", "G/", 0);
        playerModel   = new DocumentListModel(doc, "PlayerDefs", "Player", "P/");
        panel         = new TerminalLinkPanel(this, terminalModel, groupModel, playerModel, terminalId>0);
        setContentPane(panel);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
        JRootPane rp = getRootPane();
        if(rp!=null) rp.setDefaultButton(panel.getDefaultButton());
    }

    public boolean showDialog() {
        setVisible(true);
        if(terminalModel.getSelectedItem()==null)
            return false;
        if(groupModel.getSelectedItem()==null)
            return false;
        return buttonOkPressed;
    }

    public int getSelectedTerminalId() {
        DocumentModelItem item = (DocumentModelItem)terminalModel.getSelectedItem();
        return item.getId();
    }

    public int getSelectedGroupId() {
        DocumentModelItem item = (DocumentModelItem)groupModel.getSelectedItem();
        return item.getId();
    }

    public int[] getSelectedPlayerIdArray() {
        Object[] oa = panel.getPlayerList().getSelectedValues();
        if(oa==null) return new int[0];
        int[] ia = new int[oa.length];
        for(int i=0;i<oa.length;i++)
            ia[i] = ((DocumentModelItem)oa[i]).getId();
        return ia;
    }

    public void actionPerformed(ActionEvent e) {
        buttonOkPressed = e.getActionCommand().equals("OK");
        setVisible(false);
    }
}
