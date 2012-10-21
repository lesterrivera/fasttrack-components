package org.fasttrack.blth;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import com.netegrity.imapi.BLTHAdapter;
import com.netegrity.imapi.BLTHContext;
import com.netegrity.sdk.apiutil.SmApiException;
import com.netegrity.llsdk6.imsapi.managedobject.User;
import com.netegrity.ims.exception.IMSException;
import java.util.Hashtable;
import com.netegrity.rtl.jce.JSafeTools;
/**
 * Generates a unique password and assigns it to the user. An optional feature allows you 
 * to establish an encrypted default password to assign to all users. In addition, an 
 * override mechanism is supported when a value is set to the %PASSWORD% and |passwordConfirm|
 * attribute in the userform.
 * <p>
 * This class uses all built-in methods available by CA Identity Manager API. The
 * design allows the system to function properly without external encryption libraries.
 * <h3>User-Defined Properties</h3>
 * <ul>
 * <li><b>DEFAULT_PASSWORD</b> - Encrypted password to set by default for all users. 
 * To set the value, use the IM password tool to encrypt password as follows:
 * </ul>
 * <p>
 * To encrypt the password, go to <b>%admin_tools_home%</b>/passwordtool<br>
 * > <code>pwdtools -JSAFE -p &lt;new password&gt;</code><br><br>
 * NOTE: Be sure the JAVA_HOME variable is defined.
 * @author 		Lester Rivera
 * @version     %I%, %G%
 * @since       CA Identity Manager 12.0 CR7
 */

// Associate this BLTH with task Create User
public class GeneratePassword extends BLTHAdapter {
    private static final Log logger = LogFactory.getLog(GeneratePassword.class);
    private String defaultPass = "";
    /**
     * The init method allows retrieval of BLTH-specific properties.
     * Using properties promotes re-usable code since business logic can
     * be different for each task you assign the BLTH to.
     */
    public void init(Hashtable imeProperties) throws Exception {
        super.init(imeProperties);
        // Properties from the BLTH settings
        defaultPass = (String)imeProperties.get("DEFAULT_PASSWORD");
        
        logger.debug("GeneratePassword BLTH properties: ");
        logger.debug("- DEFAULT_PASSWORD: " + defaultPass);
    }
    /**
     * Use the handleValidation trigger because it allows adding new events and
     * attribute values to the task before it is submitted into the engine.
     * @exception IMSException If it fails to set the password.
     */
    public void handleValidation(BLTHContext blthContext) throws Exception {
        
        logger.debug("Retrieving User Record Information.");
        
        // get current user
        User changeUser = blthContext.getUser();
        // get the current requestor's user object; this is to use a special API method available to that object.
        User managerUser = blthContext.getAdministrator();
        // Strip to retrieve user ID
        if (changeUser == null) {
        	// Displays the error in the userform and stops the form submission
        	// because a password is required for all users.
            IMSException imsEx = new IMSException();
            logger.error("Unable to retrieve the user record.");
            imsEx.addUserMessage("Unable to retrieve the user record.");
            throw imsEx;
        } else {
            
            String firstPass = "";
            if (changeUser.containsAttribute("%PASSWORD%")) {
                firstPass = changeUser.getAttribute("%PASSWORD%"); //Password
            }

            String validatePass = "";
            if (changeUser.containsAttribute("|passwordConfirm|")) {
                validatePass = changeUser.getAttribute("|passwordConfirm|"); //Password Confirm
            }
            // Generate a password if none has been set
            if (firstPass.isEmpty() && validatePass.isEmpty()){
                // Generate a new password
                try {
                    String tempPassword = "";
                    // If default password is not set; generate a password
                    if (defaultPass.isEmpty()){
                        logger.debug("Attempting to generate a new password"); 
                        // Use the admin user to generate the temporary password.
                        // The generateTemporaryPassword method is only available
                        // for the Requestor's user object and does not affect
                        // their password.
                        tempPassword = managerUser.generateTemporaryPassword();
                        logger.debug("tempPassword: " + tempPassword);

                    } else {
                    	// If a default password is set; use that value
                        logger.debug("Using default password");
                        tempPassword = JSafeTools.decryptText(defaultPass.trim()).toString();
                    }
                    logger.debug("Attempting to set a new password"); 
                    changeUser.setAttribute("%PASSWORD%", tempPassword); 
                    changeUser.setAttribute("|passwordConfirm|", tempPassword);
                    logger.info("Password is set for user.");
                } catch (SmApiException ex) {
                	// Displays the error in the userform and stops the form submission
                	// because a password is required for all users.
                    IMSException imsEx = new IMSException();
                    logger.warn("Failed to set the initial password for user "+ changeUser.getUniqueName());
                    logger.error("Exception reason: " + ex.getReason());
                    logger.error("Exception message: " + ex.getMessage());
                    imsEx.addUserMessage("Failed to set the initial password.");
                    throw imsEx;
                } catch (Exception sx) {
                	// Displays the error in the userform and stops the form submission
                	// because a password is required for all users.
                    IMSException imsEx = new IMSException();
                    logger.warn("Failed to set the initial password for user " + changeUser.getUniqueName());
                    logger.error("Caused by: " + sx.getClass());
                    logger.error("Exception message: " + sx.getMessage());
                    imsEx.addUserMessage("Failed to set the initial password.");
                    throw imsEx;
                }
            } //if
        }
    }
}

