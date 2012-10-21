package org.fasttrack.blth;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import com.netegrity.imapi.BLTHAdapter;
import com.netegrity.imapi.BLTHContext;
import com.netegrity.sdk.apiutil.SmApiException;
import com.netegrity.llsdk6.imsapi.managedobject.User;
import com.netegrity.ims.exception.IMSException;
import java.util.Hashtable;
/**
 * Formats the fullname field according to the provided format template. 
 * An override mechanism is supported when a value is placed in the 
 * %FULL_NAME% attribute.
 * <h3>User-Defined Properties</h3>
 * <ul>
 * <li><b>FORMAT</b> - Provides a template for formatting the fullname.
 * <br>
 *               Where,<br>
 *               &lt;&lt;first&gt;&gt; - First Name<br>
 *               &lt;&lt;middle&gt;&gt; - Middle Name<br>
 *               &lt;&lt;last&gt;&gt; - Last Name<br>
 *               Example: &lt;&lt;last&gt;&gt;, &lt;&lt;first&gt;&gt; &lt;&lt;middle&gt;&gt; for Smith, John Jacob
 * <li><b>USE_INITIAL</b> - True or False. Use only the 1st character in Middle Name
 * </ul>
 * @author 		Lester Rivera
 * @version     %I%, %G%
 * @since       CA Identity Manager 12.0
 */
public class FormatFullname extends BLTHAdapter {
    private static final Log logger = LogFactory.getLog(FormatFullname.class);
    
    private String formatString = "<<last>>, <<first>>";
    private boolean useInitial = false;
    /**
     * The init method allows retrieval of BLTH-specific properties.
     * Using properties promotes re-usable code since business logic can
     * be different for each task you assign the BLTH to.
     */
    public void init(Hashtable imeProperties) throws Exception {
        super.init(imeProperties);
        // Properties from the BLTH settings
        formatString = (String)imeProperties.get("FORMAT");
        String useInit = (String)imeProperties.get("USE_INITIAL");
 
        if (useInit.equalsIgnoreCase("true")){
            useInitial = true;
        }
        logger.debug("FormatFullname BLTH properties: ");
        logger.debug("- FORMAT: " + formatString);
        logger.debug("- USE_INITIAL: " + useInit);
    }
    /**
     * Use the handleValidation trigger because it allows adding new events and
     * attribute values to the task before it is submitted into the engine.
     * @exception 	IMSException If setting the fullname attribute fails. 
     */
    public void handleValidation(BLTHContext blthContext) throws Exception {
        
        logger.debug("Retrieving User Information to generate Fullname.");
        // get current users information
        User changeUser = blthContext.getUser();
        
        // nullPointerExceptions are prevalent when coding for CA Identity Manager,
        // its a good idea to check the user object before attempting to modify it.
        // Oddly enough, reading from the user object does not generate the  errors.
        if (changeUser != null) {
            // Retrieve first name
            // This is an example of the best way to retrieve fields;
            // that is, determine if the userform contains the attribute
            // then get the attribute.
            String firstName = "";
            if (changeUser.containsAttribute("%FIRST_NAME%")) {
                firstName = changeUser.getAttribute("%FIRST_NAME%"); //First Name
            }
            // Retrieve middle name
            String middleName = "";
            if (changeUser.containsAttribute("eTMiddleInitial")) {
                middleName = changeUser.getAttribute("eTMiddleInitial"); //Middle Name
            }
            // Determine if to use the middle initial only
            if (useInitial && !middleName.isEmpty()){
                middleName = middleName.substring(0, 1);
            }
            // Retrieve last name
            String lastName = "";
            if (changeUser.containsAttribute("%LAST_NAME%")) {
                lastName = changeUser.getAttribute("%LAST_NAME%"); //Last Name
            }
            // Retrieve full name
            String fullName = "";
            if (changeUser.containsAttribute("%FULL_NAME%")) {
                fullName = changeUser.getAttribute("%FULL_NAME%"); //Full Name
            }
            
            logger.debug("Finding user attributes: ");
            logger.debug("- First Name: " + firstName);
            logger.debug("- Middle Name: " + middleName);
            logger.debug("- Last Name: " + lastName);
        	
            // Generate a fullname only if no value is set
            if(fullName.isEmpty() || fullName.equalsIgnoreCase("default")){
                try {
                    String firstTemplate = "<<first>>";
                    String middleTemplate = "<<middle>>";
                    String lastTemplate = "<<last>>";
                    // Replace the template values
                    formatString = formatString.replaceAll(firstTemplate, firstName.trim());
                    formatString = formatString.replaceAll(middleTemplate, middleName.trim());
                    fullName = formatString.replaceAll(lastTemplate, lastName.trim());

                    changeUser.setAttribute("%FULL_NAME%", fullName.trim()); 
                    logger.debug("fullname is set.");

                } catch (SmApiException ex) {
                    // this message will be presented on the screen
                    IMSException imsEx = new IMSException();
                    logger.error("Failed to set the fullname.");
                    logger.error("Exception reason: " + ex.getReason());
                    logger.error("Exception message: " + ex.getMessage());
                    imsEx.addUserMessage("Failed to set the fullname.");
                    throw imsEx;
                } catch (Exception sx) {
                    // this message will be presented on the screen
                    IMSException imsEx = new IMSException();
                    logger.error("Failed to set the fullname.");
                    logger.error("Caused by: " + sx.getClass());
                    logger.error("Exception message: " + sx.getMessage());
                    imsEx.addUserMessage("Failed to set the fullname.");
                    throw imsEx;
                }
            } else {
                logger.debug("Fullname already set in form.");
            }
            logger.info("Fullname for user " +changeUser.getUniqueName()+ " set to " +fullName);
        } else {
            // this message will be presented on the screen
            IMSException imsEx = new IMSException();
            logger.error("Unable to retrieve the user record.");
            imsEx.addUserMessage("Unable to retrieve the user record.");
            throw imsEx;
        }
    }
}

