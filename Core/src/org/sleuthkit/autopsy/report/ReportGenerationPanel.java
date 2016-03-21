/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.report;

import java.awt.*;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.Box;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;

class ReportGenerationPanel extends javax.swing.JPanel {

    private GridBagConstraints c;
    ReportProgressPanel progressPanel;
    private Component glue;
    private ActionListener actionListener;

    /**
     * Creates new form ReportGenerationPanel
     */
    public ReportGenerationPanel() {
        initComponents();
        customInit();
    }

    private void customInit() {
        reportPanel.setLayout(new GridBagLayout());
        c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1.0;
        glue = Box.createVerticalGlue();
        
    }

    /**
     * Add a ReportProgressPanel to this panel with the given report name and
     * path.
     *
     * @param reportName report name
     * @param reportPath report path
     *
     * @return ReportProgressPanel progress panel to update
     */
    public ReportProgressPanel addReport(String reportName, String reportPath) {
        // Remove the glue
        reportPanel.remove(glue);

        progressPanel = new ReportProgressPanel(reportName, reportPath);
        c.weighty = 0.0;
        c.anchor = GridBagConstraints.NORTH;
        reportPanel.add(progressPanel, c);
        c.gridy++;

        // Add the glue back to the bottom
        c.weighty = 1.0;
        c.anchor = GridBagConstraints.PAGE_END;
        reportPanel.add(glue, c);

        // 80 px per progressPanel.
        reportPanel.setPreferredSize(new Dimension(600, 1 * 80));
        reportPanel.repaint();
        progressPanel.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            String propName = evt.getPropertyName();
            if (propName.equals(ReportProgressPanel.Events.COMPLETED.toString())) {
                SwingUtilities.invokeLater(() -> {
                    cancelButton.setEnabled(false);
                });
            }
        });
        return progressPanel;
    }

    /**
     * Close this panel and it's dialog if all reports are done.
     */
    void close() {
        boolean closeable = true;
        if (progressPanel.getStatus() != ReportStatus.CANCELED && progressPanel.getStatus() != ReportStatus.COMPLETE && progressPanel.getStatus() != ReportStatus.ERROR) {
            closeable = false;
        }
        if (closeable) {
            actionListener.actionPerformed(null);
        } else {
            int result = JOptionPane.showConfirmDialog(null,
                    NbBundle.getMessage(this.getClass(),
                            "ReportGenerationPanel.confDlg.sureToClose.msg"),
                    NbBundle.getMessage(this.getClass(),
                            "ReportGenerationPanel.confDlg.title.closing"),
                    JOptionPane.YES_NO_OPTION);
            if (result == 0) {
                progressPanel.cancel();
                actionListener.actionPerformed(null);
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        closeButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        reportScrollPane = new javax.swing.JScrollPane();
        reportPanel = new javax.swing.JPanel();
        titleLabel = new javax.swing.JLabel();
        titleSeparator = new javax.swing.JSeparator();
        optionSeparator = new javax.swing.JSeparator();

        setFont(getFont().deriveFont(getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        setPreferredSize(new java.awt.Dimension(700, 400));

        closeButton.setFont(closeButton.getFont().deriveFont(closeButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(closeButton, org.openide.util.NbBundle.getMessage(ReportGenerationPanel.class, "ReportGenerationPanel.closeButton.text")); // NOI18N
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        cancelButton.setFont(cancelButton.getFont().deriveFont(cancelButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(ReportGenerationPanel.class, "ReportGenerationPanel.cancelButton.text")); // NOI18N
        cancelButton.setActionCommand(org.openide.util.NbBundle.getMessage(ReportGenerationPanel.class, "ReportGenerationPanel.cancelButton.actionCommand")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        reportScrollPane.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.SystemColor.activeCaptionBorder));
        reportScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        reportScrollPane.setFont(reportScrollPane.getFont().deriveFont(reportScrollPane.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        reportPanel.setFont(reportPanel.getFont().deriveFont(reportPanel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        reportPanel.setPreferredSize(new java.awt.Dimension(600, 400));

        javax.swing.GroupLayout reportPanelLayout = new javax.swing.GroupLayout(reportPanel);
        reportPanel.setLayout(reportPanelLayout);
        reportPanelLayout.setHorizontalGroup(
            reportPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 661, Short.MAX_VALUE)
        );
        reportPanelLayout.setVerticalGroup(
            reportPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );

        reportScrollPane.setViewportView(reportPanel);

        titleLabel.setFont(titleLabel.getFont().deriveFont(titleLabel.getFont().getStyle() | java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(titleLabel, org.openide.util.NbBundle.getMessage(ReportGenerationPanel.class, "ReportGenerationPanel.titleLabel.text")); // NOI18N

        titleSeparator.setForeground(new java.awt.Color(0, 0, 0));

        optionSeparator.setForeground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(optionSeparator)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(reportScrollPane)
                    .addComponent(titleSeparator, javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addComponent(titleLabel)
                        .addGap(0, 522, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 546, Short.MAX_VALUE)
                        .addComponent(cancelButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(closeButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(titleLabel)
                .addGap(0, 0, 0)
                .addComponent(titleSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(reportScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 290, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addComponent(optionSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(closeButton)
                    .addComponent(cancelButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        close();
    }//GEN-LAST:event_closeButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        if (progressPanel.getStatus() == ReportStatus.QUEUING || progressPanel.getStatus() == ReportStatus.RUNNING) {
            int result = JOptionPane.showConfirmDialog(null, NbBundle.getMessage(this.getClass(),
                    "ReportGenerationPanel.confDlg.cancelReport.msg"),
                    NbBundle.getMessage(this.getClass(),
                            "ReportGenerationPanel.cancelButton.text"),
                    JOptionPane.YES_NO_OPTION);
            if (result == 0) {
                progressPanel.cancel();
            }
        }
    }//GEN-LAST:event_cancelButtonActionPerformed

    void addCloseAction(ActionListener l) {
        this.actionListener = l;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton closeButton;
    private javax.swing.JSeparator optionSeparator;
    private javax.swing.JPanel reportPanel;
    private javax.swing.JScrollPane reportScrollPane;
    private javax.swing.JLabel titleLabel;
    private javax.swing.JSeparator titleSeparator;
    // End of variables declaration//GEN-END:variables

}
