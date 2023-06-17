/**
 * This file is part of muCommander, http://www.mucommander.com
 * <p>
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mucommander.commons.file.protocol.gcs;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.StorageOptions;
import com.mucommander.commons.file.FileURL;
import com.mucommander.commons.runtime.OsFamily;
import com.mucommander.protocol.ui.ServerPanel;
import com.mucommander.protocol.ui.ServerPanelListener;

import javax.swing.*;
import java.awt.*;
import java.net.MalformedURLException;
import java.util.Objects;

/**
 * This ServerPanel helps initiate Google Cloud Storage connections.
 *
 * @author miroslav.spak
 */
public class GoogleCloudStoragePanel extends ServerPanel {

    private static final int Y_SPACE_AFTER_TEXT_FIELD = 15;
    static final String GCS_SCHEMA = "gcs";
    static final String GCS_DEFAULT_PROJECT_ID = "gcs_default_project_id";
    static final String GCS_CREDENTIALS_JSON = "gcs_credentials_json";
    static final String GCS_DEFAULT_CREDENTIALS = "gcs_default_credentials";
    static final String GCS_BUCKET_LOCATION = "gcs_bucket_location";
    static final String GCS_DEFAULT_BUCKET_LOCATION = "gcs_default_bucket_location";
    static final String GCS_IMPERSONATED_PRINCIPAL = "gcs_impersonated_principal";
    static final String GCS_IMPERSONATION = "gcs_impersonation";

    private final TextField projectIdField;
    private final TextField credentialsJsonPathField;
    private final TextField locationField;
    private final TextField impersonatedPrincipalField;

    private final JCheckBox defaultProjectIdCheckBox;
    private final JCheckBox defaultCredentialsCheckBox;
    private final JCheckBox defaultLocationCheckBox;
    private final JCheckBox impersonationCheckBox;

    // Store to static, so it is saved for the next instance
    private static final boolean gsUtilsDefaults = hasGsUtilsDefaults();
    private static String lastProjectId = gsUtilsDefaults ? StorageOptions.getDefaultProjectId() : "";
    private static String lastCredentialsJsonPath = "";
    private static String lastLocation = "";
    private static String lastImpersonatedPrincipal = "";
    private static boolean lastDefaultProjectId = gsUtilsDefaults;
    private static boolean lastDefaultCredentials = gsUtilsDefaults;
    private static boolean lastDefaultLocation = gsUtilsDefaults;
    private static boolean lastImpersonation = false;

    GoogleCloudStoragePanel(ServerPanelListener listener, JFrame mainFrame) {
        super(listener, mainFrame);

        // TODO use translator for descriptions
        var gsUtilsDefaults = hasGsUtilsDefaults();

        // Add all text fields
        projectIdField = addTextField("Project id", lastProjectId, !lastDefaultProjectId, true);
        credentialsJsonPathField = addFilePathChooser("Credentials json", lastCredentialsJsonPath, !lastDefaultCredentials);
        locationField = addTextField("Bucket location", lastLocation, !lastDefaultLocation, false);
        impersonatedPrincipalField = addTextField("Impersonated principal", lastImpersonatedPrincipal, lastImpersonation, false);

        // Add all check boxes
        defaultProjectIdCheckBox = addCheckBoxToTextField("Default project id", lastDefaultProjectId, gsUtilsDefaults, projectIdField, StorageOptions.getDefaultProjectId());
        defaultCredentialsCheckBox = addCheckBoxToTextField("Default credentials", lastDefaultCredentials, gsUtilsDefaults, credentialsJsonPathField, "");
        defaultLocationCheckBox = addCheckBoxToTextField("Default bucket location", lastDefaultLocation, gsUtilsDefaults, locationField, "");
        impersonationCheckBox = addCheckBoxToTextField("Impersonation", lastImpersonation, true, impersonatedPrincipalField, "");

        if (!gsUtilsDefaults) {
            // Missing GS utils warning
            JLabel warnLabel = new JLabel("To use defaults install gsutils!");
            warnLabel.setForeground(Color.red);
            addRow(warnLabel, 10);
        }
    }

    /**
     * Adds simple standard TextField to the Server connection panel.
     *
     * @param label     the label od the new textField
     * @param initValue initial value of this textField
     * @param enabled   if the textField should be enabled or not
     * @param updateUrl if the connection panel url should be updated for this textField
     * @return a new simple textField already added to the Server connection panel
     */
    private TextField addTextField(String label, String initValue, boolean enabled, boolean updateUrl) {
        var jTextField = new JTextField(initValue);
        jTextField.setEnabled(enabled);
        jTextField.selectAll();
        if (updateUrl) {
            // Add listener for the file url if needed
            addTextFieldListeners(jTextField, true);
        }
        addRow(label, jTextField, Y_SPACE_AFTER_TEXT_FIELD);

        return new TextField(jTextField);
    }

    /**
     * Adds TextField with the file chooser to the Server connection panel.
     *
     * @param label     the label of the new textField
     * @param initValue initial value of this textField
     * @param enabled   if the textField and the file chooser button should be enabled or not
     * @return a composite textField with path chooser added to the Server connection panel
     */
    private TextField addFilePathChooser(String label, String initValue, boolean enabled) {
        var fileChooserPanel = new JPanel(new BorderLayout());

        // Prepare text field
        var jTextField = new JTextField(initValue);
        jTextField.setEnabled(enabled);
        jTextField.selectAll();
        fileChooserPanel.add(jTextField, BorderLayout.CENTER);

        // Prepare button
        var chooseFileButton = new JButton("...");
        chooseFileButton.setEnabled(enabled);
        // Mac OS X: small component size
        if (OsFamily.MAC_OS.isCurrent())
            chooseFileButton.putClientProperty("JComponent.sizeVariant", "small");

        // Prepare file chooser
        var fileChooser = new JFileChooser(System.getProperty("user.home"));
        chooseFileButton.addActionListener(event -> {
            int returnVal = fileChooser.showOpenDialog(mainFrame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                jTextField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });
        fileChooserPanel.add(chooseFileButton, BorderLayout.EAST);

        addRow(label, fileChooserPanel, Y_SPACE_AFTER_TEXT_FIELD);

        return new TextField(jTextField) {
            @Override
            public boolean switchEnabled() {
                var isEnabled = super.switchEnabled();
                // Update also the button state to match the text field
                chooseFileButton.setEnabled(isEnabled);
                return isEnabled;
            }
        };
    }

    /**
     * Adds checkBox to the Server connection panel with the ability to switch text field "enabled" state and reset it
     * to the default value.
     *
     * @param label                 the label of the new checkBox
     * @param initValue             initial state of this checkBox
     * @param enabled               if the checkBox should be enabled or not
     * @param textField             controlled textField using this checkBox
     * @param textFieldDefaultValue the default value of the textField when it is disabled
     * @return a new checkBox (controlling textField) already added to the Server connection panel
     */
    private JCheckBox addCheckBoxToTextField(
            String label, boolean initValue, boolean enabled, TextField textField, String textFieldDefaultValue) {
        var checkBox = new JCheckBox(label, initValue);
        checkBox.setEnabled(enabled);
        checkBox.addActionListener(event -> {
            var textFieldEnabled = textField.switchEnabled();
            if (!textFieldEnabled) {
                // Revert the field to the default value if disabled
                textField.setText(textFieldDefaultValue);
            }
        });
        addRow("", checkBox, 5);
        return checkBox;
    }

    private void updateValues() {
        lastProjectId = projectIdField.getText();
        lastCredentialsJsonPath = credentialsJsonPathField.getText();
        lastImpersonatedPrincipal = impersonatedPrincipalField.getText();
        lastLocation = locationField.getText();
        lastDefaultProjectId = defaultProjectIdCheckBox.isSelected();
        lastDefaultCredentials = defaultCredentialsCheckBox.isSelected();
        lastDefaultLocation = defaultLocationCheckBox.isSelected();
        lastImpersonation = impersonationCheckBox.isSelected();
    }

    @Override
    public FileURL getServerURL() throws MalformedURLException {
        updateValues();

        var url = FileURL.getFileURL(String.format("%s://%s", GCS_SCHEMA, lastProjectId));

        url.setProperty(GCS_CREDENTIALS_JSON, lastCredentialsJsonPath);
        url.setProperty(GCS_BUCKET_LOCATION, lastLocation);
        url.setProperty(GCS_IMPERSONATED_PRINCIPAL, lastImpersonatedPrincipal);
        url.setProperty(GCS_DEFAULT_PROJECT_ID, Boolean.toString(lastDefaultProjectId));
        url.setProperty(GCS_DEFAULT_CREDENTIALS, Boolean.toString(lastDefaultCredentials));
        url.setProperty(GCS_DEFAULT_BUCKET_LOCATION, Boolean.toString(lastDefaultLocation));
        url.setProperty(GCS_IMPERSONATION, Boolean.toString(lastImpersonation));
        return url;
    }

    @Override
    public boolean usesCredentials() {
        return false;
    }

    @Override
    public void dialogValidated() {
        updateValues();
    }

    /**
     * Checks if the google-cloud library can find default credentials and default project id.
     * Typically signifying that the "gsUtils" are installed. It doesn't matter if the defaults were provided in
     * a different way, we are using only those two.
     */
    private static boolean hasGsUtilsDefaults() {
        try {
            // Test we can use default credentials and project id
            GoogleCredentials.getApplicationDefault();
            Objects.requireNonNull(StorageOptions.getDefaultProjectId());
            return true;
        } catch (Exception ex) {
            // Defaults does not exist
            return false;
        }
    }

    /**
     * Wrapper class for any composite UI elements containing text field.
     */
    private static class TextField {
        private final JTextField textField;

        public TextField(JTextField textField) {
            this.textField = textField;
        }

        /**
         * Switch enabled state of the composite text field.
         *
         * @return the final isEnabled state of the field
         */
        boolean switchEnabled() {
            textField.setEnabled(!textField.isEnabled());
            return textField.isEnabled();
        }

        void setText(String text) {
            textField.setText(text);
        }

        String getText() {
            return textField.getText();
        }
    }
}
