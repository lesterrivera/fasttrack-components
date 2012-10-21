package org.fasttrack.util;

import com.netegrity.llsdk6.imsapi.managedobject.ProvisioningRole;
import com.netegrity.llsdk6.imsapi.managedobject.Role.CustomFieldId;

import java.util.Iterator;
import java.util.Vector;

import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;
/**
 * ProvisioningRoleType is a utility class which takes a vector of 
 * ProvisioningRole objects and classifies each object according
 * to the following custom types of Provisioning Roles:
 * <ul>
 * <li><b>Base Role</b> - Assigned to all users in a company
 * <li><b>Application Role</b> - Assigns application-specific access
 * <li><b>Functional Role</b> - Assigns access based on job function 
 * <li><b>No Type</b> - All other Provisioning Roles not explicitly defined
 * </ul>
 * <p>
 * The custom type for a Provisioning Role is assigned by the value 
 * assigned to CustomField01 attribute in the configuration settings 
 * for each Provisioning Role in CA Identity Manager. The valid values
 * for CustomField01 attribute are:
 * <ul>
 * <li>Base
 * <li>Application
 * <li>Functional
 * </ul>
 * <p> 
 * If other values are used, or if left blank, the Provisioning Roles is 
 * set to No Type.
 * <p>
 * The utility class is designed to function with the AssignProvisioningRole
 * Event Listener.
 * 
 * @author      Lester Rivera
 * @version     %I%, %G%
 * @since       CA Identity Manager 12.0 CR7
 * @see org.fasttrack.evt.AssignProvisioningRole
 */
public class ProvisioningRolesType {
	private static final Log logger = LogFactory.getLog(ProvisioningRolesType.class);
	
	private Vector<ProvisioningRole> _applicationRoles = new Vector<ProvisioningRole>();
	private Vector<ProvisioningRole> _functionalRoles = new Vector<ProvisioningRole>();
	private Vector<ProvisioningRole> _baseRoles = new Vector<ProvisioningRole>();
	private Vector<ProvisioningRole> _noTypeRoles = new Vector<ProvisioningRole>();
    /** 
     * Processes the vector list of Provisioning Roles to classify according to the 
     * defined business rules.
     *
     * @param ListOfRoles	the vector of Provisioning Role objects
     */
	public ProvisioningRolesType(Vector<ProvisioningRole> ListOfRoles) throws Exception {
        if (ListOfRoles.size() > 0) {
        	logger.debug("Number of Provisoning Roles: " + ListOfRoles.size());
        	Iterator<?> vItr = ListOfRoles.iterator(); 
        	ProvisioningRole checkRole = null;
        	String findRole = null;
        	while(vItr.hasNext()){
        		try{
	        		checkRole = (ProvisioningRole) vItr.next();
        			findRole = checkRole.getFriendlyName();
	        		// Make sure you have an object to process
	                if (checkRole.exists()) {
	                	logger.debug("Processing Provisioning Role: " + checkRole.getFriendlyName());
	                	String roleType = null;
	                	// Fix: LJR 01-28-2010 - the call to customfield1 was retrieving null
	                	roleType = checkRole.getCustomField(CustomFieldId.CUSTOM01); //Type of Provisioning Role from CustomField01
	                	// Check if value is blank
	                	if (roleType.length() > 0) {
	                		logger.debug("Role Type (customfield1): " + roleType);
		                	if (roleType.equalsIgnoreCase("BASE")){
		                		_baseRoles.addElement(checkRole); 					// Add to list of base roles
		                	} else if (roleType.equalsIgnoreCase("FUNCTIONAL")){
		                		_functionalRoles.addElement(checkRole);				// Add to list of functional roles
		                	} else if (roleType.equalsIgnoreCase("APPLICATION")){
		                		_applicationRoles.addElement(checkRole);			// Add to list of application roles
		                	} else {
		                		_noTypeRoles.addElement(checkRole);					// Add to list of no type roles
		                	}
	                	} else {
	                		logger.debug("Provisioning Role has no Role Type (customfield1) value: " + checkRole.getFriendlyName());
	                		_noTypeRoles.addElement(checkRole);						// Add to list of no type roles
	                	}
	                } else {
	                	logger.debug("Provisioning Role does not exist: " + findRole);
	                } //if
                } catch(Exception ex){
                	logger.debug("Error processing Provisioning Role " + findRole + ": " + ex.getMessage());
                }
        	} // while
        } else {
        	logger.debug("Provisioning Roles list is empty.");
        }
	} // constructor
	/**
     * Determines if the list of Provisioning Roles contains Application Roles.
     * @return	true	if the list contains Application Roles 
     * @return	false	otherwise
     */
	public boolean hasApplicationRoles(){
		return (_applicationRoles.size() > 0);
	}
	/**
     * Determines if the list of Provisioning Roles contains Base Roles.
     * @return	true	if the list contains Base Roles 
     * @return	false	otherwise
     */
	public boolean hasBaseRoles(){
		return (_baseRoles.size() > 0);
	}
	/**
     * Determines if the list of Provisioning Roles contains Functional Roles.
     * @return	true	if the list contains Functional Roles 
     * @return	false	otherwise
     */
	public boolean hasFunctionalRoles(){
		return (_functionalRoles.size() > 0);
	}
	/**
     * Determines if the list of Provisioning Roles contains roles of no type.
     * @return	true	if the list contains roles of no type 
     * @return	false	otherwise
     */
	public boolean hasNoTypeRoles(){
		return (_noTypeRoles.size() > 0);
	}
	/**
     * Method returns a vector list of Provisioning Roles objects that are Application Roles
     * for this ProvisioningRoleType object. The returned vector is a subset of the full 
     * vector list of Provisioning Roles assigned to the ProvisioningRoleType object.
     * @return	Vector of Provisioning Role objects
     */
	public java.util.Vector<ProvisioningRole> getApplicationRoles(){
		return _applicationRoles;
	}
	/**
     * Method returns a vector list of Provisioning Roles objects that are Base Roles
     * for this ProvisioningRoleType object. The returned vector is a subset of the full 
     * vector list of Provisioning Roles assigned to the ProvisioningRoleType object.
     * @return	Vector of Provisioning Role objects
     */
	public java.util.Vector<ProvisioningRole> getBaseRoles(){
		return _baseRoles;
	}
	/**
     * Method returns a vector list of Provisioning Roles objects that are Functional Roles
     * for this ProvisioningRoleType object. The returned vector is a subset of the full 
     * vector list of Provisioning Roles assigned to the ProvisioningRoleType object.
     * @return	Vector of Provisioning Role objects
     */
	public java.util.Vector<ProvisioningRole> getFunctionalRoles(){
		return _functionalRoles;
	}
	/**
     * Method returns a vector list of Provisioning Roles objects that are have no type
     * for this ProvisioningRoleType object. The returned vector is a subset of the full 
     * vector list of Provisioning Roles assigned to the ProvisioningRoleType object.
     * @return	Vector of Provisioning Role objects
     */
	public java.util.Vector<ProvisioningRole> getNoTypeRoles(){
		return _noTypeRoles;
	}
} // Class

