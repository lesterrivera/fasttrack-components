package org.fasttrack.blth;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import com.netegrity.imapi.BLTHAdapter;
import com.netegrity.imapi.BLTHContext;
import com.netegrity.llsdk6.imsapi.managedobject.Role;
import com.netegrity.llsdk6.imsapi.managedobject.User;
import com.netegrity.llsdk6.imsapi.provider.ProvisioningRoleProvider;
/**
 * Assigns Provisioning Role to users during any CreateUser and ModifyUser task 
 * based upon user attribute values. The class was developed before and subsequently
 * replaced by the AssignProvisioningRole event listener. It remains as a 
 * working example of assigning a Provisioning Role thru a BLTH.
 * <p> 
 * The BLTH takes the value of eTCustomField52 from the userform, determines
 * whether a Provisioning Role exists in the system by the same name, and 
 * assigns the Provisioning Role. Keep in mind that the attribute value is 
 * assumed to be a single Provisioning Role name whereas the event listener
 * is designed to take a comma-delimited list of names.
 * <p>
 * The BLTH does not display error messages to the requestor on the userform
 * as designed. Instead the errors will be displayed in the View Submitted Tasks
 * view. We decided not to allow errors in the BLTH to stop the form submission.
 * 
 * @author      Lester Rivera
 * @version     %I%, %G%
 * @since       CA Identity Manager 12.0 CR7
 * @see 		org.fasttrack.evt.AssignProvisioningRole
 */
public class AssignProvisioningRole extends BLTHAdapter {

    private static final Log logger = LogFactory.getLog(AssignProvisioningRole.class);
    /**
     * Use the handleValidation trigger because it allows adding new events and
     * attribute values to the task before it is submitted into the engine.
     * @exception Exception If the assigning the Provisioning Role to the user fails.
     */
    public void handleValidation(BLTHContext blthContext) throws Exception {
        logger.debug("Retrieving User Record Information.");
        String funcRole = "";
        // Retrieve the user object from context object
        User userObj = blthContext.getUser();

        // nullPointerExceptions are prevalent when coding for CA Identity Manager,
        // its a good idea to check the user object before attempting to modify it.
        // Oddly enough, reading from the user object does not generate the errors.
        if (userObj != null) {
            String userID = userObj.getFriendlyName();
            logger.debug("User is: " + userID);
            
            // Retrieve the user attribute
            if (userObj.containsAttribute("eTCustomField52")) {
                funcRole = userObj.getAttribute("eTCustomField52"); //Functional Role
                logger.debug("eTCustomField52 is: " + funcRole);
            } else {
                logger.warn("No value available for eTCustomField52.");
            }
        	

            try {
                // get a Provisioning Role object
                ProvisioningRoleProvider pRoleProvider = blthContext.getProvisioningRoleProvider();
                logger.debug("retrieved the ProvisioningRoleProvider.");
                Role pRoleObject = pRoleProvider.findProvisioningRole(funcRole.trim());
                
                if (pRoleObject == null) {
                    logger.info("No Provisioning Role matches the assigned Functional Role.");
                } else {
                	// Only provisioning the role if not previously assigned
                    if (!userObj.isRoleMember(pRoleObject)) {
                        // Assigns the provisioning role to the user by adding to the user's role memberlist
                        String sessionId = blthContext.getSessionId();
                        logger.debug("Assigning Provisioning Role - " + pRoleObject.getFriendlyName() + " - to User: " + userObj.getFriendlyName() + " for " + sessionId);
                        userObj.makeRoleMember(pRoleObject, true, sessionId);
                        logger.info("Provisioning Role - " + pRoleObject.getFriendlyName() + " - assigned to user " + userID);
                    } else {
                        logger.info("The user " + userID + " is already a member of the Provisioning Role - " + pRoleObject.getFriendlyName());
                    }
                }
            } catch (Exception ex) {
                logger.debug("Error when assigning the Provisioning Role to the User.");
                logger.debug("Exception Message: " + ex.getMessage());
                throw ex;
            } //Try/ Catch
        } else {
            logger.warn("Failed to get the user's unique name (UserID). No provisioning role assigned.");
        }
    } //handleValidation
}

