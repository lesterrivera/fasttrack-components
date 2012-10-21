package org.fasttrack.blth;

import org.fasttrack.util.LDAPUtils;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import com.netegrity.imapi.BLTHAdapter;
import com.netegrity.imapi.BLTHContext;
import com.netegrity.ims.exception.IMSException;
import com.netegrity.llsdk6.imsapi.managedobject.User;
import java.util.Hashtable;
import javax.naming.directory.Attributes;
/**
 * Business Logic Task Handler (BLTH) class which generates a LANID for a user;
 * that is, a UserID designed to function as an account name or accountID for 
 * an Active Directory, LDAP, or UNIX account. In this class, we also use the 
 * LANID as the prefix for the email address of the user. In addition, an override 
 * mechanism is supported when a value is placed in the eTCustomField02 and 
 * %EMAIL% attribute in the userform. 
 * The BLTH loads the resulting values in the following attributes:
 * <ul>
 * <li>eTCustomField02 - Becomes the accountID for all managed accounts
 * <li>%EMAIL% - the email address for the user; LANID combined with the email domain
 * </ul>
 * <p>
 * This is a typical use case in IAM design. The best practice is to generate
 * the LANID as the user is created in order to ensure the same LANID is used 
 * for all accounts provisioned for the user. Creating the LANID at the point
 * the the account is created generates a number of logical hurdles that are
 * harder to overcome at that time.
 * <p>
 * The business logic to generates the LANID based upon the first name, middle 
 * initial, and last name. The LANID has a max size of 20 characters to ensure
 * compatibility with older RACF/TSS systems. The business logic starts with
 * an initial value based on a combination of first initial, middle initial
 * (if available), and up to 19 characters of the last name (18 if middle initial
 * is available. The value is then checked against an LDAP directory considered
 * the system of record for LANIDs. If the value is not unique, the first name 
 * and last name characters are shifted to create a new value and checked for
 * uniqueness again. For example, a name "John Fred Doe" would generate the 
 * following potential values:
 * <ol>
 * <li>jfdoe
 * <li>jodoe
 * <li>johdoe
 * <li>johndoe
 * <li>etc...
 * </ol>
 * <p>
 * The algorithm keeps looping thru potential values until we
 * find one that is unique. If a total of 20 potential values are checked and
 * found not to be unique, an error message is generated in the userform.
 * <p>
 * As mentioned, an LDAP directory is used as the system of record for LANIDs
 * and used to check potential values for uniqueness. Ideally, we might use
 * the CA Identity Manager system, but sometimes terminated users are removed
 * from the system and we are looking for LANIDs to be unique across those users
 * as well; primarily, because it allows audit of user activities after their 
 * employment ends.
 * <h3>User-Defined Properties</h3>
 * <ul>
 * <li><b>EMAIL_DOMAIN</b> - The domain for the email address. For example, company.com 
 * to be assigned LANID@company.com
 * <li><b>LDAP_ENVIRONMENT</b> - Environment name of settings configured in ldap.properties
 * <li><b>LDAP_USERID</b> -	The username attribute used by the LDAP directory. For example,
 * sAMAccountName is used by Active Directory, UID is typically used for LDAP.
 * </ul>
 * @author 		Lester Rivera
 * @version     %I%, %G%
 * @since       CA Identity Manager 12.0 CR7
 * @see 		org.fasttrack.util.LDAPUtils
 */
public class GenerateLANID extends BLTHAdapter {
    private static final Log logger = LogFactory.getLog(GenerateLANID.class);
    private int MAXTRIES = 20;
    private String emaildomain = "";
    private String ldap_env = "";
    private String ldap_userID = "";
    /**
     * The init method allows retrieval of BLTH-specific properties.
     * Using properties promotes re-usable code since business logic can
     * be different for each task you assign the BLTH to.
     */
    public void init(Hashtable imeProperties) throws Exception {
        super.init(imeProperties);
        // Properties from the BLTH settings
        String emaildomain = (String)imeProperties.get("EMAIL_DOMAIN");
        String ldap_env = (String)imeProperties.get("LDAP_ENVIRONMENT");
        String ldap_userID = (String)imeProperties.get("LDAP_USERID");
        
        logger.debug("GenerateLANID BLTH properties: ");
        logger.debug("- EMAIL_DOMAIN: " + emaildomain);
        logger.debug("- LDAP_ENVIRONMENT: " + ldap_env);
        logger.debug("- LDAP_USERID: " + ldap_userID);
        
    }
    /**
     * Use the handleValidation trigger because it allows adding new events and
     * attribute values to the task before it is submitted into the engine.
     * @exception IMSException	If the a valid unique LANID cannot be found, or if the
     * required properties are not configured.
     */
    public void handleValidation(BLTHContext blthContext) throws Exception {
        if (emaildomain.isEmpty() || ldap_env.isEmpty() || ldap_userID.isEmpty()){
            // Displays the error in the userform and stops the form submission
        	// if the BLTH is improperly configured.
            IMSException imsEx = new IMSException();
            logger.warn("GenerateLANID is not configured properly. Property settings are required.");
            imsEx.addUserMessage("GenerateLANID is not configured properly. Property settings are required.");
            throw imsEx;
        }
        
        logger.debug("Retrieving User Information to generate LANID.");
        // get current users information
        User changeUser = blthContext.getUser();        
        
        String firstName = "";
        if (changeUser.containsAttribute("%FIRST_NAME%")) {
            firstName = changeUser.getAttribute("%FIRST_NAME%"); //First Name
        }
        
        String middleName = "";
        if (changeUser.containsAttribute("eTMiddleInitial")) {
            middleName = changeUser.getAttribute("eTMiddleInitial"); //Middle Name
        }
        
        String lastName = "";
        if (changeUser.containsAttribute("%LAST_NAME%")) {
            lastName = changeUser.getAttribute("%LAST_NAME%"); //Last Name
        }
        
        String LANID = "";
        if (changeUser.containsAttribute("eTCustomField02")) {
            LANID = changeUser.getAttribute("eTCustomField02"); //LANID
        }
        
        String email = "";
        if (changeUser.containsAttribute("%EMAIL%")) {
        	email = changeUser.getAttribute("%EMAIL%"); //email
        }
        
        logger.debug("Finding user attributes: ");
        logger.debug("- First Name: " + firstName);
        logger.debug("- Middle Name: " + middleName);
        logger.debug("- Last Name: " + lastName);
        logger.debug("- eTCustomField02: " + LANID);  
        logger.debug("- %EMAIL%: " + email);

        // with a value in LANID; a new value will not be generated and saved 
        if (LANID.isEmpty() && email.isEmpty()) { 
            // remove special characters from first and last name
            String nonSpecialChars = "[^A-Z]";
            String remSuffix1 = "^AL-"; // remove arabic name prefix al-
            String remSuffix2 = "^BIN-"; // remove arabic name prefix bin-
            String remSuffix3 = "\\sI$"; // remove suffix I
            String remSuffix4 = "\\sII$"; // remove suffix II
            String remSuffix5 = "\\sIII$"; // remove suffix III
            String remSuffix6 = "\\sIV$"; // remove suffix IV
            String remSuffix7 = "\\sV$"; // remove suffix V
            String remSuffix8 = "\\sJR$"; // remove suffix JR
            String remSuffix9 = "\\sSR$"; // remove suffix SR
            firstName = firstName.toUpperCase().replaceAll(nonSpecialChars, "");
            lastName = lastName.toUpperCase().replaceAll(remSuffix1, ""); // Remove al- from name
            lastName = lastName.toUpperCase().replaceAll(remSuffix2, ""); // Remove bin- from name
            lastName = lastName.toUpperCase().replaceAll(remSuffix3, ""); // remove suffix I
            lastName = lastName.toUpperCase().replaceAll(remSuffix4, ""); // remove suffix II
            lastName = lastName.toUpperCase().replaceAll(remSuffix5, ""); // remove suffix III
            lastName = lastName.toUpperCase().replaceAll(remSuffix6, ""); // remove suffix IV
            lastName = lastName.toUpperCase().replaceAll(remSuffix7, ""); // remove suffix V
            lastName = lastName.toUpperCase().replaceAll(remSuffix8, ""); // remove suffix Jr
            lastName = lastName.toUpperCase().replaceAll(remSuffix9, ""); // remove suffix Sr
            lastName = lastName.toUpperCase().replaceAll(nonSpecialChars, "");

            logger.debug("Modifying user attributes: ");
            logger.debug("- First Name: " + firstName);
            logger.debug("- Last Name: " + lastName);

            logger.info("Starting to generate LANID for: " + lastName + ", " + firstName + " " + middleName);
            try {
                logger.debug("Determining LANID.");
                boolean goodId = false;
                LANID = genInitialTempID(firstName, middleName, lastName);
                // Iterate to test a valid LANID
                for (int count = 1; count < MAXTRIES; count++) { // cycle through 20 times; max length of LANID
                   if (isUniqueID(LANID)){ //check for uniqueness in user store
                       goodId = true;
                       break;
                   }
                   LANID = genNewTempID(firstName, lastName, count);
                }

                // If a good LANID is available, change user record
                if (goodId && !emaildomain.isEmpty()) {
                    // Set the LANID on required fields
                    logger.info("Setting LANID for " + firstName + " " + lastName + " as " + LANID);
                    logger.debug("LANID is of size: " + LANID.length());
                    changeUser.setAttribute("eTCustomField02", LANID); // LAN userID
                    logger.debug("Set eTCustomField02: " + LANID);
                    changeUser.setAttribute("%EMAIL%", LANID + "@" + emaildomain.trim()); // Email
                    logger.debug("Set %EMAIL%: " + LANID + "@" + emaildomain.trim());
                } else {
                    throw new Exception(); //else, let the user know
                }

                // Done
            } catch (Exception ex) {
                // Displays the error in the userform and stops the form submission
            	// LANID is required for all users, so if the operation fails
            	// you don't want that record in your Identity Manager system.
                IMSException imsEx = new IMSException();
                logger.warn("Failed to set LANID for the Global User: " + lastName + ", " + firstName);
                imsEx.addUserMessage("Failed to set LANID for the Global User: " + lastName + ", " + firstName);
                throw imsEx;
            }
        } else {
            logger.info("Generate LANID not required. Setting LANID for " + firstName + " " + lastName + " as " + LANID);
        }
    }
    
    // Generate LANID according to initial specification
    private String genInitialTempID (String firstName, String middleName, String lastName){
        logger.debug("Generating initial LANID.");
        if (firstName.length() > 1) {
            firstName = firstName.substring(0,1);
        }
        
        if (!middleName.isEmpty()){
            middleName = middleName.substring(0,1);
        }
        
        int nIndex = 18; //total length cannot be more than 20; 18 + 2 initials.
        if (lastName.length() < 18){
            nIndex = lastName.length();
        }
        lastName = lastName.substring(0, nIndex);
        
        logger.debug("Initial LANID: " + lastName + firstName + middleName);
        
        return lastName + firstName + middleName;
    }
    // Generate LANID after initial one
    private String genNewTempID (String firstName, String lastName, int nCycle) {
        logger.debug("Generating additional LANID ( " + nCycle + " )");
        int sIndex = 1 + nCycle; // Add a letter from first name each cycle
        if (firstName.length() >= sIndex) {
            firstName = firstName.substring(0, sIndex);
        } else {
            firstName = firstName.substring(0, firstName.length()) + (sIndex - firstName.length());
        }
        
        int nIndex = 20 - nCycle; // Remove a letter from last name each cycle if length is over 20
        if (lastName.length() > nIndex){
            lastName = lastName.substring(0, nIndex);
        } else {
            // last name is already short, use it all
            lastName = lastName.substring(0, lastName.length());
        }
        
        logger.debug("Generated LANID (" + nCycle + "): " + lastName + firstName);
        
        return lastName + firstName;
    }
    // Check if ID is unique to user store
    private boolean isUniqueID (String checkID) throws Exception{
        boolean ret = false;
        logger.debug("Determining if LANID is unique: " + checkID);
        try {
            LDAPUtils ldap = new LDAPUtils();
            String[] ldapReturns = new String[]{ldap_userID.trim()};
            String ldapFilter = "(&(objectClass=user)(" + ldap_userID.trim() + "=" + checkID + "))";
            Attributes attrs = ldap.Query(ldap_env.trim(), ldapFilter, ldapReturns);
            if (attrs.size() > 0) {
                logger.debug(attrs.get(ldap_userID.trim()));
                logger.debug("Records: " + attrs.size());
                ret = false;
            } else {
                ret = true;
            }
        } catch (Exception e) {
            logger.debug("Unable to connect to LDAP.");
            logger.debug(e.getMessage());
            throw e;
        }
        logger.debug("Is LANID " + checkID + " unique?: " + ret);
        return ret;
    }
}
