/*
 * Asimba Server
 * 
 * Copyright (C) 2015, Gluu
 * Copyright (C) 2013 Asimba
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
 * gluu-Asimba - Serious Open Source SSO - More information on www.gluu.org
 * 
 */
package org.gluu.asimba.util.ldap.selector;

import java.io.Serializable;
import java.util.Date;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * ApplicationSelector configuration mapping entry.
 * 
 * @author Dmitry Ognyannikov
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ApplicationSelectorEntry implements Serializable, Comparable<ApplicationSelectorEntry> {
    
    private String inum;
    
    /**
     * The entity id of the selector (SP entityId URL).
     */
    private String id;
    
    /**
     * IDP ID (URL).
     */
    private String organizationId;
    
    /**
     * the application friendly name.
     */
    private String friendlyName;
    
    private boolean enabled = true;
    
    /**
     * Timestamp when Entry was last modified, or null when unknown.
     */
    private Date lastModified = new Date();

    /**
     * @return the inum
     */
    public String getInum() {
        return inum;
    }

    /**
     * @param inum the inum to set
     */
    public void setInum(String inum) {
        this.inum = inum;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the organizationId
     */
    public String getOrganizationId() {
        return organizationId;
    }

    /**
     * @param organizationId the organizationId to set
     */
    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    /**
     * @return the friendlyName
     */
    public String getFriendlyName() {
        return friendlyName;
    }

    /**
     * @param friendlyName the friendlyName to set
     */
    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    /**
     * @return the enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled the enabled to set
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return the lastModified
     */
    public Date getLastModified() {
        return lastModified;
    }

    /**
     * @param lastModified the lastModified to set
     */
    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }
    
    @Override
    public int compareTo(ApplicationSelectorEntry entry) {
        return this.id.compareToIgnoreCase(entry.getId());
    }

}
