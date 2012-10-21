package org.fasttrack.blth;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import com.netegrity.imapi.BLTHAdapter;
import com.netegrity.imapi.BLTHContext;
import com.netegrity.llsdk6.imsapi.managedobject.User;
/**
 * Set default values for contractor-related user attributes. 
 * This class is available for educational purposes only.
 * <p>
 * This is a typical use case in IAM design; however, the actual business logic would
 * be different for each implementation. That said, a best practice is to ensure that
 * a requestor does not have to input values into a user form that can be easily derived 
 * from other values in the userform that may have already been specified. This mechanism
 * is an example of that type of business logic.
 * @author 		Lester Rivera
 * @version     %I%, %G%
 * @since       CA Identity Manager 12.0 CR7
 */
public class SetContractorAttributes extends BLTHAdapter {
    private static final Log logger = LogFactory.getLog(SetContractorAttributes.class);
    
    public void handleValidation(BLTHContext blthContext) throws Exception {
        
        // Retrieve selection
        logger.debug("Retrieving user information to set additional contractor attributes.");
        // get current users information
        User changeUser = blthContext.getUser();        
        
        String employeeType = "";
        if (changeUser.containsAttribute("employeeType")) {
            employeeType = changeUser.getAttribute("employeeType"); //Employee Type
        }
        logger.debug("Retrieved Employee Type as " + employeeType);
        
        // Get and set contractor attributes
        if (employeeType.equalsIgnoreCase("ABC")){
            changeUser.setAttribute("eTCustomField03", "2"); // INST DATA
            logger.debug("Set eTCustomField03: " + "2");  
            changeUser.setAttribute("eTTitle", "ABC Contractor"); // Title
            logger.debug("Set eTTitle: " + "ABC Contractor");  
        } else if (employeeType.equalsIgnoreCase("BANK")){
            changeUser.setAttribute("eTCustomField03", "2"); // INST DATA
            logger.debug("Set eTCustomField03: " + "2");  
            changeUser.setAttribute("eTTitle", "Bank Contractor"); // Title
            logger.debug("Set eTTitle: " + "Bank Contractor");               
        } else if (employeeType.equalsIgnoreCase("USMC")){
            changeUser.setAttribute("eTCustomField03", "M"); // INST DATA
            logger.debug("Set eTCustomField03: " + "M");  
            changeUser.setAttribute("eTTitle", "US Marines"); // Title
            logger.debug("Set eTTitle: " + "US Marines");             
        } else if (employeeType.equalsIgnoreCase("USCG")){
            changeUser.setAttribute("eTCustomField03", "O"); // INST DATA
            logger.debug("Set eTCustomField03: " + "O");  
            changeUser.setAttribute("eTTitle", "US Coast Guard"); // Title
            logger.debug("Set eTTitle: " + "US Coast Guard");            
        } else if (employeeType.equalsIgnoreCase("NAVY")){
            changeUser.setAttribute("eTCustomField03", "N"); // INST DATA
            logger.debug("Set eTCustomField03: " + "N");  
            changeUser.setAttribute("eTTitle", "US Navy"); // Title
            logger.debug("Set eTTitle: " + "US Navy");                
        } else {
            changeUser.setAttribute("eTCustomField03", "2"); // INST DATA
            logger.debug("Set eTCustomField03: " + "2");  
            changeUser.setAttribute("eTTitle", "Contractor"); // Title
            logger.debug("Set eTTitle: " + "Contractor");  
        }
        logger.info("Set additional contractor attributes.");
    }

}
