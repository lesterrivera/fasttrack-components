package org.fasttrack.blth;

import java.util.Vector;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import com.netegrity.imapi.BLTHAdapter;
import com.netegrity.imapi.BLTHContext;
import com.netegrity.llsdk6.imsapi.managedobject.User;
import com.netegrity.ims.exception.IMSException;
import com.netegrity.llsdk6.imsapi.collections.AttributeRightsCollection;
import com.netegrity.llsdk6.imsapi.metadata.AttributeRight;
import com.netegrity.llsdk6.imsapi.policy.rule.constraints.AttributeExpression;
import com.netegrity.llsdk6.imsapi.policy.rule.constraints.OrgMembershipConstraint;
import com.netegrity.llsdk6.imsapi.policy.rule.constraints.UserFilter;
import com.netegrity.sdk.apiutil.SmApiException;
import com.netegrity.llsdk6.imsapi.type.OperatorType;
import com.netegrity.llsdk6.imsapi.type.PermissionType;
import com.netegrity.llsdk6.imsapi.type.ConjunctionType;
/**
 * Verify that the user to be created does not already exist in the CA Identity 
 * Manager system. The business logic attempts to use the Last Name and the 
 * last 4 digits in the SSN to determine if the user exists.
 * <p>
 * This is a typical use case in IAM design as requestors may frequently assume
 * that all user's undergoing the onboard process are new to the company; however,
 * it's possible that they were previously employed or simply already entered by 
 * another person. This avoids a duplicate user record and displays a nice error
 * on the userform before its actually submitted.
 * <p>
 * We assume that Last Name and Last4SSN attributes are required attributes for
 * all users.
 * @author 		Lester Rivera
 * @version     %I%, %G%
 * @since       CA Identity Manager 12.0 CR7
 */

// Associate this BLTH with task Create User
public class VerifyNoPreviousUserRecord extends BLTHAdapter {
    private static final Log logger = LogFactory.getLog(VerifyNoPreviousUserRecord.class);
    private String foundID = "N/A";
    private String foundName = "N/A";
    /**
     * Use the handleValidation trigger because it allows adding new events and
     * attribute values to the task before it is submitted into the engine.
     * @exception IMSException If a duplicate user is found.
     */
    public void handleValidation(BLTHContext blthContext) throws Exception {
        
        logger.debug("Retrieving User Record Information.");
        
        // get current user object 
        User changeUser = blthContext.getUser();
        
        // Determine if you retrieve the user object
        if (changeUser != null) {
        	// Retrieve from the userform to use in the query
        	// The last 4 digits of the SSN
            String userLast4SSN = "";
            if (changeUser.containsAttribute("eTCustomField06")) {
                userLast4SSN = changeUser.getAttribute("eTCustomField06"); //Last 4 SSN
            }
            logger.debug("Retrieved eTCustomField06: " + userLast4SSN);
            // The first name; not really used for the query, but good to log errors
            String firstName = "";
            if (changeUser.containsAttribute("%FIRST_NAME%")) {
                firstName = changeUser.getAttribute("%FIRST_NAME%"); //First Name
            }
            logger.debug("Retrieved %FIRST_NAME%: " + firstName);
            // the last name      
            String lastName = "";
            if (changeUser.containsAttribute("%LAST_NAME%")) {
                lastName = changeUser.getAttribute("%LAST_NAME%"); //Last Name
            }
            logger.debug("Retrieved %LAST_NAME%: " + lastName);
            
            // 1. Search for the manager's global record
            logger.info("Searching for a previous user record for " + firstName + " " + lastName);
                
		    try
		    {
		    	// You have to go thru several programmatic steps to generate a query thru this API
		    	// Basically, creating an LDAP-style query where &(%LAST_NAME%=lastName)(eTCustomField06=userLast4SSN)
		    	// with the query results that contains %FULL_NAME% and %USER_ID% attribute values
		    	String attrName = "";
	            OperatorType operator = OperatorType.EQUALS;
	            String attrVal = "";
	            // these are that attributes for the query results        
	            Vector attributes = new Vector();
	            attributes.add("%FULL_NAME%");
	            attributes.add("%USER_ID%");
	            // To set up the LDAP-style query with the correct values  
	            Vector allFilters = new Vector();
	            allFilters.addElement(new AttributeExpression("%LAST_NAME%", OperatorType.EQUALS, lastName));
	            allFilters.addElement(new AttributeExpression("eTCustomField06", OperatorType.EQUALS, userLast4SSN));
	            // Put the elements in the LDAP-style query together with an AND conditional
	            Vector allConj = new Vector();
	            allConj.addElement(ConjunctionType.AND);
	            // and all that comes together into a complete query filter
	            UserFilter uf = new UserFilter(allFilters, allConj);
				OrgMembershipConstraint omc = null;
					
				// Update the rights collection for each attribute to make sure the current user has permission
				// to actually retrieve the values from the query, 'cause we have not been tortured enough!
				// Skip this and crazy errors ensue!
				AttributeRightsCollection rights = new AttributeRightsCollection();
				logger.debug("finding users with attributes: ");
				for (int i=0; i < attributes.size(); i ++){
					logger.debug(attributes.elementAt(i).toString());
					rights.addEntry(new AttributeRight(attributes.elementAt(i).toString(), PermissionType.READWRITE));
				}
				// And we finally can submit the query to the CA Identity Manager system
	            Vector users = null;
	            users = blthContext.getUserProvider().findUsers(uf, omc, rights);
	            if (users != null ) {
                    if (users.size() > 0) {
                    	// Retrieved a one or more records as a result of the query.
                    	// if you stopped giving a shit 5 lines ago as I did, recognize 
                    	// that this is the bad state; that is a user exists with the same values.
                        logger.debug("found " + users.size() + " users");
                        // Retrieve the userID and full name of the first user only
                        // to display in your error message to the requestor
                        User found = (User) users.firstElement();
                        foundID = found.getAttribute("%USER_ID%");
                        foundName = found.getAttribute("%FULL_NAME%");
                        throw new Exception(); //throw the except to let the user know
                    } else {
                    	logger.debug("found 0 users");
                    	// This is the good state, let the userform submit without errors.
                    }
	            } else {
                    logger.debug("user search returned null. Indicates an error in the query.");
	            }
	        }
	        catch(SmApiException smapiex)
	        {
	            logger.error("exception occurred on search for users: " + smapiex.getMessage());
	        }
	        catch(Exception ex)
	        {
	        	// Displays the error in the userform and stops the form submission
	        	// Provide a good message to the requestor so that they can troubleshoot preoperly.
	            logger.error("exception occurred on search for users: " + ex.getMessage());
	            logger.error("A user record was found for " + firstName + " " + lastName + " as " + foundName + " (" + foundID + "). Consider updating the user instead.");
	            // this message will be presented on the screen
	            IMSException imsEx = new IMSException();
	            imsEx.addUserMessage("A user record was found for " + firstName + " " + lastName + " as " + foundName + " (" + foundID + "). Consider updating the user instead.");
	            throw imsEx;
	        }
        	logger.info("No previous record found for " + firstName + " " + lastName + " based on Last4SSN and Last Name search.");
        } else {
            // this message will be presented on the screen
            IMSException imsEx = new IMSException();
            logger.warn("Unable to retrieve the user record.");
            imsEx.addUserMessage("Unable to retrieve the user record.");
            throw imsEx;
        }
    } // End function
} // End class
