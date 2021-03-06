/* 
 * Copyright (C) 2017-2020 by Øyvind Hanssen (ohanssen@acm.org)
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


package no.polaric.aprsd.http;
import spark.Request;
import spark.Response;
import spark.route.Routes;
import static spark.Spark.get;
import static spark.Spark.put;
import static spark.Spark.*;
import java.util.*; 
import no.polaric.aprsd.*;

/**
 * Implement REST API for user-related info. 
 */
public class UserApi extends ServerBase {

    private ServerAPI _api; 
    
    /* 
     * User info as it is sent to clients. 
     */
    public static class UserInfo {
        public String ident; 
        public Date lastused; 
        public String name;
        public boolean sar, admin; 
        public String passwd;
        public UserInfo() {}
        public UserInfo(String id, Date lu, String n, boolean s, boolean a)
           { ident = id; lastused = lu; name=n; sar=s; admin=a; }
    }

    
    public static class UserUpdate {
        public String name; 
        public String passwd;
        public boolean sar, admin;
    }
    
    
    private LocalUsers _users; 
    
    
    public UserApi(ServerAPI api,  LocalUsers u) {
        super(api);
        _api = api;
        _users = u;
    }
    
    
    
    /** 
     * Return an error status message to client. 
     * FIXME: Move to superclass. 
     */
    public String ERROR(Response resp, int status, String msg)
      { resp.status(status); return msg; }
      
      
    protected UserInfo getUser(User u) {
        if (u==null)
            return null;
        String name = "";           
        if (u instanceof LocalUsers.User) {
            var lu = (LocalUsers.User) u; 
            return new UserInfo(u.getIdent(), u.getLastUsed(), lu.getName(), lu.isSar(), lu.isAdmin());    
        }
        else
            return null;
    }
    
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
        
        /******************************************
         * Get a list of users. 
         ******************************************/
        get("/users", "application/json", (req, resp) -> {
            List<UserInfo> ul = new ArrayList<UserInfo>();
            for (User u: _users.getAll())  
               ul.add(getUser(u));
    
            return ul;
        }, ServerBase::toJson );
        
        
        
        /*******************************************
         * Get info about a given user.
         *******************************************/
        get("/users/*", "application/json", (req, resp) -> {
            var ident = req.splat()[0];
            User u = _users.get(ident);
            if (u==null)
                return ERROR(resp, 404, "Unknown user: "+ident);
            return getUser(u);
        }, ServerBase::toJson );
        
        
    
        /*******************************************
         * Update user
         *******************************************/
        put("/users/*", (req, resp) -> {
            var ident = req.splat()[0];        
            User u = _users.get(ident);
            if (u==null)
                return ERROR(resp, 404, "Unknown user: "+ident);
            var uu = (UserUpdate) 
                ServerBase.fromJson(req.body(), UserUpdate.class);
            
            if (u instanceof LocalUsers.User) {
                if (uu.name != null)
                    ((LocalUsers.User) u).setName(uu.name);
                if (uu.passwd != null) 
                    ((LocalUsers.User) u).setPasswd(uu.passwd);
                ((LocalUsers.User) u).setSar(uu.sar);
                ((LocalUsers.User) u).setAdmin(uu.admin);
            }
            return "Ok";
        });
        
        
        
        /*******************************************
         * Add user
         *******************************************/
        post("/users", (req, resp) -> {
            var u = (UserInfo) 
                ServerBase.fromJson(req.body(), UserInfo.class);

            if (_users.add(u.ident, u.name, u.sar, u.admin, u.passwd)==null) 
                return ERROR(resp, 400, "Probable cause: User exists");
            return "Ok";
        });
        
        
        
        /*******************************************
         * Delete user
         *******************************************/
        delete("/users/*", (req, resp) -> {
            var ident = req.splat()[0];  
            _users.remove(ident);
            return "Ok";
        });
        
        
    }


}



