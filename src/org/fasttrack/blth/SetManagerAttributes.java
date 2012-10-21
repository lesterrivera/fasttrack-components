package org.fasttrack.blth;

import java.util.Hashtable;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import com.netegrity.imapi.BLTHAdapter;
import com.netegrity.imapi.BLTHContext;
import com.netegrity.ims.exception.IMSException;
import com.netegrity.llsdk6.imsapi.managedobject.User;
import org.fasttrack.util.LDAPUtils;
import javax.naming.directory.Attributes;
/**
 * Set additional manager-related values on the user object based on selection of manager 
 * on the task form.
 * <p>
 * When the manager is selected in the task form, the value is actually the distinguishedName
 * (DN) from the Corporate Directory in CA Identity Manager. In this format, the value
 * has limited use. So, by cleaning up the format to reflect the LANID or retrieving the 
 * Active Directory ADSPath to store with attributes on the user object, we can use
 * these values to fill related attributes in user accounts on managed resources, such 
 * as Active Directory.
 * <p>
 * In this class, we fill the following attributes on the user object:
 * <ul>
 * <li>eTCustomField22 - The Active Directory ADSPath for the manager; it allows 
 * you to set the manager attribute for AD
 * <li>eTCustomField69 - The UserID for the manager in LANID format
 * </ul>
 * I believe its good IAM design to retrieve and store related values that may be required
 * later in the provisioning process at the time when that original value is being created 
 * or updated. This allows proper synchronization of attribute values to managed endpoints
 * with little additional fuss and its possible that the hooks will not be available later.
 * <h3>User-Defined Properties</h3>
 * <ul>
 * <li><b>LDAP_ENVIRONMENT</b> - Environment name of settings configured in ldap.properties
 * </ul>
 * @author 		Lester Rivera
 * @version     %I%, %G%
 * @since       CA Identity Manager 12.0 CR7
 */
public class SetManagerAttributes extends BLTHAdapter {
    private static final Log logger = LogFactory.getLog(SetManagerAttributes.class);
    private String ldap_env = "";
    /**
     * The init method allows retrieval of BLTH-specific properties.
     * Using properties promotes re-usable code since business logic can
     * be different for each task you assign the BLTH to.
     */
    public void init(Hashtable imeProperties) throws Exception {
        super.init(imeProperties);
        // Properties from the BLTH settings
        ldap_env = (String)imeProperties.get("LDAP_ENVIRONMENT");
        
        logger.debug("SetManagerAttributes BLTH properties: ");
        logger.debug("- LDAP_ENVIRONMENT: " + ldap_env);
    }
    /**
     * Use the handleValidation trigger because it allows adding new events and
     * attribute values to the task before it is submitted into the engine.
     * @exception 	IMSException If required properties are not configured.
     */
    public void handleValidation(BLTHContext blthContext) throws Exception {
        if (ldap_env.isEmpty()){
            // Displays the error in the userform and stops the form submission
        	// if the BLTH is improperly configured.
            IMSException imsEx = new IMSException();
            logger.warn("SetManagerAttributes is not configured properly. Property settings are required.");
            imsEx.addUserMessage("SetManagerAttributes is not configured properly. Property settings are required.");
            throw imsEx;
        }
        // Retrieve selection
        logger.debug("Retrieving user information to set additional manager attributes.");
        // get current users information
        User changeUser = blthContext.getUser();        
        // retrieve the manager selection in the userform; this is the standard field
        String managerDN = "";
        if (changeUser.containsAttribute("manager")) {
            managerDN = changeUser.getAttribute("manager"); //Manager DN in LDAP format
        }
        logger.debug("Retrieved manager DN as " + managerDN);
        
        // Get and set Manager LANID
        String[] tempArr = managerDN.split(",");
        String managerYID = tempArr[0].replaceAll("uid=", "");
        logger.debug("Cleaned Manager UID to " + managerYID);
        changeUser.setAttribute("eTCustomField69", managerYID); // LAN userID of the manager
        logger.debug("Set eTCustomField69: " + managerYID);       
        
        // retrieve the ADSPath from Active Directory for the manager
        // and set it in the user object field
        try {
            LDAPUtils ldap = new LDAPUtils();
            Attributes attrs = ldap.Query(ldap_env, "(&(objectClass=user)(sAMAccountName=" + managerYID + "))", new String[]{"distinguishedName", "sAMAccountName"});
            if (attrs.size() > 0) {
                logger.debug("distinguishedName: " + attrs.get("distinguishedName").get().toString());
                logger.debug("sAMAccountName: " + attrs.get("sAMAccountName").get().toString());
                //logger.debug("adsPath: " + attrs.get("adsPath").getID().toString());
                String managerADSPath = attrs.get("distinguishedName").get().toString();
                changeUser.setAttribute("eTCustomField22", managerADSPath); // ADSPath of the manager
                logger.debug("Set eTCustomField22: " + managerADSPath);
            } else {
                logger.debug("No user account found for manager in AD.");
            }
        } catch (Exception e) {
            logger.error("Unable to connect to LDAP/AD to retrieve manager: " + e.getMessage());
        }
    }

}
