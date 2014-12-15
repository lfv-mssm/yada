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
package com.lfv.lanzius.application;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;

/**
 *
 * @author  Mikael Björk
 */
public class ISAPane extends javax.swing.JPanel implements ActionListener {

    /**
         *
         */
        private static final long serialVersionUID = 1L;

        private ViewEventHandler handler;

        private javax.swing.JTextArea text;

        private JButton jButton[] = new JButton[10];
        private int numISAButtons;

    /** Creates new form ISAPane */
    public ISAPane(ViewEventHandler handler, int isaNumChoices, boolean extendedMode, String isakeytext[]) {

        numISAButtons = isaNumChoices;
        for (int i = 0; i < isaNumChoices; i++) {
            jButton[i] = new JButton(isakeytext[i]);
        }

        initComponents(isaNumChoices, isakeytext, extendedMode);
        this.handler = handler;

        for (int i = 0; i < isaNumChoices; i++) {
            jButton[i].addActionListener(this);
        }

        this.setBackground(Color.YELLOW);
    }

    private void initComponents(int isaNumChoices, String isakeytext[], boolean extendedMode) {
        java.awt.GridBagConstraints gridBagConstraints;

        text = new javax.swing.JTextArea("Current workload");
        text.setEditable(false);
        text.setOpaque(false);

        setLayout(new java.awt.GridBagLayout());

        setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 1;
        gridBagConstraints.weighty = 0;
        if (extendedMode) {
                gridBagConstraints.gridwidth = 2;
        } else {
                gridBagConstraints.gridx = 2;
                gridBagConstraints.gridwidth = 4;
        }
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(0,0,0,0);
        add(text, gridBagConstraints);
        for (int i = 0; i < isaNumChoices; i++){
            jButton[i].setBackground(java.awt.Color.lightGray);
            jButton[i].setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
            jButton[i].setFocusPainted(false);
            jButton[i].setMaximumSize(new java.awt.Dimension(0, 0));
            jButton[i].setMinimumSize(new java.awt.Dimension(150, 100));
            jButton[i].setPreferredSize(new java.awt.Dimension(150, 100));
            gridBagConstraints = new java.awt.GridBagConstraints();
            if ( (isaNumChoices > 6) &&  (isaNumChoices <= 8)) { // 7 or 8 choices -> 2 lines
               if (i <= 3) {
                  gridBagConstraints.gridx = i;
                  gridBagConstraints.gridy = 1;
               } else {
                  gridBagConstraints.gridx = i-4;
                  gridBagConstraints.gridy = 2;
               }
            } else if (isaNumChoices >= 9 ) { // >= 9 choices -> 3 lines
               if (i <= 2 ) {
                   gridBagConstraints.gridx = i;
                   gridBagConstraints.gridy = 1;
               } else if (i <= 5) {
                   gridBagConstraints.gridx = i-3;
                   gridBagConstraints.gridy = 2;
               } else {
                   gridBagConstraints.gridx = i-6;
                   gridBagConstraints.gridy = 3;
               }
            } else { // single line
               gridBagConstraints.gridx = i;
               gridBagConstraints.gridy = 1;
            }
/*
            if (extendedMode && (i >= 4) ) {
                gridBagConstraints.gridx = i - 4;
                gridBagConstraints.gridy = 2;
            } else {
                gridBagConstraints.gridx = i;
                gridBagConstraints.gridy = 1;
            }
*/
            gridBagConstraints.weightx = 1;
            gridBagConstraints.weighty = 1;
            gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
            gridBagConstraints.insets = new java.awt.Insets(1,1,1,1);

            add(jButton[i], gridBagConstraints);
        }
    }

    public void actionPerformed(ActionEvent e) {


        for (int i = 0; i < numISAButtons; i++){

            if (e.getActionCommand() == jButton[i].getText()) {
                handler.isaValueChosen(i + 1) ;
                return;
            }
        }
    }
}
