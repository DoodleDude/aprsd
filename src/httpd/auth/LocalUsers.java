
package no.polaric.aprsd.http;

import java.util.*; 
import java.io.Serializable;
import no.polaric.aprsd.*;
import java.io.*;
 
 
 
/**
 * User info (profile) stored locally. 
 * Currently this is used for keeping track of usage. May add more later. 
 */
 
public class LocalUsers {
    
    public static class User implements Serializable {
       private String userid; 
       private Date lastused; 

       public String getIdent() {return userid;}
       public Date   getLastUsed() {return lastused;}
       public void   updateTime() { lastused = new Date(); }
       
       public User(String id, Date d)
         {userid=id; lastused=d;} 
    }
    
    private ServerAPI _api;
    private SortedMap<String, User> _users = new TreeMap<String, User>(); 
    private String _filename; 
    
    
    public LocalUsers(ServerAPI api, String fname) {
       _api = api;
       _filename = fname; 
       restore();
    }
  
  
    public User get(String id) {
       return _users.get(id); 
    }
  
  
    public Collection<User> getAll() {
       return _users.values();
    }
    
    public void add(String user) {
      if (!_users.containsKey(user))
         _users.put(user, new User(user, null));
    }
    
    
    public void save() {
       try {
          _api.log().info("LocalUsers", "Saving user data...");
          FileOutputStream fs = new FileOutputStream(_filename);
          ObjectOutput ofs = new ObjectOutputStream(fs);
          for (User u: _users.values())
             { ofs.writeObject(u); }

        }
        catch (Exception e) {
          _api.log().warn("LocalUsers", "Cannot save data: "+e);
        } 
    }
    
    
    public void restore() {
       try {
          _api.log().info("LocalUsers", "Restoring user data...");
          FileInputStream fs = new FileInputStream(_filename);
          ObjectInput ifs = new ObjectInputStream(fs);
          while (true)
          { 
              User u = (User) ifs.readObject(); 
              if (!_users.containsKey(u.getIdent()))
                  _users.put(u.getIdent(), u);
          }
        }
        catch (EOFException e) { }
        catch (Exception e) {
            _api.log().warn("StationDBImp", "Cannot restore data: "+e);
            _users.clear();
        }
    }
    
}
