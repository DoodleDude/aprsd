/* 
 * Copyright (C) 2009 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.io.*;
import java.util.*;
import gnu.io.*;
  /* Similar to javax.comm */


/**
 * TNC channel. For devices in TNC2 compatible mode.
 */
 
public class TncChannel extends Channel implements Runnable, Serializable
{
    private  String  _portName; 
    private  int     _baud;
    private  String  _myCall; /* Move this ? */
    
    transient private  boolean        _close = false;    
    transient private  int            _max_retry;
    transient private  long           _retry_time;   
    transient private  BufferedReader _in;
    transient private  SerialPort     _serialPort;

    
    
    public TncChannel(Properties config) 
    {
        _myCall = config.getProperty("tncchannel.mycall", "").trim().toUpperCase();
        if (_myCall.length() == 0)
           _myCall = config.getProperty("default.mycall", "NOCALL").trim().toUpperCase();  
        
        _max_retry = Integer.parseInt(config.getProperty("tncchannel.retry", "0").trim());
        _retry_time = Long.parseLong(config.getProperty("tncchannel.retry.time", "30").trim()) * 60 * 1000; 
        
        _portName = config.getProperty("tncchannel.port", "/dev/ttyS0").trim();
        _baud= Integer.parseInt(config.getProperty("tncchannel.baud", "9600").trim());
        System.setProperty("gnu.io.rxtx.SerialPorts", "/dev/ttyACM0");
    }
 
 
 
 
 
 
    /**
     * The generic sendPacket method is unsupported on generic TNCs. 
     * We cannot set addresses per packet. If callsign is not null and match
     * TNC callsign, or explicitly requested, we use third party format. 
     */ 
    public void sendPacket(Packet p)
    {
       if (_out == null)
          return;
       if (p.thirdparty || (p.to != null && !p.to.equals(_myCall)))
           _out.print(
             "}" + p.from + ">" + p.to +
                ((p.via != null && p.via.length() > 0) ? ","+p.via : "") + 
                ":" + p.report + "\r" );
       else 
           _out.print(p.report+"\r");
       _out.flush();    
    }
   
    

    /**
     * Setup the serial port
     */
    private SerialPort connect () throws Exception
    {
        System.out.println("Serial port: "+_portName);
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(_portName);
        if ( portIdentifier.isCurrentlyOwned() )
            System.out.println("*** ERROR: Port "+ _portName + " is currently in use");
        else
        {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);
            
            if ( commPort instanceof SerialPort )
            {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(_baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                serialPort.enableReceiveTimeout(5000);
                return (SerialPort) commPort;
            }
            else
                System.out.println("*** ERROR: Port " + _portName + " is not a serial port.");
        }    
        return null; 
    }
   
   
  
   
   /**
    * Init the TNC - set it to converse mode
    */
    private void initTnc()
    {
        System.out.println("*** Init TNC");
        try {
          Thread.sleep(100);
          OutputStream o = _serialPort.getOutputStream();
          o.write(3);
          o.flush();
          Thread.sleep(500); 
          _out.print("k\r"); 
          _out.flush();
        }
        catch (Exception e) 
           { System.out.println("*** Error: initTnc: "+e); }
    }
    
    
    
    private void restoreTnc()
    {
       System.out.println("*** Restore Tnc");
       try {
          OutputStream o = _serialPort.getOutputStream();
          o.write(3);
          o.flush();
          Thread.sleep(500);
       }
       catch (Exception e) 
          {  System.out.println("*** Error: restoreTnc: "+e); }
    }
    
    
    
    public void close() 
    { 
       System.out.println("*** Closing TNC channel");
       try {
         restoreTnc(); 
         _close = true;
         Thread.sleep(4000);
         if (_out != null) _out.close(); 
         if (_in != null) _in.close(); 
       }
       catch (Exception e) {}
       if (_serialPort != null) _serialPort.close(); 
    }
       
       
    
    public void run()
    {
        int retry = 0;             
        while (true) 
        {
           if (retry <= _max_retry || _max_retry == 0) 
               try { 
                   long sleep = 30000 * (long) retry;
                   if (sleep > _retry_time) 
                      sleep = _retry_time; /* Default: Max 30 minutes */
                   Thread.sleep(sleep); 
               } 
               catch (Exception e) {break;} 
           else break;
        
           try {
               System.out.println("*** Initialize TNC on "+_portName);
               _serialPort = connect();
               if (_serialPort == null)
                   continue; 
                   
               _in = new BufferedReader(new InputStreamReader(_serialPort.getInputStream(), _rx_encoding));
               _out = new PrintWriter(new OutputStreamWriter(_serialPort.getOutputStream(), _tx_encoding));
               initTnc();
               while (true) 
               {
                   try {
                      String inp = _in.readLine(); 
                      System.out.println(new Date() + ":  "+inp);
                      receivePacket(inp, false);
                   }
                   catch (java.io.IOException e) {
                      if (_close) { System.out.println("*** Stopping TNC thread"); break; } 
                      else continue;             
                   }
               }
           }
           catch(NoSuchPortException e)
           {
                System.out.println("*** WARNING: serial port " + _portName + " not found");
                e.printStackTrace(System.out);
           }
           catch(Exception e)
           {   
                e.printStackTrace(System.out); 
           }  
           retry++;     
           
        }
        System.out.println("*** Couldn't connect to TNC on'"+_portName+"' - giving up");
     }




    public String toString() { return "TNC on " + _portName+", "+_baud+" baud]"; }

}

