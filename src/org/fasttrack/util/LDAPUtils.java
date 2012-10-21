package org.fasttrack.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.directory.*;
import com.netegrity.rtl.jce.JSafeTools;


import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory;

import java.util.Properties;
/**
 * A utility class to work with LDAP services.
 * <p>
 * A known issue is that the LDAP connection is always
 * set to a secure LDAPS/SSL option. 
 * 
 * @author      Lester Rivera
 * @version     %I%, %G%
 * @since       CA Identity Manager 12.0 CR7
 */
public class LDAPUtils {

    private static final Log logger = LogFactory.getLog(LDAPUtils.class);
    private Properties props = new Properties();
    static String ATTRIBUTE_FOR_USER = "sAMAccountName";
    private String _host = null;
    private String _port = null;
    private String _domain = null;
    private String _rootDN = null;
    private String _authType = null;
    private String _protocol = null;
    private String _username = null;
    private String _password = null;
    /** 
     * Constructor will load LDAP server connection information from the
     * ldap-target.properties file.
     */
    public LDAPUtils() {
        //Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
        props = new Properties();
        try {
            //File file = new File("D:\\AuthenticationModule\\ldap-target.properties");
            File file = new File(LDAPUtils.class.getProtectionDomain().getCodeSource().getLocation().getPath() + File.separator + "ldap-target.properties");
            if (file.exists()) {
                props.load(new FileInputStream(file));
            } else {
                File localfile = new File("ldap-target.properties");
                if (!localfile.exists()) {
                    logger.error("No properties file found.");
                }
                props.load(new FileInputStream(localfile)); //in case it is build directory
            }

        } catch (IOException e) {
            logger.error("An error occured reading the properties file.");
            logger.error(e.getMessage());
        }
        // the keystore that holds trusted root certificates
        System.setProperty("javax.net.ssl.trustStore", this.props.getProperty("certificate_store"));
        //System.setProperty("javax.net.debug", "all");
    }

    /**
     * This method will authenticate a user to an LDAP environment.
     * 
     * @param username	The UserID provided by the user
     * @param password	The password provided by the user. 
     * @param env		The LDAP environment being accessed. Must be configured in the 
     * ldap-target.properties file.
     * @return true, if authentication is successful; false, if the authentication failed. 
     */
    public boolean authenticateUser(String username, String password, String env) {

        //_provider = this.props.getProperty(env + "_provider");
        _host = this.props.getProperty(env + "_host");
        _port = this.props.getProperty(env + "_port");
        _domain = this.props.getProperty(env + "_domain");
        _rootDN = this.props.getProperty(env + "_rootDN");
        _authType = this.props.getProperty(env + "_authType");
        _protocol = this.props.getProperty(env + "_protocol");

        logger.debug("environment: " + env);
        logger.debug("host: " + _host);
        logger.debug("port: " + _port);
        logger.debug("domain: " + _domain);
        logger.debug("rootDN: " + _rootDN);
        logger.debug("authType: " + _authType);
        logger.debug("protocol: " + _protocol);

        String returnedAtts[] = {"cn", "givenName", "mail"};
        String searchFilter = "(&(objectClass=user)(" + ATTRIBUTE_FOR_USER + "=" + username + "))";
        // Create the search controls

        SearchControls searchCtls = new SearchControls();
        searchCtls.setReturningAttributes(returnedAtts);
        // Specify the search scope

        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        String searchBase = _rootDN;
        Hashtable<String, String> environment = new Hashtable<String, String>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, "ldap://" + _host + ":" + _port);
        environment.put(Context.SECURITY_AUTHENTICATION, _authType);
        environment.put(Context.SECURITY_PRINCIPAL, username + "@" + _domain);
        environment.put(Context.SECURITY_CREDENTIALS, password);
        environment.put(Context.SECURITY_PROTOCOL, _protocol);
        LdapContext ctxGC = null;
        try {
            ctxGC = new InitialLdapContext(environment, null);
            // Search for objects in the GC using the filter

            NamingEnumeration<?> answer = ctxGC.search(searchBase, searchFilter, searchCtls);
            while (answer.hasMoreElements()) {
                SearchResult sr = (SearchResult) answer.next();
                Attributes attrs = sr.getAttributes();
                if (attrs != null) {
                    logger.info("User " + username + " has been authenticated by " + _host);
                    return true;
                }
            }

        } catch (NamingException e) {
            logger.error("An error occured connecting to LDAP server.");
            logger.error(e.getMessage());
        } catch (Exception ex) {
            logger.error("A general error occured connecting to LDAP server.");
            logger.error(ex.getMessage());
        }
        logger.error("User " + username + " has NOT been authenticated by " + _host);
        return false;
    }

    /**
     * This method will authenticate a user to a series of LDAP environments.
     * The number and sequence of the LDAP environments is based on the settings
     * in the ldap-target.properties file.
     * 
     * @param username	The UserID provided by the user
     * @param password	The password provided by the user. 
     * @return true, if authentication is successful in at least one environment; 
     * false, if the authentication failed in all environments. 
     */
    public boolean authenticateUser(String username, String password) {
        String[] environ = null;

        environ = this.props.getProperty("environments").split(",");
        logger.debug("LDAP Authentication for " + environ.length + " environments.");
        LDAPUtils ldap = new LDAPUtils();
        for (int i = 0; i < environ.length; i++) {
            String attempted = environ[i].trim();
            logger.debug("Attempting LDAP Authentication to " + attempted + " environment.");
            if (ldap.authenticateUser(username, password, attempted)) {
                return true;
            }
        }
        return false;
    }
    /**
     * This method submits a query to the LDAP environment.
     * 
     * @param env		The LDAP environment being accessed. Must be configured in the 
     * ldap-target.properties file.
     * @param filter	The LDAP Filter Query to search with.
     * @param attrList	The LDAP attributes to return.
     * @return List of attributes the results of the query.
     */
    public Attributes Query(String env, String filter, String[] attrList) {
        logger.debug("Setting up LDAP Query for " + env + " environment only.");
        _username = this.props.getProperty(env + "_username");
        _password = this.props.getProperty(env + "_encrypted");
        return this.Query(_username, _password, env, filter, attrList);
    }
    /**
     * This method submits a query to the LDAP environment.
     * 
     * @param username	The UserID provided by the user
     * @param encryptedpassword	The encrypted hash of the password provided by the user. 
     * @param env		The LDAP environment being accessed. Must be configured in the 
     * ldap-target.properties file.
     * @param filter	The LDAP Filter Query to search with.
     * @param attrList	The LDAP attributes to return.
     * @return List of attributes the results of the query.
     */
    public Attributes Query(String username, String encryptedpassword, String env, String filter, String[] attrList) {
        //char [] tmpPassword = null;
        String tmpPassword = "";
        logger.debug("LDAP Query for " + env + " environment only.");
        //_provider = this.props.getProperty(env + "_provider");
        _host = this.props.getProperty(env + "_host");
        _port = this.props.getProperty(env + "_port");
        _domain = this.props.getProperty(env + "_domain");
        _rootDN = this.props.getProperty(env + "_rootDN");
        _authType = this.props.getProperty(env + "_authType");
        _protocol = this.props.getProperty(env + "_protocol");
        if (!encryptedpassword.isEmpty())
            tmpPassword = JSafeTools.decryptText(encryptedpassword).toString();
        
        logger.debug("environment: " + env);
        logger.debug("host: " + _host);
        logger.debug("port: " + _port);
        logger.debug("domain: " + _domain);
        logger.debug("rootDN: " + _rootDN);
        logger.debug("authType: " + _authType);
        logger.debug("protocol: " + _protocol);
        logger.debug("username: " + username);
        //logger.debug("Password: " + tmpPassword);

        String returnedAtts[] = attrList;
        String searchFilter = filter;
        Attributes attrs = new BasicAttributes();
        // Create the search controls

        SearchControls searchCtls = new SearchControls();
        searchCtls.setReturningAttributes(returnedAtts);
        // Specify the search scope

        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        String searchBase = _rootDN;
        Hashtable<String, String> environment = new Hashtable<String, String>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, "ldap://" + _host + ":" + _port);
        environment.put(Context.SECURITY_AUTHENTICATION, _authType);
        environment.put(Context.SECURITY_PRINCIPAL, username + "@" + _domain);
        environment.put(Context.SECURITY_CREDENTIALS, tmpPassword);
        environment.put(Context.SECURITY_PROTOCOL, _protocol);
        LdapContext ctxGC = null;
        try {
            ctxGC = new InitialLdapContext(environment, null);
            // Search for objects in the GC using the filter

            NamingEnumeration<?> answer = ctxGC.search(searchBase, searchFilter, searchCtls);
            while (answer.hasMoreElements()) {
                SearchResult sr = (SearchResult) answer.next();
                attrs = sr.getAttributes();
                return attrs;
            }
        } catch (NamingException e) {
            logger.error("An error occured connecting to LDAP server.");
            logger.error(e.getMessage());
        } catch (Exception ex){
            logger.error("A general error occured connecting to LDAP server.");
            logger.error(ex.getMessage());
        } finally {
            return attrs;
        }
    }
     /**
     * This method retrieves a value from the properties file.
     * 
     * @param attribute		Name of attribute configured in the 
     * ldap-target.properties file.
     * @return String of the value.
     */
    public String getPropertyValue(String attribute){
        return this.props.getProperty(attribute);
    }
     /**
     * This method retrieves a value of the attribute in the object in LDAP.
     * 
     * @param ldapObject		DN of the LDAP object.
     * @param ldapAttribute		Attribute of the LDAP object.
     * @param env				The LDAP environment being accessed. Must be configured in the 
     * ldap-target.properties file.
     * @return String of the value.
     */
    public String getAttributeValue(String ldapObject, String ldapAttribute, String env){
        String tmpPassword = "";
        //String env = "";
        String tempVal = "";
        logger.debug("LDAP getAttribute for " + env + " environment only.");
        _username = this.props.getProperty(env + "_username");
        _password = this.props.getProperty(env + "_encrypted");
        _host = this.props.getProperty(env + "_host");
        _port = this.props.getProperty(env + "_port");
        _domain = this.props.getProperty(env + "_domain");
        _rootDN = this.props.getProperty(env + "_rootDN");
        _authType = this.props.getProperty(env + "_authType");
        _protocol = this.props.getProperty(env + "_protocol");
        if (!_password.isEmpty())
            tmpPassword = JSafeTools.decryptText(_password).toString();
        
        logger.debug("environment: " + env);
        logger.debug("host: " + _host);
        logger.debug("port: " + _port);
        logger.debug("domain: " + _domain);
        logger.debug("rootDN: " + _rootDN);
        logger.debug("authType: " + _authType);
        logger.debug("protocol: " + _protocol);
        logger.debug("username: " + _username);       
        
        Hashtable<String, String> environment = new Hashtable<String, String>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, "ldap://" + _host + ":" + _port);
        environment.put(Context.SECURITY_AUTHENTICATION, _authType);
        environment.put(Context.SECURITY_PRINCIPAL, _username);
        environment.put(Context.SECURITY_CREDENTIALS, tmpPassword);
        environment.put(Context.SECURITY_PROTOCOL, _protocol);
        
        try{
            DirContext ctx = new InitialDirContext(environment);
            Attributes orig = ctx.getAttributes(ldapObject,new String[]{ldapAttribute});    
            tempVal = orig.get(ldapAttribute).get().toString();            
            ctx.close();
        } catch (Exception ex) {
            logger.error("General exception occurred connecting to LDAP: " + ex.getMessage());
        }
        return tempVal;
    }
     /**
     * Add a value for the attribute of the specified object in LDAP
     * 
     * @param ldapObject		DN of the LDAP object.
     * @param ldapAttribute		Attribute of the LDAP object.
     * @param ldapValue			Value to add.
     * @param env				The LDAP environment being accessed. Must be configured in the 
     * ldap-target.properties file.
     * @return boolean. Success or failed.
     */
    public boolean addAttributeValue(String ldapObject, String ldapAttribute, String ldapValue, String env){
        return this.setAttributeValue(ldapObject, ldapAttribute, DirContext.ADD_ATTRIBUTE, ldapValue, env);
    }
     /**
     * Replace a value for the attribute of the specified object in LDAP
     * 
     * @param ldapObject		DN of the LDAP object.
     * @param ldapAttribute 	Attribute of the LDAP object.
     * @param ldapValue 		Value to replace.
     * @param env 				The LDAP environment being accessed. Must be configured in the 
     * ldap-target.properties file.
     * @return boolean. Success or failed.
     */
    public boolean replaceAttributeValue(String ldapObject, String ldapAttribute, String ldapValue, String env){
        return this.setAttributeValue(ldapObject, ldapAttribute, DirContext.REPLACE_ATTRIBUTE, ldapValue, env);
    }
     /**
     * Remove the attribute of the specified object in LDAP
     * 
     * @param ldapObject		DN of the LDAP object.
     * @param ldapAttribute		Attribute of the LDAP object.
     * @param env				The LDAP environment being accessed. Must be configured in the 
     * ldap-target.properties file.
     * @return boolean. Success or failed.
     */
    public boolean removeAttributeValue(String ldapObject, String ldapAttribute, String env){
        return this.setAttributeValue(ldapObject, ldapAttribute, DirContext.REMOVE_ATTRIBUTE, "", env);
    }
     /**
     * Set the attribute of the specified object in LDAP
     * 
     * @param ldapObject		DN of the LDAP object.
     * @param ldapAttribute		Attribute of the LDAP object.
     * @param ldapValue			Value to replace.
     * @param env				The LDAP environment being accessed. Must be configured in the 
     * ldap-target.properties file.
     * @return boolean. Success or failed.
     */
    private boolean setAttributeValue(String ldapObject, String ldapAttribute, int ldapAction, String ldapValue, String env){
        String tmpPassword = "";
        //String env = "";
        boolean tempVal = true;
        logger.debug("LDAP getAttribute for " + env + " environment only.");
        _username = this.props.getProperty(env + "_username");
        _password = this.props.getProperty(env + "_encrypted");
        _host = this.props.getProperty(env + "_host");
        _port = this.props.getProperty(env + "_port");
        _domain = this.props.getProperty(env + "_domain");
        _rootDN = this.props.getProperty(env + "_rootDN");
        _authType = this.props.getProperty(env + "_authType");
        _protocol = this.props.getProperty(env + "_protocol");
        if (!_password.isEmpty())
            tmpPassword = JSafeTools.decryptText(_password).toString();
        
        logger.debug("environment: " + env);
        logger.debug("host: " + _host);
        logger.debug("port: " + _port);
        logger.debug("domain: " + _domain);
        logger.debug("rootDN: " + _rootDN);
        logger.debug("authType: " + _authType);
        logger.debug("protocol: " + _protocol);
        logger.debug("username: " + _username);   
        
        logger.debug("Object: " + ldapObject);
        logger.debug("Attribute: " + ldapAttribute);
        logger.debug("Value: " + ldapValue); 
        logger.debug("Action: " + ldapAction);
        
        Hashtable<String, String> environment = new Hashtable<String, String>();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        environment.put(Context.PROVIDER_URL, "ldap://" + _host + ":" + _port);
        environment.put(Context.SECURITY_AUTHENTICATION, _authType);
        environment.put(Context.SECURITY_PRINCIPAL, _username);
        environment.put(Context.SECURITY_CREDENTIALS, tmpPassword);
        environment.put(Context.SECURITY_PROTOCOL, _protocol);
        
        DirContext ctx = null;
        try{
            ctx = new InitialDirContext(environment); 
            // Specify the changes to make
	    ModificationItem[] mods = new ModificationItem[1];
            // Replace the attribute with a new value
            if (ldapAction != DirContext.REMOVE_ATTRIBUTE){
                mods[0] = new ModificationItem(ldapAction,new BasicAttribute(ldapAttribute, ldapValue));
                logger.debug("We are adding or modifying the attribute.");
            } else {
                mods[0] = new ModificationItem(ldapAction,new BasicAttribute(ldapAttribute));
                logger.debug("We are removing the attribute.");
            }
	    // Perform the requested modifications on the named object
	    ctx.modifyAttributes(ldapObject, mods);
            logger.debug("Modify completed.");
            ctx.close();
        } catch (Exception ex) {
            logger.error("General exception occurred connecting to LDAP: " + ex.getMessage());
            tempVal = false;
        }

        return tempVal;
    }
    /**
    * This main class exist for testing purposes only.
    */
    public static void main(String[] args) throws Exception {
        InputStreamReader converter = new InputStreamReader(System.in);
        BufferedReader in = new BufferedReader(converter);
        System.out.println("Please type username:");
        String username = in.readLine();
        System.out.println("Please type password:");
        String password = in.readLine();

        try {
            LDAPUtils ldap = new LDAPUtils();
            boolean att = ldap.authenticateUser(username, password);
            //System.out.println("Results: " + att);
            if (att) {
                System.out.println("Login successful.");
            } else {
                System.out.println("Login failed. Your userid is invalid or password incorrect.");
            }
            Attributes attrs = ldap.Query("ADQuery", "(&(objectClass=user)(" + ATTRIBUTE_FOR_USER + "=" + username + "))", new String[]{"cn", "givenName", "mail"});
            if (attrs.size() > 0) {
                System.out.println(attrs.get("cn").toString());
                System.out.println(attrs.get("mail").toString());
                System.out.println("Records: " + attrs.size());
            } else {
                System.out.println("No attributes returned.");
            }
        } catch (Exception e) {
            System.err.println("Unable to connect to LDAP.");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
