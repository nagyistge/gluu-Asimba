
/*
 * Asimba Server
 * 
 * Copyright (C) 2012 Asimba
 * Copyright (C) 2007-2008 Alfa & Ariss B.V.
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
package com.alfaariss.oa.sso.authorization.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;

import com.alfaariss.oa.OAException;
import com.alfaariss.oa.SystemErrors;
import com.alfaariss.oa.api.IComponent;
import com.alfaariss.oa.api.IOptional;
import com.alfaariss.oa.api.attribute.ISessionAttributes;
import com.alfaariss.oa.api.authorization.IAuthorizationAction;
import com.alfaariss.oa.api.configuration.IConfigurationManager;
import com.alfaariss.oa.api.logging.IAuthority;
import com.alfaariss.oa.api.session.ISession;
import com.alfaariss.oa.api.session.SessionState;
import com.alfaariss.oa.engine.core.Engine;
import com.alfaariss.oa.engine.core.authorization.AuthorizationMethod;
import com.alfaariss.oa.engine.core.authorization.AuthorizationProfile;
import com.alfaariss.oa.engine.core.authorization.factory.IAuthorizationFactory;
import com.alfaariss.oa.engine.core.requestor.RequestorPool;
import com.alfaariss.oa.engine.core.server.Server;
import com.alfaariss.oa.sso.SSOException;

/**
 * Authorizes the user by configured pre authorization methods and actions.
 * 
 * This manager can be used to authorize a user for a globally configured web 
 * pre authorization profile and a configured web pre authorization profile, 
 * which is specific for a requestor pool.
 * 
 * An pre authorization profile can contain one or more authorization steps. 
 * These steps are divided into a method part and an action part. The action 
 * part will be performed by the method, when the method part will match the 
 * user specific authorization information. 
 * 
 * DD If a method is configured multiple times then the methods will be performed multiple times.
 * 
 * @author MHO
 * @author JRE
 * @author Alfa & Ariss
 *
 */
public class PreAuthorizationManager implements IComponent, IOptional, IAuthority
{
    /** all methods from the global authZ profile and the requestor authZ profile */
    private final String SESSION_CURRENT_PROFILE = "PRE_AUTHZ_PROFILE_CURRENT";
    /** contains the index of the method list */
    private final String SESSION_CURRENT_METHOD = "PRE_AUTHZ_METHOD_CURRENT";
    
    private Log _logger;
    private boolean _bEnabled;
    private IConfigurationManager _configManager;
    private IAuthorizationFactory _oPreAuthorizationFactory;
    private AuthorizationProfile _oGlobalAuthorizationProfile;
    private Map<String, IWebAuthorizationMethod> _mapMethods;
    private Map<String, IAuthorizationAction> _mapActions;

    /**
     * PreAuthorizationManager default constructor
     */
    public PreAuthorizationManager ()
    {
        _logger = LogFactory.getLog(PreAuthorizationManager.class);
        _bEnabled = false;
        _mapMethods = new HashMap<String, IWebAuthorizationMethod>();
        _mapActions = new HashMap<String, IAuthorizationAction>();
    }
    
    /**
     * @see IComponent#start(IConfigurationManager, org.w3c.dom.Element)
     */
    @Override
    public void start(IConfigurationManager oConfigurationManager,
        Element eConfig) throws OAException
    {
        if (eConfig == null)
        {
            _bEnabled = false;
        }
        else
        {
            if (oConfigurationManager == null)
                throw new IllegalArgumentException(
                    "Supplied oConfigurationManager is empty");
            
            _configManager = oConfigurationManager;
            
            _bEnabled = true;
            String sEnabled = _configManager.getParam(eConfig, "enabled");
            if (sEnabled != null)
            {
                if (sEnabled.equalsIgnoreCase("FALSE"))
                    _bEnabled = false;
                else if (!sEnabled.equalsIgnoreCase("TRUE"))
                {
                    _logger.error("Unknown value in 'enabled' configuration item: " + sEnabled);
                    throw new OAException(SystemErrors.ERROR_CONFIG_READ);
                }
            }
            
            if (_bEnabled)
            {
                Engine oEngine = Engine.getInstance();
                _oPreAuthorizationFactory = oEngine.getPreAuthorizationPoolFactory();
                if (_oPreAuthorizationFactory == null || !_oPreAuthorizationFactory.isEnabled())
                {
                    _logger.error("Pre Authorization Manager is disabled");
                    throw new OAException(SystemErrors.ERROR_INIT);
                }
                
                Server oServer = oEngine.getServer();
                String sGlobalProfileID = oServer.getPreAuthorizationProfileID();
                if (sGlobalProfileID != null)
                    _oGlobalAuthorizationProfile = _oPreAuthorizationFactory.getProfile(sGlobalProfileID);
                else
                    _oGlobalAuthorizationProfile = null;
                
                Element eActions = _configManager.getSection(eConfig, "actions");
                if (eActions == null)
                {
                    _logger.warn("No optional actions found in configuration");
                }
                else
                {
                    Element eAction = _configManager.getSection(eActions, "action");
                    while (eAction != null) 
                    {
                        IAuthorizationAction oAction = createAction(eAction);
                        if (!oAction.isEnabled())
                        {
                            _logger.debug("Action is disabled: " + oAction.getID());
                        }
                        
                        if (_mapActions.containsKey(oAction.getID()))
                        {
                            _logger.error("Action is not unique: " + oAction.getID());
                            throw new OAException(SystemErrors.ERROR_CONFIG_READ);
                        }
                        
                        _mapActions.put(oAction.getID(), oAction);
                        eAction = _configManager.getNextSection(eAction);
                    }
                }
                
                Element eMethods = _configManager.getSection(eConfig, "methods");
                if (eMethods == null)
                {
                    _logger.error("No 'methods' section found in configuration");
                    throw new OAException(SystemErrors.ERROR_CONFIG_READ);
                }
                
                Element eMethod = _configManager.getSection(eMethods, "method");
                while (eMethod != null)
                {
                    
                    IWebAuthorizationMethod oMethod = createMethod(eMethod, _mapActions);
                    if (!oMethod.isEnabled())
                    {
                        _logger.debug("Authentication method is disabled: " 
                            + oMethod.getID());
                    }
                    
                    if (_mapMethods.containsKey(oMethod.getID()))
                    {
                        _logger.error("Authentication method is not unique: " 
                            + oMethod.getID());
                        throw new OAException(SystemErrors.ERROR_CONFIG_READ);
                    }
                    _mapMethods.put(oMethod.getID(), oMethod);    
                    
                    eMethod = _configManager.getNextSection(eMethod);
                }
                
                
                _logger.debug("Finished action initialization (" + _mapActions.size() + " actions loaded)");
            }
            
        }
    }
    
    /**
     * @see com.alfaariss.oa.api.IComponent#restart(org.w3c.dom.Element)
     */
    @Override
    public void restart(Element eConfig) throws OAException
    {
        synchronized(this)
        {
            stop();
            start(_configManager, eConfig);
        }
    }
    
    /**
     * @see com.alfaariss.oa.api.IComponent#stop()
     */
    @Override
    public void stop()
    {
        if (_mapMethods != null)
        {
            for (IWebAuthorizationMethod oMethod: _mapMethods.values())
                oMethod.stop();
            _mapMethods.clear();
        }
        
        if (_mapActions != null)
        {
            for (IAuthorizationAction oAction: _mapActions.values())
                oAction.stop();
            _mapActions.clear();
        }
        
        _bEnabled = false;
    }

    /**
     * Authorizes the user by configured pre-authorization methods.
     * 
     * Will authorize the supplied user for the globally configured 
     * pre-authorization profile and the supplied requestor specific 
     * pre-authorization profile. 
     * 
     * Will update the session state with one of the following states:
     * <ul>
     * <li>SessionState.PRE_AUTHZ_OK</li>
     * <li>SessionState.PRE_AUTHZ_IN_PROGRESS</li>
     * <li>SessionState.PRE_AUTHZ_FAILED</li>
     * <li>SessionState.USER_CANCELLED</li>
     * </ul>
     * 
     * @param oRequest The HTTP request object.
     * @param oResponse The HTTP response object.
     * @param oSession The session that is linked to the requesting party.
     * @param oRequestorPool The requestor pool.
     * @throws SSOException if authorization fails 
     *  due to internal error in manager itself.
     * @throws OAException if authorization fails due to internal error.
     */
    @SuppressWarnings("unchecked")//added for (List<AuthorizationMethod>)oAttributes.get(SESSION_CURRENT_PROFILE);
    public void authorize(HttpServletRequest oRequest, HttpServletResponse oResponse,
        ISession oSession, RequestorPool oRequestorPool) throws SSOException, OAException
    {
        try
        {
            if (oRequest == null)
                throw new IllegalArgumentException("Supplied request == null");
            if (oResponse == null)
                throw new IllegalArgumentException("Supplied response == null");
            if (oSession == null)
                throw new IllegalArgumentException("Supplied session == null");
            
            if (!_bEnabled)
            {
                _logger.error("Preauthorization manager disabled");
                throw new SSOException(SystemErrors.ERROR_INTERNAL);
            }
            
            ISessionAttributes oAttributes = oSession.getAttributes();
            
            List<AuthorizationMethod> listMethods = null;
            if (oAttributes.contains(PreAuthorizationManager.class, SESSION_CURRENT_PROFILE))
            {
                listMethods = (List<AuthorizationMethod>)oAttributes.get(PreAuthorizationManager.class, SESSION_CURRENT_PROFILE);
            }
            else
            {
                //DD a list of all profile methods from the global and the requestor profile will be stored in the user session
                listMethods =  new Vector<AuthorizationMethod>();
                
                //first put all methods from the global profile in the list
                if (_oGlobalAuthorizationProfile != null && _oGlobalAuthorizationProfile.isEnabled())
                    listMethods.addAll(_oGlobalAuthorizationProfile.getAuthorizationMethods());
                
                //then add the pool specific methods in the list
                String sPreAuthZProfileID = oRequestorPool.getPreAuthorizationProfileID();
                if (sPreAuthZProfileID != null)
                {
                    AuthorizationProfile reqAuthZProfile = null;
                    try
                    {
                        reqAuthZProfile = _oPreAuthorizationFactory.getProfile(sPreAuthZProfileID);
                    }
                    catch(OAException e)
                    {
                        _logger.warn("Could not get preauthorization profile", e);
                        throw new SSOException(SystemErrors.ERROR_INTERNAL);
                    }
                    
                    if (reqAuthZProfile == null)
                    {
                        _logger.warn("Preauthorization profile not found: " + sPreAuthZProfileID);
                        throw new SSOException(SystemErrors.ERROR_INTERNAL);
                    }
                    
                    if (reqAuthZProfile.isEnabled())
                        listMethods.addAll(reqAuthZProfile.getAuthorizationMethods());
                    else
                        _logger.debug("Preauthorization profile disabled: " + sPreAuthZProfileID);
                }
                
                oAttributes.put(PreAuthorizationManager.class, SESSION_CURRENT_PROFILE, listMethods);
            }            
            
            if (listMethods.isEmpty())
            {
                //no authorization available and enabled
                oSession.setState(SessionState.PRE_AUTHZ_OK);
                try
                {
                    oSession.persist();
                }
                catch(OAException e)
                {
                    _logger.warn("Could not store session", e);
                    throw new SSOException(SystemErrors.ERROR_INTERNAL);
                }
            }
            else
            {
                authorizeForProfile(listMethods, oRequest, oResponse, oSession);
            }
        }
        catch(OAException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            _logger.fatal("Internal error during preauthorization", e);
            throw new SSOException(SystemErrors.ERROR_INTERNAL);
        } 
    }
    
    /**
     * @see com.alfaariss.oa.api.IOptional#isEnabled()
     */
    @Override
    public boolean isEnabled()
    {
        return _bEnabled;
    }
    
    /**
     * @see IAuthority#getAuthority()
     */
    @Override
    public String getAuthority()
    {
        return "Pre authZ Manager";
    }

    private IAuthorizationAction createAction(Element eAction) 
        throws OAException
    {
        IAuthorizationAction oAction = null;
        try
        {
            String sClass = _configManager.getParam(eAction, "class");
            if (sClass == null)
            {
                _logger.error("No 'class' item found in 'action' section found in configuration");
                throw new OAException(SystemErrors.ERROR_CONFIG_READ);
            }
            
            Class cAction = null;
            try
            {
                cAction = Class.forName(sClass);
            }
            catch (Exception e)
            {
                _logger.error("Class not found: " + sClass, e);
                throw new OAException(SystemErrors.ERROR_CONFIG_READ);
            }
    
            try
            {
                oAction = (IAuthorizationAction)cAction.newInstance();
            }
            catch(Exception e)
            {
                _logger.error("Could not create instance of " + sClass, e);
                throw new OAException(SystemErrors.ERROR_CONFIG_READ);
            }
            
            oAction.start(_configManager, eAction);
        }
        catch (OAException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            _logger.fatal("Internal error during object creation", e);
            throw new OAException(SystemErrors.ERROR_INTERNAL);
        } 
        return oAction;
    }
    
    private IWebAuthorizationMethod createMethod(Element eMethod, 
        Map<String, IAuthorizationAction> mapActions) throws OAException
    {
        IWebAuthorizationMethod oMethod = null;
        try
        {
            String sClass = _configManager.getParam(eMethod, "class");
            if (sClass == null)
            {
                _logger.error("No 'class' item found in 'methods' section found in configuration");
                throw new OAException(SystemErrors.ERROR_CONFIG_READ);
            }
            
            Class cMethod = null;
            try
            {
                cMethod = Class.forName(sClass);
            }
            catch (Exception e)
            {
                _logger.error("Class not found: " + sClass, e);
                throw new OAException(SystemErrors.ERROR_CONFIG_READ);
            }
    
            try
            {
                oMethod = (IWebAuthorizationMethod)cMethod.newInstance();
            }
            catch(Exception e)
            {
                _logger.error("Could not create instance of " + sClass, e);
                throw new OAException(SystemErrors.ERROR_CONFIG_READ);
            }
            
            oMethod.start(_configManager, eMethod, mapActions);
        }
        catch (OAException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            _logger.fatal("Internal error during object creation", e);
            throw new OAException(SystemErrors.ERROR_INTERNAL);
        } 
        return oMethod;
    }
    
    private void authorizeForProfile(List<AuthorizationMethod> listMethods,
        HttpServletRequest oRequest, HttpServletResponse oResponse,
        ISession oSession) throws OAException, SSOException
    {
        try
        {
            ISessionAttributes oAttributes = oSession.getAttributes();
            Integer intCurrentMethod = new Integer(0);
            //retrieve current authorization method
            if (!oAttributes.contains(PreAuthorizationManager.class, SESSION_CURRENT_METHOD))
            {//get first method
                oAttributes.put(PreAuthorizationManager.class, SESSION_CURRENT_METHOD, intCurrentMethod);
            }
            else
            {//get method which previously returned PRE_AUTHZ_IN_PROGRESS
                intCurrentMethod = (Integer)oAttributes.get(PreAuthorizationManager.class, SESSION_CURRENT_METHOD);
            }
            
            AuthorizationMethod curAuthZMethodBean = listMethods.get(intCurrentMethod);
            
            IWebAuthorizationMethod oWebAuthZMethod = _mapMethods.get(curAuthZMethodBean.getID());
            if (oWebAuthZMethod == null)
            {
                _logger.error("No preauthorization method found with id: " 
                    + curAuthZMethodBean.getID());
                throw new SSOException(SystemErrors.ERROR_INTERNAL);
            }
            
            while (oSession.getState() == SessionState.PRE_AUTHZ_IN_PROGRESS) 
            {
                if (!oWebAuthZMethod.isEnabled())
                {
                    _logger.error("Preauthorization method is disabled: " 
                        + oWebAuthZMethod.getID());
                    throw new SSOException(SystemErrors.ERROR_INTERNAL);
                }
                
                switch (oWebAuthZMethod.authorize(oRequest, oResponse, oSession))
                {
                    case AUTHZ_METHOD_SUCCESSFUL:
                    {
                        intCurrentMethod++;
                        if (intCurrentMethod < listMethods.size())
                        {
                            oAttributes.put(PreAuthorizationManager.class, SESSION_CURRENT_METHOD, intCurrentMethod);
                            curAuthZMethodBean = listMethods.get(intCurrentMethod);
                            
                            if (!_mapMethods.containsKey(curAuthZMethodBean.getID()))
                            {
                                _logger.error("Preauthorization method not available: " 
                                    + curAuthZMethodBean.getID());
                                throw new SSOException(SystemErrors.ERROR_INTERNAL);
                            }
                            oWebAuthZMethod = _mapMethods.get(curAuthZMethodBean.getID());
                        }
                        else 
                            oSession.setState(SessionState.PRE_AUTHZ_OK);
                        
                        break;
                    }
                    case AUTHZ_METHOD_IN_PROGRESS:
                    {
                        return;
                    }
                    case USER_CANCELLED:
                    {
                        oSession.setState(SessionState.USER_CANCELLED);
                        break;
                    }
                    default:
                    {//if AUTHZ_METHOD_FAILED
                        oSession.setState(SessionState.PRE_AUTHZ_FAILED);
                    }
                }
            }
        
        }
        catch(OAException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            _logger.fatal("Internal error during preauthorization", e);
            throw new SSOException(SystemErrors.ERROR_INTERNAL);
        } 
        
    }
}