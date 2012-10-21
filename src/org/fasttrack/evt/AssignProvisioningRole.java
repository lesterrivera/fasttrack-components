package org.fasttrack.evt;

import java.util.*;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import com.netegrity.imapi.UserEvent;
import com.netegrity.llsdk6.imsapi.managedobject.User;
import com.netegrity.imapi.EventContext;
import com.netegrity.imapi.IMEvent;
import com.netegrity.imapi.EventListenerAdapter;
import com.netegrity.llsdk6.imsapi.managedobject.ProvisioningRole;
import com.netegrity.llsdk6.imsapi.exception.NoSuchObjectException;
import com.netegrity.imapi.IMEventName;

import org.fasttrack.util.ProvisioningRolesType;

import javax.mail.Session; 
import javax.mail.Message; 
import javax.mail.Transport; 
import javax.mail.MessagingException; 
import javax.mail.internet.MimeMessage; 
import javax.mail.internet.InternetAddress; 
import java.util.Properties;
/**
 * Assigns Provisioning Roles to users during the CreateUser and ModifyUser 
 * event based upon user attribute values. The class is designed to function 
 * with a TEWS data feed that determines the assignment of functional roles 
 * based upon organizational hierarchy information from the HR system.
 * <p>
 * An event listener listens for a specific event or a group of 
 * events in CA Identity Manager. When the event occurs, the event 
 * listener performs custom business logic that is appropriate for 
 * the event and the current event state.
 * <p> 
 * The following types of roles are supported by this framework:
 * <ul>
 * <li><b>Base</b> - A Provisioning Role assigned to all users
 * during the CreateUser event. The name of the Provisioning Role
 * is assigned in the configuration parameters for Event Listener.
 * <li><b>Functional</b> - A list of functional roles is assigned, updated, and
 * removed according to the value in the <b>eTCustomField52</b> 
 * attribute. The attribute value is assumed to be a comma-delimited 
 * list of pre-existing Provisioning Role names.
 * <li><b>Application</b> - Not supported by this Event Listener. Application
 * roles are assumed to be assigned within CA Identity Manager and not by
 * the TEWS data feed.
 * </ul>
 * An optional feature sends an email to selected recipients when a
 * Provisioning Role that is assigned was not found in CA Identity Manager.
 * <p>
 * <b>Important Note</b> The Event Listeners assumes the list of Functional (Provisioning) 
 * Roles in <b>eTCustomField52</b> is always complete; that is, the list represents
 * all the Functional Roles that should be assigned to the user. As such, during an update 
 * the business logic will attempt to remove any Functional Roles assigned to the user not 
 * found on the list. 
 * <h3>User-Defined Properties</h3>
 * <ul>
 * <li><b>SMTP_SERVER</b> - SMTP Host Name or IP Address
 * <li><b>SMTP_PORT</b> - SMTP Port; Optional if default port (25)
 * <li><b>TO_EMAIL</b> - Email Address to receive; More than one email can be used separated by comma (,)
 * <li><b>FROM_EMAIL</b> - Email Address of sender; may be required by SMTP server
 * <li><b>DEBUG</b> - Optional. TRUE if you want to trace SMTP in server log.
 * <li><b>BASE_ROLE</b> - Optional. Name of the Provisioning Role assigned to all users.
 * </ul>
 * 
 * @author      Lester Rivera
 * @version     %I%, %G%
 * @since       CA Identity Manager 12.0 CR7
 * @see 		org.fasttrack.util.ProvisioningRolesType
 */
public class AssignProvisioningRole extends EventListenerAdapter {
	private static final Log logger = LogFactory.getLog(AssignProvisioningRole.class);
	
	private String _smtp = "";
	private String _port  = "";
	private String _toEmail  = "";
	private String _fromEmail  = "";
	private String[] _recipients;
	private String _debug  = "false";
    private Boolean _canMail  = false;
    private Boolean _isCreate  = false;
    private String _baseRole  = "";
    /** 
     * Business logic performed when the Event Listener is initialized.
     * Retrieves the configuration parameters for the Event 
     * Listener. Any updates to the parameter requires restarting
     * CA Identity Manager environment. 
     */
    public void init(Hashtable imeProperties) throws Exception {
        super.init(imeProperties);
        
        _smtp = (String) imeProperties.get("SMTP_SERVER");
        _port = (String) imeProperties.get("SMTP_PORT");
        _toEmail = (String) imeProperties.get("TO_EMAIL");
        _fromEmail = (String) imeProperties.get("FROM_EMAIL");
        _debug = (String) imeProperties.get("DEBUG");
        _baseRole = (String) imeProperties.get("BASE_ROLE");
        
        // In case multiple email addresses
        if (_toEmail.contains(","))
        	_recipients = _toEmail.split(",");
        
        // Check for all required SMTP attributes to enable email function
        if (!_smtp.isEmpty() && !_fromEmail.isEmpty() && !_toEmail.isEmpty()){
            _canMail  = true;
        }
        
        logger.debug("Set SMTP_SERVER " + _smtp);
        logger.debug("Set SMTP_PORT " + _port);
        logger.debug("Set TO_EMAIL " + _toEmail);
        logger.debug("Set FROM_EMAIL " + _fromEmail);
        logger.debug("Set DEBUG " + _debug);
        logger.debug("Set BASE_ROLE " + _baseRole);
        logger.debug("Can We Email " + _canMail);

    }
    
    /** 
     * Business logic performed after the event occurs.
     */
    public int after(EventContext evtCtx) throws Exception {
        IMEvent evt = evtCtx.getEvent();
        // LJR - Fix on 3/18/10 - isRoleMemeber() call fails in CreateUserEvent
        _isCreate = evtCtx.getEventName().equals(IMEventName.CREATEUSEREVENT);
        if (evt instanceof UserEvent) {
        	// Retrieve the user object in the event
            User user = ((UserEvent) evt).getUser();

        	String[] temp;
            Vector requestedRoles = new Vector(); 							// Temporary list of requested roles
            String delimiter = ","; 										// Delimiter for the list of functional roles
            String userFuncRole = user.getAttribute("eTCustomField52");     // List of functional roles
            
            logger.debug("Processing user " + user.getFriendlyName());
            logger.debug("Functional Role (eTCustomField52) " + userFuncRole);
            logger.debug("Is this a create event? " + _isCreate);

            // Process the Base Role
            ProvisioningRole baseRole = null;
            logger.debug("Determining if Base Role needs to assigned.");
            if (!_baseRole.isEmpty()){
	            try{
	            	// Determines if the base role exists in the system.
	                baseRole =  evtCtx.getProvisioningRoleProvider().findProvisioningRole(_baseRole.trim());
	                if(baseRole.exists() && (_isCreate || !user.isRoleMember(baseRole))) {
	                	// Create an AssignProvisioningRoleEvent to provision the role
                        logger.debug("Generating AssignProvisioningRoleEvent");
                        evtCtx.generateEvent("com.netegrity.ims.events.AssignProvisioningRoleEvent", user, baseRole,null);
                        logger.info("Assign Provisioning Role " + baseRole.getFriendlyName() + " to user " + user.getFriendlyName());
	                }
	            } catch(NoSuchObjectException e){
	                logger.warn("Could not find the Provisioning Role " + baseRole.getFriendlyName());
	
	                // Notify if no provisioning role was found for a base role
	                String mytxt = "The following system did not find a Provisioning Role corresponding to " +
	                        "the Base Role.\n" +
	                        "Base Role: " + baseRole.getFriendlyName() + "\n" +
	                        "UserID: " + user.getFriendlyName() + "\n" +
	                        "Event: " + evt.getEventName() + "\n" +
	                        "\n\n**** This is an automated message sent by the Identity Management System ****";
	                this.postMail("Provisioning Role not found for Base Role.", mytxt);
	            } catch(Exception ex){
	                logger.error("Unspecified error in Base Role Processing: " + ex.getMessage());
	            }
            } else {
            	logger.error("No Base Role was configured for the Event Listener.");
            }
               
            // Process the Functional Roles    
            // FIX: If submitted from the userform, we may get control characters
            userFuncRole = userFuncRole.replaceAll("(\r\n|\r|\n|\n\r|\u001F\u001F)", ",");
            logger.debug("OMF Functional Role (eTCustomField52) after cleanup " + userFuncRole);
            if(!userFuncRole.isEmpty()) {
            	// Split the comma-delimited list 
            	temp = userFuncRole.split(delimiter);  
            	for(int i =0; i < temp.length ; i++) {                   
                	// Set the Provisioning Role from Functional Role
                    ProvisioningRole assignRole = null;
                    try{
                    	logger.debug("Searching for Provisioning Role based on OMF Functional Role value.");
                    	assignRole =  evtCtx.getProvisioningRoleProvider().findProvisioningRole(temp[i].trim());
                        if(assignRole.exists()) {
                            if(_isCreate || !user.isRoleMember(assignRole)) {
                            	// assign the role to the list of requested roles
                                requestedRoles.add(assignRole);
                                // Create an AssignProvisioningRoleEvent to provision the role
                            	logger.debug("Generating AssignProvisioningRoleEvent");
                                evtCtx.generateEvent("com.netegrity.ims.events.AssignProvisioningRoleEvent", user, assignRole,null);
                                logger.info("Assign Provisioning Role " + assignRole.getFriendlyName() + " from the Functional Role (eTCustomField52) for user " + user.getFriendlyName());
                            }
                        }
                    } catch(NoSuchObjectException e){
                    	logger.warn("Could not find the Provisioning Role " + temp[i].trim());
                    	
                    	// Notify if no provisioning role was found for an OMF Role
                        String mytxt = "The following system did not find a Provisioning Role corresponding to " +
                        	"the Functional Role.\n" +
                        	"Functional Role: " + temp[i].trim() + "\n" +
                        	"UserID: " + user.getFriendlyName() + "\n" +
                        	"Event: " + evt.getEventName() + "\n" +
                        	"\n\n**** This is an automated message sent by the Identity Management System ****";
                        this.postMail("Provisioning Role not found for Functional Role.", mytxt);
                    } catch(Exception ex){
                    	logger.error("Unspecified error in Functional Role Processing: " + ex.getMessage());
                    }
            	}//for
                    
            	// Determine if we need to de-provision any roles
            	// Basically, we compare the current list of functional roles from the user attribute with
            	// the list of provisioning roles of type functional role that is already assigned to the user.
            	if (requestedRoles.size() > 0 && !_isCreate){
            		// Determine what the type of the newly requested roles
            		ProvisioningRolesType newRoles = new ProvisioningRolesType(requestedRoles);
            		// Determine the type of the currently assigned roles
            		ProvisioningRolesType assignedRoles = new ProvisioningRolesType(user.getProvisioningRolesMember());
            		logger.debug("Functional roles found. Requested: " + newRoles.getFunctionalRoles().size() + " Assigned: "+ assignedRoles.getFunctionalRoles().size());
            		if (assignedRoles.hasFunctionalRoles() && newRoles.hasFunctionalRoles()) {
            			logger.debug("User has previously assigned functional roles.");
            			Iterator vItr = assignedRoles.getFunctionalRoles().iterator(); 
            			ProvisioningRole checkRole = null;
            			logger.debug("Ready to remove previously assigned functional roles.");
            			while(vItr.hasNext()){
            				checkRole = (ProvisioningRole) vItr.next();
            				 // Create an RevokeProvisioningRoleEvent to de-provision the role
            				logger.debug("Generating RevokeProvisioningRoleEvent");
            				evtCtx.generateEvent("com.netegrity.ims.events.RevokeProvisioningRoleEvent", user, checkRole,null);
            				logger.info("Remove Assigned Functional Provisioning Role " + checkRole.getFriendlyName() + " for user " + user.getFriendlyName());
            			} //While
            		}
            	}
            } else { //if
            	logger.warn("No Functional Role Assigned to User: " + user.getFriendlyName());
            } 
                
        }
        return CONTINUE;
    }
    /** 
     * Method to send email to select recipients based upon parameter settings in the
     * Event Listener configuration.
     * 
     * @param subject	the subject line for the email
     * @param message	the message body of the email
     */
    private void postMail(String subject, String message) throws MessagingException
    {
        boolean debug = false;
        
        if (_canMail){

	         //Set the host smtp address
    		 logger.debug("Setting properties.");
	         Properties props = new Properties();
	         logger.debug("Setting mail.smtp.host properties to " + _smtp);
	         props.put("mail.smtp.host", _smtp);
	         if (_port != null && _port.length() != 0){ // set by default, but can be customized
	        	 logger.debug("Setting mail.smtp.port properties to " + _port);
	        	 props.put("mail.smtp.port", _port);
	         } else {
	        	 logger.debug("Setting mail.smtp.port properties to default port (25).");
	        	 props.put("mail.smtp.port", "25");
	         }
	         logger.debug("Completed setting properties.");
	
	        // create some properties and get the default Session
	         logger.debug("Getting session.");
	        Session session = Session.getDefaultInstance(props, null);
	        if (_debug != null && _debug.equalsIgnoreCase("true")){ debug = true;} // Set debug to true if _debug value is true
	        session.setDebug(debug);
            try {
                // create a message
                logger.debug("Getting message.");
                Message msg = new MimeMessage(session);

                // set the from and to address
                logger.debug("Setting from address.");
                InternetAddress addressFrom = new InternetAddress(_fromEmail);
                msg.setFrom(addressFrom);

                // Process the recipients of the email
                logger.debug("Setting to address.");
                if (_recipients.length != 0) {
                        logger.debug("Setting multiple addresses.");
                        InternetAddress[] addressTo = new InternetAddress[_recipients.length]; 
                        for (int i = 0; i < _recipients.length; i++)
                        {
                            addressTo[i] = new InternetAddress(_recipients[i]);
                        }
                        msg.setRecipients(Message.RecipientType.TO, addressTo);
                } else {
                        logger.debug("Setting single address.");
                        InternetAddress[] addressTo = new InternetAddress[1]; 
                        addressTo[0] = new InternetAddress(_toEmail);
                        msg.setRecipients(Message.RecipientType.TO, addressTo);
                }

                // Optional : You can also set your custom headers in the Email if you Want
                //msg.addHeader("MyHeaderName", "myHeaderValue");

                // Setting the Subject and Content Type
                logger.debug("Sending email. Subject - " + subject + " - to: " + _toEmail);
                msg.setSubject(subject);
                msg.setContent(message, "text/plain");
                Transport.send(msg);
	        } catch (Exception ex) {
	        	logger.error(" Error postMail exception: " + ex.getMessage());
	        }
        } else {
        	logger.debug("No email sent. SMTP settings must be set.");
        }
    } //postMail

}