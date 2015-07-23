 
/* 
 * Copyright (C) 2015 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */ 

package no.polaric.aprsd;
import uk.me.jstott.jcoord.*; 
import java.util.*;
import java.io.Serializable;
  

/**
 * Geographical point. Movable, with label, etc..
 */
public abstract class TrackerPoint extends PointObject implements Serializable, Cloneable
{
    private   static long      _nonMovingTime = 1000 * 60 * 5; 
    private   static Notifier  _change = new Notifier();
    protected static ServerAPI _api = null;
    protected static StationDB _db  = null;
    
    private boolean    _changing = false; 
    protected Date     _updated = new Date();  // FIXME: Can this be private?
    private Date       _lastChanged;        
        
    private   String   _alias;    
    private   boolean  _hidelabel = false; 
    protected boolean  _persistent = false;
  
  
  
    public TrackerPoint(Reference p)
      { super(p); }
    
              
    public static void setApi(ServerAPI api)
       { _api = api; _db = _api.getDB(); }   
       
       
       
    /************** Methods related to tracking of changes ***************/
          
    /** 
     * Abort all waiters. See Notifier class. 
     * @param retval Return value: true=ok, false=was aborted.
     */
    public static void abortWaiters(boolean retval)
      { _change.abortAll(retval); }
      
      
      
    /**
     * Wait for change of state. Blocks the calling thread until there is 
     * a change in at least one object. 
     *
     * @param clientid Unique numbe for calling thread.
     * @return true=ok, false=was aborted.
     */
    public static boolean waitChange(long clientid)
       { return waitChange(null, null, clientid); }
       
       
       
     /**
     * Wait for change of state within an area. Blocks calling thread until any object
     * inside the specified area changes. 
     *
     * @param uleft Upper left corner of the rectangular area of interest.
     * @param lright Lower right corner of the rectangular area of interest.
     * @param clientid Unique numbe for calling thread.
     * @return true=ok, false=was aborted.
     */
    public static boolean waitChange(Reference uleft, Reference lright, long clientid) 
       { return _change.waitSignal(uleft, lright, clientid); } 

       
     
    /** Return time of last significant change in position, etc.. */
    public Date getLastChanged()
       { return _lastChanged; }
    
    
    
    /** Return time when last updated */              
    public Date getUpdated()
       { return _updated; }
    
    
    
    /** Set time when last updated. */   
    public void setUpdated(Date t)
       { _updated = t; }
       
    
    
    public synchronized void reset()
    {
       _updated  = new Date(0);
       setChanging();
    }
    
          
        
    /**
     * Called to indicate that something has changed.
     */
    public synchronized void setChanging()
    {
         _changing = true;
         _lastChanged = new Date();  
         _change.signal(this); 
    }
           
      
      
    /**
     * Return true and signal a change if position etc. is updated recently.
     * This must be called periodically to ensure that asyncronous waiters
     * are updated when a station has stopped updating. 
     */ 
    public synchronized boolean isChanging() 
    {
          /* 
           * When station has not changed position or other properties for xx minutes, 
           * it is regarded as not moving. 
           */
          if (_changing && (new Date()).getTime() > _lastChanged.getTime() + _nonMovingTime) {
               _change.signal(this);
               return (_changing = false);
          }
          if (!_changing) 
               checkForChanges(); 
          return _changing; 
    }
    
        
    protected void checkForChanges() {}    
    
    
    
    
    /************ Methods related to descriptions, icons, labels and aliases *************/
    
    
    protected static boolean changeOf(String x, String y)
       { return x != y || (x != null && !x.equals(y)); }  
       
       
    public String getAlias()
       { return _alias; }
       
    
    public synchronized boolean setAlias(String a)
    {  
      if (changeOf(_alias, a)) {
        _alias = a;
         setChanging();
         return true;
      }
      return false;
    }
                       
    /**
     * Change the icon explicitly.
     * @param a file path of the icon. 
     * @return true if there was a change. 
     */
    public synchronized boolean setIcon(String a)
    {  
      if (changeOf(_icon, a)) {
        _icon = a;
        setChanging();
        return true;
      }
      return false;
    }
  
    public abstract String getIcon(boolean override);
    
    
    @Override public String getIcon()
       { return getIcon(true); }
    
    
    /** 
     * Get identifier for display on map. 
     * 
     * @param usealias If true, return alias instead of ident, if set. 
     * @return ident or alias.
     */
    public String getDisplayId(boolean usealias)
       { return (usealias && _alias != null) ? _alias : getIdent().replaceFirst("@.*",""); }    
    
    
    /** 
     * Get identifier for display on map. 
     * @return ident or alias. 
     */
    public String getDisplayId()
       { return getDisplayId(true); }
               
               
    /** Return true if label is hidden (not displayed on map). */           
    public boolean isLabelHidden()
       { return _hidelabel; }
    
    
    /** Hide label (do not display it on map). */
    public synchronized void setLabelHidden(boolean h)
      { if (_hidelabel != h) {
          _hidelabel = h;
          setChanging(); 
        }
      }
     
     
    /** Set description. */
    public synchronized void setDescr(String d)
    {   
        if (d != null) 
        {
           if ((_description==null && d!=null) || (_description != null && !_description.equals(d)))
               setChanging(); 
           _description = d;  
        }
    }
    
   
   
   /************ Methods related to persistence and expiry *************/
    
                       
    public boolean isPersistent()
       { return _persistent; }
     
     
    /**
     * Set object to be persistent. I.e. it is not deleted by garbage collection
     * even if expired. 
     */
    public void setPersistent(boolean p)
       { _persistent = p; }
        
            
    /** 
     * Return true if object has expired. 
     */    
    public abstract boolean expired();
    
   
    /**
     * Return false if object should not be visible on maps. If it has expired.
     */
    public boolean visible() { return !expired(); }
    
    
    
    
}