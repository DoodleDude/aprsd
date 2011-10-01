
package no.polaric.aprsd.http;
import no.polaric.aprsd.*;

import org.simpleframework.http.core.Container;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import org.simpleframework.http.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.io.PrintStream;

import uk.me.jstott.jcoord.*;
import java.util.*;
import java.io.*;
import java.text.*;
import com.mindprod.base64.Base64;
import java.util.concurrent.locks.*; 


public abstract class ServerBase 
{
   protected  ServerAPI  _api;
   protected  int        _utmzone;
   protected  char       _utmlatzone;
   private    String     _timezone;
   protected  String     _wfiledir;
   protected  boolean    _infraonly;
   private    String     _adminuser, _updateusers;
           
   public static final String _encoding = "UTF-8";


   DateFormat df = new SimpleDateFormat("dd MMM. HH:mm",
           new DateFormatSymbols(new Locale("no")));
   DateFormat tf = new SimpleDateFormat("HH:mm",
           new DateFormatSymbols(new Locale("no")));
    DateFormat xf = new SimpleDateFormat("yyyyMMddHHmmss",
           new DateFormatSymbols(new Locale("no")));       
   
   public static Calendar utcTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.getDefault());
   public static Calendar localTime = Calendar.getInstance();
   
   
   public ServerBase(ServerAPI api, Properties config) throws IOException
   {
      _api=api; 
      _utmzone     = Integer.parseInt(config.getProperty("map.utm.zone", "33").trim());
      _utmlatzone  = config.getProperty("map.utm.latzone", "W").charAt(0);
      _wfiledir    = config.getProperty("map.web.dir", "/aprsd").trim();
      _infraonly   = config.getProperty("map.infraonly", "false").trim().matches("true|yes");
      _timezone    = config.getProperty("timezone", "").trim();
      _adminuser   = config.getProperty("user.admin","admin").trim();
      _updateusers = config.getProperty("user.update", "").trim();
                
      TimeZone.setDefault(null);
      if (_timezone.length() > 1) {
          TimeZone z = TimeZone.getTimeZone(_timezone);
          localTime.setTimeZone(z);
          df.setTimeZone(z);
          tf.setTimeZone(z);
      }
   }



   /**
    * Convert reference to UTM projection with our chosen zone.
    * Return null if it cannot be converted to our zone (too far away).
    */
   protected UTMRef toUTM(Reference ref)
   {
        try { return ref.toLatLng().toUTMRef(_utmzone); }
        catch (Exception e)
           { System.out.println("*** Kan ikke konvertere til UTM"+_utmzone+" : "+ref);
             return null; }
   }
   
   
   
   /**
    * Get name of user identified using basic authentication.
    * (we assume that there is a front end webserver which already 
    * did the authentication).  
    */ 
   protected final String getAuthUser(Request req)
   {
         String auth = req.getValue("authorization");
         if (auth==null)
            auth = req.getValue("Authorization");
         if (auth != null) {
           Base64 b64 = new Base64();
           byte[] dauth = b64.decode(auth.substring(6));
           return (new String(dauth)).split(":")[0];
         }
         return null;
   }
   


   protected final boolean authorizedForUpdate(Request req)
   {
       String user = getAuthUser(req);

       if (user == null)
          user="_NOLOGIN_";
       return ( user.matches(_adminuser) ||
                user.matches(_updateusers) );
   }
   

   protected final boolean authorizedForAdmin(Request req)
   {
       String user = getAuthUser(req);
       return (user != null && user.matches(_adminuser)); 
   } 
   
   
   protected PrintWriter getWriter(Response resp) throws IOException
   {
        OutputStream os = resp.getOutputStream();
        return new PrintWriter(new OutputStreamWriter(os, _encoding));
   }
   

    
   protected String fixText(String t)
   {
        t = t.replaceAll("&amp;", "##amp;"); 
        t = t.replaceAll("&lt;", "##lt;");
        t = t.replaceAll("&gt;", "##gt;");   
        t = t.replaceAll("&quot;", "##amp;");
        t = t.replaceAll("&", "&amp;");   
        t = t.replaceAll("<", "&lt;");
        t = t.replaceAll(">", "&gt;");
        t = t.replaceAll("\"", "&quot;");
        t = t.replaceAll("\\p{Cntrl}", "?");
        t = t.replaceAll("##amp;", "&amp;"); 
        t = t.replaceAll("##lt;", "&lt;");
        t = t.replaceAll("##gt;", "&gt;");   
        t = t.replaceAll("##quot;", "&amp;"); 
        return t; 
   }
   
  

   protected String showDMstring(double ll)
   {
       int deg = (int) Math.floor(ll);
       double minx = ll - deg;
       if (ll < 0 && minx != 0.0) 
          minx = 1 - minx;
       
       double mins = ((double) Math.round( minx * 60 * 100)) / 100;
       return ""+deg+"\u00B0 "+mins+"'";
   }

   
   
   protected String ll2dmString(LatLng llref)
      { return showDMstring(llref.getLatitude())+"N, "+showDMstring(llref.getLongitude())+"E"; }
   
   
   
  protected long _sessions = 0;
  protected synchronized long getSession(Request req)
      throws IOException
  {
     String s_str  = req.getParameter("clientses");
     if (s_str != null && s_str.matches("[0-9]+")) {
        long s_id = Long.parseLong(s_str);
        if (s_id > 0)
           return s_id;
     }
     _sessions = (_sessions +1) % 2000000000;
     return _sessions;       
  }
   
   
   
   protected void printXmlMetaTags(PrintWriter out, Request req)
      throws IOException
   {
        out.println("<meta name=\"utmzone\" value=\""+ _utmzone + "\"/>");
        out.println("<meta name=\"login\" value=\""+ getAuthUser(req) + "\"/>");
        out.println("<meta name=\"adminuser\" value=\""+ authorizedForAdmin(req) + "\"/>");
        out.println("<meta name=\"updateuser\" value=\""+ authorizedForUpdate(req) + "\"/>"); 
        out.println("<meta name=\"sarmode\" value=\""+ (_api.getSar() !=null ? "true" : "false")+"\"/>");
   }
   
   
   /**
    * Display a message path between nodes. 
    */
   protected void printPathXml(PrintWriter out, Station s, UTMRef uleft, UTMRef lright)
   {
       UTMRef ity = toUTM(s.getPosition());
       Set<String> from = s.getTrafficTo();
       if (from == null || from.isEmpty()) 
           return;
       
       Iterator<String> it = from.iterator();    
       while (it.hasNext()) 
       {
            Station p = (Station)_api.getDB().getItem(it.next(), null);
            if (p==null || !p.isInside(uleft, lright) || p.expired())
                continue;
            Reference x = p.getPosition();
            UTMRef itx = toUTM(x);
            RouteInfo.Edge e = _api.getDB().getRoutes().getEdge(s.getIdent(), p.getIdent());
            if (itx != null) { 
               out.print("<linestring stroke="+
                   (e.primary ? "\"2\"" : "\"1\"")  + " opacity=\"1.0\" color=\""  +
                   (e.primary ? "B00\">" : "00A\">"));
               out.print((int) Math.round(itx.getEasting())+ " " + (int) Math.round(itx.getNorthing()));
               out.print(", ");
               out.print((int) Math.round(ity.getEasting())+ " " + (int) Math.round(ity.getNorthing()));
               out.println("</linestring>");
            }
       }
   }
   
   
   /** 
    * Print a history trail of a moving station as a XML linestring object. 
    */
   protected void printTrailXml(PrintWriter out, String[] tcolor, 
          Reference firstpos, Iterable<Trail.Item> h, UTMRef uleft, UTMRef lright)
   {
       out.println("   <linestring stroke=\"2\" opacity=\"1.0\" color=\""+ tcolor[0] +"\" color2=\""+ tcolor[1] +"\">");
       
       boolean first = true;
       int state = 1;
       UTMRef itx = toUTM(firstpos);  
       String t = "00000000000000";
       
       for (Trail.Item it : h) 
       {       
          if (itx != null) {       
              if (!first) 
                  out.print(", "); 
              else
                  first = false;   
              out.println( (int) Math.round(itx.getEasting())+ " " + (int) Math.round(itx.getNorthing()) +
                          " " + t);
          }
            
          itx = toUTM(it.getPosition());
          t = xf.format(it.time);
          if (it.isInside(uleft, lright, 0.7, 0.7))
             state = 2;
          else
             if (state == 2) {
                state = 3; 
                break;
             }    
       }
       if (itx != null & state < 3)
           out.println(", "+ (int) Math.round(itx.getEasting())+ " " + (int) Math.round(itx.getNorthing()) +
                         " "+t);  // FIXME: get first time
       out.println("   </linestring>");
   }
     
}