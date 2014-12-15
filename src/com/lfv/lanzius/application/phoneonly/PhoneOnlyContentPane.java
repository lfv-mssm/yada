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
/*
 * SlimHorizContentPane.java
 *
 * Created on den 30 juli 2007, 14:39
 */

package com.lfv.lanzius.application.phoneonly;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 *
 * @author  Roalt Aalmoes
 */
public class PhoneOnlyContentPane extends javax.swing.JPanel {
        /**
         *
         */
        private static final long serialVersionUID = 4837230836816153422L;

        private javax.swing.JLabel jDialLabel;
        private javax.swing.JButton jButtonInterPhone;
        private javax.swing.JLabel jRoleLabel;
        private javax.swing.JPanel jPanelPeers;
        int peers = 0;

        /** Creates new form SlimHorizContentPane */
    public PhoneOnlyContentPane() {
        initComponents();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     *
     */

    private void initComponents() {

        GridBagLayout phoneOnlyGridBag = new GridBagLayout();
        phoneOnlyGridBag.columnWidths = new int[]{150,150,244,150,0};
        phoneOnlyGridBag.rowHeights = new int[]{92,0};
        phoneOnlyGridBag.columnWeights = new double[]{0.0,0.0,1.0,0.0,Double.MIN_VALUE};
        phoneOnlyGridBag.rowWeights = new double[]{0.0, Double.MIN_VALUE};
        this.setLayout(phoneOnlyGridBag);

        jRoleLabel = new javax.swing.JLabel();
        jRoleLabel.setFocusable(false);
        jRoleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        jRoleLabel.setText("(role)"); // FIXME replace by real role
        GridBagConstraints roleGBC = createGridBagConstraints(0,0);
        this.add(jRoleLabel,roleGBC);

        jDialLabel = new javax.swing.JLabel();
        jDialLabel.setHorizontalAlignment(SwingConstants.CENTER);

        jDialLabel.setBackground(java.awt.Color.lightGray);
        jDialLabel.setText("DIAL");
        jDialLabel.setFocusable(false);
        GridBagConstraints dialGBC = createGridBagConstraints(1, 0);
        this.add(jDialLabel,dialGBC);

        jPanelPeers = new javax.swing.JPanel();
        //setBorder(javax.swing.BorderFactory.createTitledBorder("Phone"));
        jPanelPeers.setLayout(new GridLayout(0, 5, 0, 0));
        GridBagConstraints peersGBC = createGridBagConstraints(2, 0);
        add(jPanelPeers, peersGBC);

        jButtonInterPhone = new javax.swing.JButton();
        jButtonInterPhone.setBackground(java.awt.Color.lightGray);
        jButtonInterPhone.setText("INTERPHONE");
        jButtonInterPhone.setFocusPainted(false);
        jButtonInterPhone.setFocusable(false);
        GridBagConstraints interPhoneGBC = createGridBagConstraints(3, 0);
        add(jButtonInterPhone, interPhoneGBC);


    }

        private GridBagConstraints createGridBagConstraints(int gridX, int gridY) {
                GridBagConstraints roleGBC = new GridBagConstraints();
        roleGBC.fill = GridBagConstraints.BOTH;
        roleGBC.insets = new Insets(0,0,0,0);
        roleGBC.gridx = gridX;
        roleGBC.gridy = gridY;
                return roleGBC;
        }

    public JLabel getRoleJLabel() {
        return jRoleLabel;
    }

    public JLabel getDialLabel() {
        return jDialLabel;
    }

    public JButton getHookButton() {
        return jButtonInterPhone;
    }

    public void setRole(String role) {
        jRoleLabel.setText(role);
    }
    /**
     * Make sure the right layout is chosen for the buttons
     * @param i
     */
    private void setNumberOfPeers(int i) {
        jPanelPeers.setLayout(new GridLayout(0, i, 0, 0));
    }



        public void removeAllPeers() {
                jPanelPeers.removeAll();
                peers = 0;
        }


        public void addPeerJButton(JButton peerCallButton) {
                ++peers;
                setNumberOfPeers(peers);
                jPanelPeers.add(peerCallButton);
        }
}
