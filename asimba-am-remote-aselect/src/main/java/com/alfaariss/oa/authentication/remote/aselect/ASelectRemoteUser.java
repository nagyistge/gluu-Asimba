/*
 * Asimba - Serious Open Source SSO
 * 
 * Copyright (C) 2012 Asimba
 * Copyright (C) 2007-2009 Alfa & Ariss B.V.
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
package com.alfaariss.oa.authentication.remote.aselect;

import com.alfaariss.oa.authentication.remote.bean.RemoteUser;

/**
 * Remote A-Select user.
 * @author MHO
 * @author Alfa & Ariss
 * @since 1.4
 */
public class ASelectRemoteUser extends RemoteUser
{
    /** serialVersionUID */
    private static final long serialVersionUID = -7084900438659203978L;
    
    private String _sCredentials;
    
    /**
     * Creates the user object.
     *
     * @param organization the user organization
     * @param userId The unique remote user ID
     * @param methodID Method id
     * @param credentials A-Select credentials
     */
    public ASelectRemoteUser (String organization, String userId, String methodID, String credentials)
    {
        super(organization, userId, methodID);
        _sCredentials = credentials;
    }
    
    /**
     * Returns the A-Select credentials retrieved during authentication from the remote organization. 
     * @return A-Select credentials
     */
    public String getCredentials()
    {
        return _sCredentials;
    }
}
