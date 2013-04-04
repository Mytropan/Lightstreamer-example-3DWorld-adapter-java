/*
 * 
 *  Copyright 2013 Weswit s.r.l.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 * 
 */

package com.lightstreamer.adapters.DemoQuat3d;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import com.lightstreamer.adapters.metadata.LiteralBasedProvider;
import com.lightstreamer.interfaces.metadata.CreditsException;
import com.lightstreamer.interfaces.metadata.TableInfo;

public class Move3dMetaAdapter extends LiteralBasedProvider {

    /**
     * Private logger; a specific "LS_Move3dDemo_Logger" category
     * should be supplied by log4j configuration.
     */
    private static Logger logger;
    
    private final ConcurrentHashMap<String, String> players = new ConcurrentHashMap<String, String>();
    private final static ConcurrentHashMap<String, PollsBandwidth> checkBandWidths = new ConcurrentHashMap<String, PollsBandwidth>();
    
    private static String LOGON_CUSTOM_PREFIX = "c_logon_";
    private static String BAND_PREFIX = "My_Band_";
    private int jmxPort = 9999;
    private static int maxSrvSideUsers = 10;
    private static int curSrvSideUsers = 10;
    
    
    @Override
    public void init(Map params, File configDir) {
        String logConfig = (String) params.get("log_config");
        if (logConfig != null) {
            File logConfigFile = new File(configDir, logConfig);
            String logRefresh = (String) params.get("log_config_refresh_seconds");
            if (logRefresh != null) {
                DOMConfigurator.configureAndWatch(logConfigFile.getAbsolutePath(), Integer.parseInt(logRefresh) * 1000);
            } else {
                DOMConfigurator.configure(logConfigFile.getAbsolutePath());
            }
        }
        logger = Logger.getLogger("LS_demos_Logger.Move3dDemo");
        
        if (params.containsKey("jmxPort")) {
            this.jmxPort = new Integer((String)params.get("jmxPort")).intValue();
        }
        logger.info("JMX Port:" + this.jmxPort);
        
        if (params.containsKey("Max_Srv_Players")) {
            maxSrvSideUsers = new Integer((String)params.get("Max_Srv_Players")).intValue();
        }
        logger.debug("Max server side users:" + maxSrvSideUsers);
        curSrvSideUsers = 0;
    }
    
    @Override
    public boolean wantsTablesNotification(java.lang.String user) {
        return true;
    }
    
    public static void killBandChecker(String itemName) {
        PollsBandwidth p = checkBandWidths.get(itemName);
        if ( p != null ) {
            p.setEnd();
        }
    }
    
    /*
    @Override
    public String[] getItems(String user, String sessionID, String group) throws ItemsException {
        if ( group.startsWith(LOGON_CUSTOM_PREFIX) ) {
            if  ( Move3dAdapter.worldOvercrwoded(group) ) {
                throw new ItemsException("This world is overcrowded, please migrate elsewhere.");
            }
        }
        
        return group.split(" ");
    }*/
 

    /*
    @Override
    public void notifyNewSession(java.lang.String user, java.lang.String sessionID, java.util.Map clientContext)
              throws CreditsException {
        throw new CreditsException(-3, "This world is overcrowded, please migrate elsewhere.");      
    }
     */
    
    @Override
    public void notifyTablesClose(java.lang.String sessionID, TableInfo[] tables) {
        if ( tables[0].getId().startsWith("ServerSide") ) {
            if (curSrvSideUsers > 0) {
                curSrvSideUsers--;
            }
        }
    }
                     
    @Override
    public void notifyNewTables(java.lang.String user, java.lang.String sessionID, TableInfo[] tables) throws CreditsException {
         if (tables[0].getId().startsWith(LOGON_CUSTOM_PREFIX)) {
            String item = tables[0].getId().substring(LOGON_CUSTOM_PREFIX.length());
            String pieces[] = item.split("_");
            
            if ( Move3dAdapter.tooManyUsers() ) {
                throw new CreditsException(-3, "Too many users, please wait ... ");
            }
            
            if  ( Move3dAdapter.worldOvercrwoded(tables[0].getId()) ) {
                throw new CreditsException(-3, "This world is overcrowded, please migrate elsewhere.");
            }
            
            if (pieces.length > 1) {
                players.put(sessionID, pieces[1]);   
            }
        } else if ( tables[0].getId().startsWith(BAND_PREFIX) ) {
            String usr = tables[0].getId().substring(BAND_PREFIX.length());
            PollsBandwidth p = new PollsBandwidth(sessionID, usr, this.jmxPort);
            p.start();
            checkBandWidths.put(tables[0].getId(), p);
        } else if ( tables[0].getId().startsWith("ServerSide") ) {
            if (curSrvSideUsers < maxSrvSideUsers) {
                curSrvSideUsers++;
                logger.debug("Current Server side players: " + curSrvSideUsers);
            } else {
                throw new CreditsException(-3, "Too many server side players!");
            }
        }
    }
    
    public static void terminateUser(String usr) {
        PollsBandwidth p =checkBandWidths.get(BAND_PREFIX + usr);
        if (p != null ) {
            p.setEnd();
            p.forceMeOut();
        } else {
            logger.warn("terminateUser failed for user: " + usr);
        }
    }
    
    @Override
    public void notifyUserMessage(String user, String sessionID, String message) {
        if (message == null) {
            return ;
        }
        
        if ( message.startsWith("n|") ) {
            // Set new NickName for the players. 
            try {
                String newNick = message.split("\\|")[1];
                Move3dAdapter.myWorld.changeNickName(players.get(sessionID), newNick);
            } catch (Exception e) {
                // Skip, message not well formatted
                logger.warn("Message not well formatted, skipped.", e);
            }
        } else if ( message.startsWith("m|") ) {
            // Set new NickName for the players. 
            try {
                String newMsg = message.split("\\|")[1];
                Move3dAdapter.myWorld.updateMyMsg(players.get(sessionID), newMsg);
            } catch (Exception e) {
                // Skip, message not well formatted
                logger.warn("Message not well formatted, skipped.", e);
            }
        } else {
            Move3dAdapter.myWorld.dispatchMsgs(players.get(sessionID) + "|" + message);
            logger.debug("Input command from user " + players.get(sessionID) + ": " + message);
        }
    }
    
}