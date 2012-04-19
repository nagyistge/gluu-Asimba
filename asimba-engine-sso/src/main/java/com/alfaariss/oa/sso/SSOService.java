/*
 * Asimba Server
 * 
 * Copyright (C) 2012 Asimba
 * Copyright (C) 2007-2010 Alfa & Ariss B.V.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see www.gnu.org/licenses
 * 
 * Asimba - Serious Open Source SSO - More information on www.asimba.org
 * 
 */
package com.alfaariss.oa.sso;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;

import com.alfaariss.oa.OAException;
import com.alfaariss.oa.SystemErrors;
import com.alfaariss.oa.UserEvent;
import com.alfaariss.oa.UserException;
import com.alfaariss.oa.api.IComponent;
import com.alfaariss.oa.api.attribute.IAttributes;
import com.alfaariss.oa.api.authentication.IAuthenticationMethod;
import com.alfaariss.oa.api.authentication.IAuthenticationProfile;
import com.alfaariss.oa.api.configuration.IConfigurationManager;
import com.alfaariss.oa.api.persistence.PersistenceException;
import com.alfaariss.oa.api.requestor.IRequestor;
import com.alfaariss.oa.api.session.ISession;
import com.alfaariss.oa.api.session.SessionState;
import com.alfaariss.oa.api.tgt.ITGT;
import com.alfaariss.oa.api.user.IUser;
import com.alfaariss.oa.engine.core.Engine;
import com.alfaariss.oa.engine.core.attribute.UserAttributes;
import com.alfaariss.oa.engine.core.attribute.gather.AttributeGatherer;
import com.alfaariss.oa.engine.core.attribute.release.IAttributeReleasePolicy;
import com.alfaariss.oa.engine.core.attribute.release.factory.IAttributeReleasePolicyFactory;
import com.alfaariss.oa.engine.core.authentication.AuthenticationException;
import com.alfaariss.oa.engine.core.authentication.AuthenticationProfile;
import com.alfaariss.oa.engine.core.authentication.factory.IAuthenticationProfileFactory;
import com.alfaariss.oa.engine.core.requestor.RequestorPool;
import com.alfaariss.oa.engine.core.requestor.factory.IRequestorPoolFactory;
import com.alfaariss.oa.engine.core.session.factory.ISessionFactory;
import com.alfaariss.oa.engine.core.tgt.factory.ITGTFactory;

/**
 * Authentication and SSO Service.
 * 
 * Contains basic functionality that can be called from an SSO system 
 * e.g. WebSSO. 
 *  
 * @author EVB
 * @author Alfa & Ariss
 *
 */
public class SSOService implements IComponent
{    

    private boolean _bSingleSignOn;          
    private Log _systemLogger;
    private IConfigurationManager _configurationManager;
    private ISessionFactory<?> _sessionFactory;
    private ITGTFactory<?> _tgtFactory;
    private IRequestorPoolFactory _requestorPoolFactory;
    private IAuthenticationProfileFactory _authenticationProfileFactory;
    private AttributeGatherer _attributeGatherer;
    private IAttributeReleasePolicyFactory _attributeReleasePolicyFactory;
    
    /**
     * Create a new SSO Service.
     */
    public SSOService()
    {
        _systemLogger = LogFactory.getLog(SSOService.class);
        _bSingleSignOn = true;    
    }

    /**
     * Start the SSO Service.
     * @see IComponent#start(IConfigurationManager, org.w3c.dom.Element)
     */
    public void start(IConfigurationManager oConfigurationManager, 
        Element eConfig) throws OAException
    {              
        if(oConfigurationManager == null)
            throw new IllegalArgumentException(
                "Supplied ConfigurationManager is empty");
        
        Engine engine = Engine.getInstance();
        _configurationManager = oConfigurationManager;
        _sessionFactory = engine.getSessionFactory();
        _tgtFactory = engine.getTGTFactory();
        _requestorPoolFactory = engine.getRequestorPoolFactory();
        _authenticationProfileFactory = 
            engine.getAuthenticationProfileFactory();
        _attributeGatherer = engine.getAttributeGatherer();
        _attributeReleasePolicyFactory = 
            engine.getAttributeReleasePolicyFactory();
        
        //SSO configuration
        readDefaultConfiguration(eConfig); 
        
        _systemLogger.info("SSO Service started");    
    }

    /**
     * Restart the SSO Service.
     * @see com.alfaariss.oa.api.IComponent#restart(org.w3c.dom.Element)
     */
    public void restart(Element eConfig) throws OAException
    {
        synchronized(this)
        {
            //Get new components
            Engine engine = Engine.getInstance();
            _sessionFactory = engine.getSessionFactory();
            _tgtFactory = engine.getTGTFactory();
            _requestorPoolFactory = engine.getRequestorPoolFactory();
            _authenticationProfileFactory = 
                engine.getAuthenticationProfileFactory();       
            
            //SSO
            readDefaultConfiguration(eConfig); 
            _systemLogger.info("SSO Service restarted");
        }        
    }
    
    /**
     * Stop the SSO Service.
     * @see com.alfaariss.oa.api.IComponent#stop()
     */
    public void stop()
    {    
        _systemLogger.info("SSO Service stopped");        
    }

    /**
     * Retrieve an authentication session.
     * @param sId The session ID.
     * @return The session, or null if not found.
     * @throws SSOException If retrieval fails.
     */
    public ISession getSession(String sId) throws SSOException
    {
        try
        {
            return _sessionFactory.retrieve(sId);
        }
        catch (OAException e)
        {
            _systemLogger.warn("Could not retrieve session",e);
            //wrap exception
            throw new SSOException(e.getCode(), e);
        }       
    }
    
    /**
     * Retrieve TGT.
     * @param sTGTId The TGT ID.
     * @return The TGT, or null if not found.
     * @throws SSOException If retrieval fails.
     */
    public ITGT getTGT(String sTGTId) throws SSOException
    {
        try
        {
            return _tgtFactory.retrieve(sTGTId);  
        }
        catch (OAException e)
        {
            _systemLogger.warn("Could not retrieve TGT",e);
            //wrap exception
            throw new SSOException(e.getCode(), e);
        }  
    }
    
    /**
     * Retrieve requestor pool for this authentication session.
     * @param oSession The authentication session
     * @return The requestor pool
     * @throws SSOException if retrieval fails
     */
    public RequestorPool getRequestorPool(ISession oSession) throws SSOException
    {
        try
        {
            return _requestorPoolFactory.getRequestorPool(
                oSession.getRequestorId());
        }
        catch (OAException e)
        {
            _systemLogger.warn("Could not retrieve requestor pool",e);
            //wrap exception
            throw new SSOException(e.getCode(), e);
        }  
    }
    
    /**
     * Retrieve requestor for this authentication session.
     * @param sID The requestor id
     * @return The requestor
     * @throws SSOException if retrieval fails
     * @since 1.0
     */
    public IRequestor getRequestor(String sID) throws SSOException
    {
        try
        {
            return _requestorPoolFactory.getRequestor(sID);
        }
        catch (OAException e)
        {
            _systemLogger.warn("Could not retrieve requestor: " + sID, e);
            //wrap exception
            throw new SSOException(e.getCode());
        }  
    }
    
    /**
     * Retrieve requestor for this authentication session.
     * @param oSession The authentication session
     * @return The requestor
     * @throws SSOException if retrieval fails
     */
    public IRequestor getRequestor(ISession oSession) throws SSOException
    {
        try
        {
            return _requestorPoolFactory.getRequestor(
                oSession.getRequestorId());
        }
        catch (OAException e)
        {
            _systemLogger.warn("Could not retrieve requestor ",e);
            //wrap exception
            throw new SSOException(e.getCode(), e);
        }  
    }

    /**
     * Retrieve all required authentication profiles for the supplied 
     * requestor pool.
     * 
     * @param oRequestorPool the requestor pool
     * @return List&lt;AuthenticationProfile&gt; containing the profiles 
     * @throws SSOException If retrieval fails
     */
    public List<IAuthenticationProfile> getAllAuthNProfiles(
        RequestorPool oRequestorPool) throws SSOException
    {     
        try
        {
            List<IAuthenticationProfile> listProfiles = new Vector<IAuthenticationProfile>();
            for (String sProfile: oRequestorPool.getAuthenticationProfileIDs())
            {
                IAuthenticationProfile oAuthNProfile = 
                    _authenticationProfileFactory.getProfile(sProfile);
                if(oAuthNProfile == null)
                {
                    _systemLogger.warn(
                        "AuthN Profile not found: " + sProfile);
                    throw new OAException(SystemErrors.ERROR_INTERNAL);
                }
                if (oAuthNProfile.isEnabled())
                    listProfiles.add(oAuthNProfile);
            }
            return listProfiles;    
        }
        catch (OAException e)
        {
            _systemLogger.warn("Could not retrieve AuthN profiles",e);
            //wrap exception
            throw new SSOException(e.getCode(), e);
        }  
    }
    
    /**
     * Returns the authentication profile id.
     * @param sID The ID of the Authentication Profile
     * @return the specified IAuthenticationProfile
     * @throws SSOException If authentication profile could not be retrieved.
     * @since 1.0
     */
    public IAuthenticationProfile getAuthNProfile(String sID) 
        throws SSOException
    {
        IAuthenticationProfile authenticationProfile = null;
        try
        {
            authenticationProfile = 
                _authenticationProfileFactory.getProfile(sID);
        }
        catch (AuthenticationException e)
        {
            _systemLogger.warn("Could not retrieve AuthN profile: " + sID, e);
            //wrap exception
            throw new SSOException(e.getCode());
        }
        return authenticationProfile;
    }
    
    /**
     * Retrieve selected profile.
     *
     * @param oSession The authentication session.
     * @param sSelectedProfile The profile chosen by user
     * @param bShowAllways Is the selection mandatory? 
     * @return Selected AuthenticationProfile or <code>null</code>
     * @throws UserException If selection fails
     * @throws SSOException If selection fails, due to internal error
     */
    public IAuthenticationProfile getSelectedAuthNProfile(ISession oSession, 
        String sSelectedProfile, boolean bShowAllways) 
        throws UserException, SSOException
    {
        IAuthenticationProfile oSelectedProfile = null;
        try
        {
            if (sSelectedProfile != null && oSession.getState() != SessionState.AUTHN_NOT_SUPPORTED)
            {
                List<IAuthenticationProfile> listRequiredProfiles = oSession.getAuthNProfiles();
                oSelectedProfile = _authenticationProfileFactory.getProfile(sSelectedProfile);
                if (oSelectedProfile == null)
                {
                    _systemLogger.debug("Selected profile is not available: " + sSelectedProfile);
                    throw new UserException(UserEvent.AUTHN_PROFILE_NOT_AVAILABLE);
                }
                if (!oSelectedProfile.isEnabled())
                {
                    _systemLogger.debug("Selected profile is disabled: " + sSelectedProfile);
                    throw new UserException(UserEvent.AUTHN_PROFILE_DISABLED);
                }
                
                if (!listRequiredProfiles.contains(oSelectedProfile))
                {
                    _systemLogger.debug("Selected profile is not required: " + sSelectedProfile);
                    throw new UserException(UserEvent.AUTHN_PROFILE_INVALID);
                }
                oSession.setSelectedAuthNProfile(oSelectedProfile);
            }
            else 
            {
                List<IAuthenticationProfile> listFilteredProfiles = filterRegisteredProfiles(oSession);
                if (!listFilteredProfiles.isEmpty())
                    oSession.setAuthNProfiles(listFilteredProfiles);
                
                if (oSession.getAuthNProfiles().size() == 1 && !bShowAllways)
                {
                    oSelectedProfile = oSession.getAuthNProfiles().get(0);
                    oSession.setSelectedAuthNProfile(oSelectedProfile);                
                }
            }
        }
        catch (UserException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            _systemLogger.error("Internal error during retrieval the selected profile: " 
                + sSelectedProfile, e); 
            throw new SSOException(SystemErrors.ERROR_INTERNAL);
        }
        
        return oSelectedProfile;
    }

    /**
     * Handle the SSO process.
     * 
     * Create or update a TGT, add all new methods to it, and persist TGT.
     * @param oSession The authentication session.
     * @return The created or updated TGT.
     * @throws SSOException If creation fails.
     */
    public ITGT handleSingleSignon(ISession oSession) throws SSOException
    {        
        ITGT oTgt = null;       
        if(_bSingleSignOn) //SSO enabled 
        {
            try
            {
                IAuthenticationProfile selectedAuthNProfile = oSession.getSelectedAuthNProfile();
                //create or update TGT
                String sTGT = oSession.getTGTId();
                
                if(sTGT == null) //New TGT
                {
                    oTgt = _tgtFactory.createTGT(oSession.getUser());
                }
                else //Update TGT
                {
                    oTgt = _tgtFactory.retrieve(sTGT);
                    if (oTgt == null)
                    {
                        _systemLogger.warn("Could not retrieve TGT with id: " + sTGT);
                        throw new SSOException(SystemErrors.ERROR_INTERNAL);
                    }
                }
                
                //Add all new methods to TGT
                List<IAuthenticationMethod> newMethods = selectedAuthNProfile.getAuthenticationMethods();
                IAuthenticationProfile tgtProfile = oTgt.getAuthenticationProfile(); 
                for(IAuthenticationMethod method : newMethods)
                {
                   //DD Do not add duplicate authN methods in TGT profile
                   if(!tgtProfile.containsMethod(method)) 
                       tgtProfile.addAuthenticationMethod(method); 
                }
                oTgt.setAuthenticationProfile(tgtProfile);  
                
                //Add current profile
                List<String> tgtProfileIds = oTgt.getAuthNProfileIDs();
                if (!tgtProfileIds.contains(selectedAuthNProfile.getID()))
                {
                    //DD Do not add duplicate AuthN profile id in TGT
                    oTgt.addAuthNProfileID(selectedAuthNProfile.getID());
                }
                
                //update TGT with requestor id
                addRequestorID(oTgt, oSession.getRequestorId());
                
                //Persist TGT
                oTgt.persist();
                
                oSession.setTGTId(oTgt.getId());
            }
            catch(SSOException e)
            {
                throw e;
            }
            catch(OAException e)
            {
                _systemLogger.warn("Could not update TGT", e);
                //Wrap exception
                throw new SSOException(e.getCode(), e);
            }
            catch (Exception e)
            {
                _systemLogger.error("Internal error during sso handling", e);
                throw new SSOException(SystemErrors.ERROR_INTERNAL);
            }
        }
        return oTgt;
    }

    /**
     * Check the SSO session that might be present.
     * 
     * Check the SSO session existence, expiration, and sufficiency.
     * @param oSession The authentication session.
     * @param sTGTId The TGT ID.
     * @param oRequestorPool The requestor pool.
     * @return <code>true</code> if TGT if sufficient.
     * @throws SSOException If retrieval or persisting TGT fails
     * @throws UserException If TGT user is invalid.
     */
    public boolean checkSingleSignon(ISession oSession, String sTGTId,
        RequestorPool oRequestorPool) 
        throws SSOException, UserException
    {      
        boolean bTGTSufficient = false;
        
        if(!_bSingleSignOn) //SSO enabled
        {
            _systemLogger.debug("SSO disabled");
        }
        else
        {        
            if (sTGTId == null) //TGT Cookie found
            {
                _systemLogger.debug("No valid TGT Cookie found");
            }
            else
            {
                try
                {
                    //Retrieve TGT
                    ITGT oTgt = _tgtFactory.retrieve(sTGTId);
                    
                    //Check tgt existence and expiration
                    if(oTgt == null || oTgt.isExpired()) //TGT valid
                    {   
                        _systemLogger.debug("TGT expired and ignored");
                    }
                    else
                    {
                        //Check if a previous request was done for an other user-id
                        String forcedUserID = oSession.getForcedUserID();
                        IUser tgtUser = oTgt.getUser();
                        if(forcedUserID != null && tgtUser != null && 
                            !forcedUserID.equalsIgnoreCase(tgtUser.getID()))
                            //Forced user does not match TGT user
                        {
                            //Remove TGT itself
                            removeTGT(oTgt);
                            _systemLogger.debug("User in TGT and forced user do not correspond");
                            
                            throw new UserException(UserEvent.TGT_USER_INVALID);
                        } 
                                             
                        //Set previous TGT id and user in session  
                        oSession.setTGTId(sTGTId);
                        oSession.setUser(oTgt.getUser());
                        //check ForcedAuthenticate
                        if(oSession.isForcedAuthentication()) //Forced authenticate
                        {  
                            _systemLogger.debug("Forced authentication");
                        }
                        else
                        {
                            //Check if TGT profile is sufficient 
                            IAuthenticationProfile tgtProfile = oTgt.getAuthenticationProfile();                   
                            List<String> oRequiredAuthenticationProfileIDs = 
                                oRequestorPool.getAuthenticationProfileIDs();                           
                            Iterator<String> iter = 
                                oRequiredAuthenticationProfileIDs.iterator();
                            while(iter.hasNext() && !bTGTSufficient)
                            {
                               //Retrieve next profile
                               AuthenticationProfile requiredProfile = 
                                   _authenticationProfileFactory.getProfile(iter.next());
                               if(requiredProfile != null && requiredProfile.isEnabled())
                               {                                  
                                   bTGTSufficient = 
                                       tgtProfile.compareTo(requiredProfile) >= 0;
                               }
                            }  
                        }  
                        
                        if (bTGTSufficient)
                        {//update TGT with requestor id
                            addRequestorID(oTgt, oSession.getRequestorId());
                            oTgt.persist();
                        }
                    }
                }
                catch(SSOException e)
                {
                    throw e;
                }
                catch(OAException e)
                {
                    _systemLogger.warn("Could not retrieve or update TGT", e);
                    //Wrap exception
                    throw new SSOException(e.getCode());
                }  
                
            }
        }
        
        return bTGTSufficient;
    }
    
    /**
     * Remove the TGT. 
     * @param oTgt The TGT to be removed.
     * @throws SSOException If TGT persistance fails.
     */
    public void removeTGT(ITGT oTgt) throws SSOException
    {
        //Remove TGT        
        oTgt.expire();
        try
        {
            oTgt.persist();
        }
        catch (PersistenceException e)
        {
            _systemLogger.warn("Could not remove TGT", e);
            //Wrap exception
            throw new SSOException(e.getCode(), e);
        }
    }
    
    /**
     * Gathers attributes to the user object in the supplied session.
     * 
     * @param oSession
     * @throws OAException
     * @since 1.4
     */
    public void gatherAttributes(ISession oSession) throws OAException
    {
        if (_attributeGatherer != null && 
            _attributeGatherer.isEnabled())
        {
            IUser oUser = oSession.getUser();
            if (oUser != null)
                _attributeGatherer.process(oUser.getID(), oUser.getAttributes());
        }
    }
    
    /**
     * Applies the attribute release policy over the userattributes available 
     * in the session.
     *  
     * @param session The authentication session.
     * @param sAttributeReleasePolicyID The release policy to be applied.
     * @throws OAException If an internal error occurs.
     * @since 1.4
     */
    public void performAttributeReleasePolicy(ISession session, 
        String sAttributeReleasePolicyID) throws OAException
    {
        try
        {
            IAttributes oReleaseAttributes = new UserAttributes();
            
            if (_attributeReleasePolicyFactory != null 
                && _attributeReleasePolicyFactory.isEnabled()
                && sAttributeReleasePolicyID != null)
            {
                IAttributeReleasePolicy oAttributeReleasePolicy = 
                    _attributeReleasePolicyFactory.getPolicy(sAttributeReleasePolicyID);
                if (oAttributeReleasePolicy != null 
                    && oAttributeReleasePolicy.isEnabled())
                {
                    _systemLogger.debug("applying attribute releasepolicy: " + sAttributeReleasePolicyID);
                    oReleaseAttributes = 
                        oAttributeReleasePolicy.apply(session.getUser().getAttributes());
                    
                    session.getUser().setAttributes(oReleaseAttributes);
                }
            }
            
            //DD empty attributes object so only the attributes were the release policy is applied are available
            IAttributes userAttributes = session.getUser().getAttributes();
            Enumeration enumAttributes = userAttributes.getNames();
            while (enumAttributes.hasMoreElements())
            {
                userAttributes.remove((String)enumAttributes.nextElement());
            }
            
            session.getUser().setAttributes(oReleaseAttributes);
        }
        catch(OAException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            _systemLogger.fatal("Internal error during applying the attribute release policy", e);
            throw new OAException(SystemErrors.ERROR_INTERNAL);
        }
    }
    
    //Read standard configuration    
    private void readDefaultConfiguration(Element eConfig) throws OAException
    {
        assert eConfig != null : "Supplied config == null";
        
        //SSO
        _bSingleSignOn = true;
        String sSingleSignOn = _configurationManager.getParam(eConfig, "single_sign_on");
        if (sSingleSignOn != null)
        {
            if("false".equalsIgnoreCase(sSingleSignOn))
                _bSingleSignOn = false;
            else if (!"true".equalsIgnoreCase(sSingleSignOn))
            {
                _systemLogger.error("Invalid value for 'single_sign_on' item found in configuration: " 
                    + sSingleSignOn);
                throw new OAException(SystemErrors.ERROR_CONFIG_READ);
            }
        }
        
        _systemLogger.info("SSO enabled: " + _bSingleSignOn);
    }

    private List<IAuthenticationProfile> filterRegisteredProfiles(ISession oSession)
    {        
        List<IAuthenticationProfile> listFilteredProfiles = new Vector<IAuthenticationProfile>();
        
        IUser oUser = oSession.getUser();
        if (oUser != null)
        {
            //AuthN Fallback: filter authN profiles with not registered methods if a user exists in session
            for (IAuthenticationProfile oProfile: oSession.getAuthNProfiles())
            {
                boolean isRegistered = true;
                for(IAuthenticationMethod oMethod : oProfile.getAuthenticationMethods())
                {
                    if(!oUser.isAuthenticationRegistered(oMethod.getID()))
                    {
                        isRegistered = false;
                        break;//stop looping, check finished
                    }
                }
                
                if (isRegistered)
                    listFilteredProfiles.add(oProfile);
            }   
        }
        return listFilteredProfiles;
    }
    
    //add requestor id to the end of the list
    private void addRequestorID(ITGT tgt, String requestorID) 
    {
        List<String> listRequestorIDs = tgt.getRequestorIDs();
        if (!listRequestorIDs.isEmpty() && listRequestorIDs.contains(requestorID))
            tgt.removeRequestorID(requestorID);
        
        //add to end of list
        tgt.addRequestorID(requestorID);
    }

}