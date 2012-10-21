package org.fasttrack.blth;

import org.fasttrack.util.LDAPUtils;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import com.netegrity.imapi.BLTHAdapter;
import com.netegrity.imapi.BLTHContext;
import com.netegrity.llsdk6.imsapi.managedobject.User;
import com.netegrity.ims.exception.IMSException;

import java.util.Hashtable;
/**
 * Generate the Unique UserID for Contractors.
 * <p>
 * This is a typical use case in IAM design as typically the Identity Manager system becomes
 * the system of record for non-employees in a company. The Contractor UserID (CID) is equivalent 
 * to an employee number and generally assumed to be an integer. The value is prefixed by the 
 * prefix value set in the BLTH properties; that is, if %CID_PREFIX% is "c", the value is c12345.
 * The CID value is stored in the %USER_ID% and eTCustomField01 attribute of the user object.
 * <p>
 * The technical design calls for a counter value stored as a value in an LDAP object. The counter
 * is retrieved, incremented, and returned to the LDAP object; however, the transaction is not
 * strictly atomic and - in high-usage situation - collisions can occur.
 * <p>
 * Improved designs could use a database to maintain the counter to ensure an ACID transaction. Or,
 * an interesting approach would be to generate a GUID value then check the uniqueness through a
 * query to the CA Identity Manager system.
 * <h3>User-Defined Properties</h3>
 * <ul>
 * <li><b>CID_PREFIX</b> - Prefix value to append to the numeric ID
 * <li><b>LDAP_COUNTER_ENVIRONMENT</b> - Environment name of settings configured in ldap.properties
 * <li><b>LDAP_COUNTER_OBJECT</b> - DN (distinguishedName) of the LDAP object used to store the counter information
 * <li><b>LDAP_COUNTER_ATTRIBUTE</b> - Attribute name of the attribute in the LDAP object used as a counter
 * </ul>
 * @author 		Lester Rivera
 * @version     %I%, %G%
 * @since       CA Identity Manager 12.0 CR7
 * @see 		org.fasttrack.util.LDAPUtils
 */
public class GenerateCID extends BLTHAdapter {

    private static final Log logger = LogFactory.getLog(GenerateCID.class);

    private Hashtable env = new Hashtable(11);
    private String prefixVal = "";
    private String counterObj = "";
    private String counterAttr = "";
    private String counterEnv = "";
    /**
     * The init method allows retrieval of BLTH-specific properties.
     * Using properties promotes re-usable code since business logic can
     * be different for each task you assign the BLTH to.
     */    
    public void init(Hashtable imeProperties) throws Exception {
        super.init(imeProperties);
        // Properties from the BLTH settings
        prefixVal = (String)imeProperties.get("CID_PREFIX");
        counterEnv = (String)imeProperties.get("LDAP_COUNTER_ENVIRONMENT");
        counterObj = (String)imeProperties.get("LDAP_COUNTER_OBJECT");
        counterAttr = (String)imeProperties.get("LDAP_COUNTER_ATTRIBUTE");
        
        logger.debug("GenerateCID BLTH properties: ");
        logger.debug("- CID_PREFIX: " + prefixVal);
        logger.debug("- LDAP_COUNTER_ENVIRONMENT: " + counterEnv);
        logger.debug("- LDAP_COUNTER_OBJECT: " + counterObj);
        logger.debug("- LDAP_COUNTER_ATTRIBUTE: " + counterAttr);
    }
    /**
     * Use the handleValidation trigger because it allows adding new events and
     * attribute values to the task before it is submitted into the engine.
     */
    public void handleValidation(BLTHContext blthContext) throws Exception {
        if (counterEnv.isEmpty() || counterObj.isEmpty() || counterAttr.isEmpty()){
            // Displays the error in the userform and stops the form submission
        	// if the BLTH is improperly configured.
            IMSException imsEx = new IMSException();
            logger.warn("GenerateCID is not configured properly. Property settings are required.");
            imsEx.addUserMessage("GenerateCID is not configured properly. Property settings are required.");
            throw imsEx;
        }

        logger.debug("Retrieving User Information to generate CID.");
        // get current users information
        User changeUser = blthContext.getUser();

        // Retrieve name values; strictly unnecessary, but logging a name
        // with the CID in the View Submitted Tasks logs helps troubleshooting.
        String firstName = "";
        if (changeUser.containsAttribute("%FIRST_NAME%")) {
            firstName = changeUser.getAttribute("%FIRST_NAME%"); //First Name
        }

        String middleName = "";
        if (changeUser.containsAttribute("Middlename")) {
            middleName = changeUser.getAttribute("Middlename"); //Middle Name
        }

        String lastName = "";
        if (changeUser.containsAttribute("%LAST_NAME%")) {
            lastName = changeUser.getAttribute("%LAST_NAME%"); //Last Name
        }

        logger.debug("Finding user attributes: ");
        logger.debug("- First Name: " + firstName);
        logger.debug("- Middle Name: " + middleName);
        logger.debug("- Last Name: " + lastName);

        logger.info("Starting to generate CID for: " + lastName + ", " + firstName + " " + middleName);
        try {
            logger.debug("Determining CID.");
            String CID = getNextCPID();
            logger.debug("Returned CID of " + CID);
            // If a good CID is available, change user record
            if (!CID.isEmpty()) {
                // Set the CID on required fields
                logger.info("Setting CID for " + firstName + " " + lastName + " as " + CID);
                changeUser.setAttribute("eTCustomField01", CID); // userID
                logger.debug("Set eTCustomField01: " + CID);
                changeUser.setAttribute("%USER_ID%", CID); // GlobalUserName
                logger.debug("Set %USER_ID%: " + CID);
            } else {
                throw new Exception(); //else, let the user know
            }

        // Done
        } catch (Exception ex) {
            // Displays the error in the userform and stops the form submission
        	// CID is required for all contractors, so if the operation fails
        	// you don't want that record in your Identity Manager system.
            IMSException imsEx = new IMSException();
            logger.warn("Failed to set CID for the Global User: " + lastName + ", " + firstName);
            imsEx.addUserMessage("Failed to set CID for the Global User: " + lastName + ", " + firstName);
            throw imsEx;
        }
        
    }
    // Retrieve next value from Counter
    private String getNextCPID() throws Exception {
        String foundID = "";
        try {
            // Create the initial context
	    LDAPUtils counter = new LDAPUtils();
            String tempID = counter.getAttributeValue(counterObj, counterAttr, counterEnv);
            logger.debug("Retrieved value " + counterAttr + " for " + counterObj + " is " + tempID + "");
            
            int tempInt = Integer.parseInt(tempID);
            tempInt = tempInt + 1;
            
            counter.replaceAttributeValue(counterObj, counterAttr, Integer.toString(tempInt), counterEnv);
            logger.debug("Set value " + counterAttr + " for " + counterObj + " to " + Integer.toString(tempInt) + "");
            if (prefixVal.isEmpty()){
            	foundID = "c" + Integer.toString(tempInt);
            } else {
            	foundID = prefixVal.trim() + Integer.toString(tempInt);
            }
        } catch (Exception ex) {
            logger.error("General exception occurred on search for counter object: " + ex.getMessage());
        }
        return foundID;
    }
}
